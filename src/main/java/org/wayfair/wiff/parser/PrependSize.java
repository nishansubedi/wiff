package org.wayfair.wiff.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class PrependSize implements Parser<byte[], byte[]> {
  private ByteArrayOutputStream message = new ByteArrayOutputStream(60000);
  private ByteBuffer            buffer  = ByteBuffer.allocate(4);

  protected final Logger        LOGGER  = Logger.getLogger(this.getClass());

  /**
   * Prepends the length of the byte array to the given byte array
   * 
   * @param data
   *          the byte array to which its own length will be prepended
   * @return the given byte array with its own length prepended
   */
  public byte[] parse(byte[] data) {
    // Get the size in byte form
    int size = data.length;
    buffer.putInt(size);

    // prepend the size to the beginning of the byte array
    try {
      message.write(buffer.array());
      message.write(data);
    } catch (IOException e) {
      LOGGER.error("", e);
    }
    byte[] msg = message.toByteArray();
    buffer.clear();
    message.reset();
    return msg;
  }
}
