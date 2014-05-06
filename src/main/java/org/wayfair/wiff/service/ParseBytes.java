package org.wayfair.wiff.service;

import static org.wayfair.wiff.util.ByteArrayFunctions.extractMessage;

import org.wayfair.wiff.reporter.WiffReporter;

/**
 * A service that reads data that has been parsed by the PrependSize Parser.
 */
public class ParseBytes extends WiffService<byte[], byte[]> {

  /**
   * @param reporter
   */
  public ParseBytes(WiffReporter<byte[]> reporter) {
    super(reporter);
    startReporter();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.wayfair.wiff.service.WiffService#processData(java.lang.Object)
   */
  @Override
  public void processData(byte[] data) {
    int offset = 0;

    byte[] message;
    while (offset < data.length) {
      message = extractMessage(data, offset);
      offset += message.length + 4;

      // send to reporter
      try {
        reporter.sendData(message);
      } catch (InterruptedException e) {
        LOGGER.error("", e);
      }
    }
  }
}
