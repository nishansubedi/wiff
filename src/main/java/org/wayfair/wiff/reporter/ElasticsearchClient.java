package org.wayfair.wiff.reporter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.wayfair.wiff.parser.Parser;

public class ElasticsearchClient<T> extends WiffReporter<T> {
  private ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream(200000);
  private URL                   url;

  private int                   reportInterval;

  public ElasticsearchClient(String host, int port, int reportInterval,
      Parser<T, byte[]> parser) {
    super(null, parser);
    this.reportInterval = reportInterval;

    try {
      url = new URL("http://" + host + ":" + port + "/_bulk");
    } catch (IOException e) {
      LOGGER.error("", e);
    }
  }

  @Override
  public void run() {
    int count = 0;
    running = true;
    try {
      while (running) {
        // Grab next message
        T value = receiveData();

        if (value != null) {
          // Stop execution if this is a kill pill
          if (value.equals(killSignal)) {
            break;
          }

          byte[] msg = null;
          if (parser != null) {
            msg = (byte[]) parser.parse(value);
          } else if (value instanceof byte[]) {
            msg = (byte[]) value;
          } else if (value instanceof String) {
            msg = ((String) value).getBytes();
          } else {
            LOGGER.error(this.getClass().getName()
                + " cannot handle data of this type. Exiting...");
            System.exit(1);
          }

          if (reportInterval == 1) {
            // Send message immediately
            messageBuffer.write(msg);
            report();
          } else {
            if (reportInterval > 1) {
              // Send message when the batch size has been reached
              messageBuffer.write(msg);
              if (++count > reportInterval) {
                report();
                count = 0;
              }
            } else {
              if (messageBuffer.size() + msg.length > 60000) {
                // Send message when it is sufficiently large
                report();
              }
              messageBuffer.write(msg);
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("", e);
    }
    stop();
    cleanup();
  }

  private void report() {
    try {
      HttpURLConnection con = (HttpURLConnection) url.openConnection();

      con.setRequestMethod("POST");

      con.setDoOutput(true);
      DataOutputStream wr = new DataOutputStream(con.getOutputStream());
      wr.write(messageBuffer.toByteArray());
      wr.flush();
      wr.close();

      if (con.getResponseCode() != 200) {
        LOGGER.error("The following messages could not be sent:\n"
            + messageBuffer.toString());
      }
      messageBuffer.reset();
    } catch (Exception e) {
      LOGGER.error("", e);
    }
  }
}
