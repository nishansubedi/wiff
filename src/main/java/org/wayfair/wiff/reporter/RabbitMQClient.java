package org.wayfair.wiff.reporter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.wayfair.wiff.parser.Parser;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

public class RabbitMQClient<T> extends WiffReporter<T> {
  private final String          EXCHANGE_NAME;
  private final String          QUEUE_NAME;
  private Connection            connection;
  private Channel               channel;

  private ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream(200000);
  private int                   reportInterval;

  /**
   * Create a reporter that sends data to RabbitMQ
   * 
   * @param host
   *          the RabbitMQ host
   * @param port
   *          the RabbitMQ port
   * @param exchangeName
   *          the name of the destination RabbitMQ exchange
   * @param queueName
   *          the name of the destination RabbitMQ queue
   * @param reportInterval
   *          the number of messages to store before sending. If less than 1,
   *          the size of the buffered data will be used to determine when to
   *          report
   * @throws IOException
   */
  public RabbitMQClient(String host, int port, String exchangeName,
      String queueName, int reportInterval) throws IOException {
    this(host, port, "guest", "guest", exchangeName, queueName, reportInterval,
        null, null);
  }

  /**
   * @param host
   *          the RabbitMQ host
   * @param port
   *          the RabbitMQ port
   * @param username
   *          the RabbitMQ username
   * @param password
   *          the RabbitMQ user's password
   * @param exchangeName
   *          the name of the destination RabbitMQ exchange
   * @param queueName
   *          the name of the destination RabbitMQ queue
   * @param reportInterval
   *          the number of messages to store before sending. If less than 1,
   *          the size of the buffered data will be used to determine when to
   *          report
   * @param killSignal
   *          the object that, if sent to this report, will cause it to shutdown
   * @param parser
   *          a Parser object that, if present, will be used by this reporter to
   *          transform the data it receives
   * @throws IOException
   */
  public RabbitMQClient(String host, int port, String username,
      String password, String exchangeName, String queueName,
      int reportInterval, T killSignal, Parser<T, byte[]> parser)
      throws IOException {
    super(killSignal, parser);
    this.EXCHANGE_NAME = exchangeName;
    this.QUEUE_NAME = queueName;
    this.reportInterval = reportInterval;

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUsername(username);
    factory.setPassword(password);

    connection = factory.newConnection();
    channel = connection.createChannel();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Runnable#run()
   */
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
            report(msg);
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

  /**
   * Reports the current batch of messages and reset buffers
   */
  protected void report() {
    try {
      channel.basicPublish(EXCHANGE_NAME, QUEUE_NAME, null,
          messageBuffer.toByteArray());
      messageBuffer.reset();
    } catch (IOException e) {
      LOGGER.info("", e);
    }
  }

  /**
   * Reports a single message
   * 
   * @param message
   *          the message to report
   */
  protected void report(byte[] message) {
    try {
      channel.basicPublish(EXCHANGE_NAME, QUEUE_NAME, null, message);
    } catch (IOException e) {
      LOGGER.error("", e);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.wayfair.wiff.reporter.WiffReporter#cleanup()
   */
  @Override
  public void cleanup() {
    try {
      // Send any remaining messages
      if (messageBuffer.size() != 0) {
        report();
      }
      super.cleanup();
      channel.close();
      connection.close();
    } catch (IOException e) {
      LOGGER.error("", e);
    }
  }
}