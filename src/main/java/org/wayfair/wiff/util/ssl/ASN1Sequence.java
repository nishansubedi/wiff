package org.wayfair.wiff.util.ssl;

import java.util.Arrays;
import static org.wayfair.wiff.util.ByteArrayFunctions.*;

public class ASN1Sequence {
  public static final byte IDENTIFIER = (byte) 0x30;

  private byte[]           data;
  private int              offset;

  /**
   * @param data
   *          the bytes of a ASN.1 sequence
   * @throws ASN1FormatException
   */
  public ASN1Sequence(byte[] data) throws ASN1FormatException {
    this(data, 0);
  }

  /**
   * @param data
   *          a byte array containing an ASN.1 sequence
   * @param offset
   *          the offset at which the ASN.1 sequence starts
   * @throws ASN1FormatException
   */
  public ASN1Sequence(byte[] data, int offset) throws ASN1FormatException {
    if ((data[offset] & IDENTIFIER) == IDENTIFIER) {
      this.data = data;
      this.offset = offset;
    } else {
      throw new ASN1FormatException("The first byte must be 0x30.");
    }
  }

  /**
   * 
   * @return Returns the length of the next ASN.1 object
   */
  public int getASNLength() {
    int length = 0;
    if ((data[offset + 1] & 0x80) == 0x80) {
      // Long format. find out how many bytes
      int numBytes = data[offset + 1] & 0x7f;
      length = bytesToInt(data, offset + 2, numBytes);
    } else {
      length = data[offset + 1] & 0x7f;
    }

    return length;
  }

  /**
   * @return the number of bytes are used to specify the lenght of the next
   *         ASN.1 object
   */
  public int getASNLengthByteSize() {
    int length = 1;
    if ((data[offset + 1] & 0x80) == 0x80) {
      // Long format. find out how many bytes
      length += data[offset + 1] & 0x7f;
    }
    return length;
  }

  /**
   * skips to the next ASN.1 object i in the sequence
   */
  public void skipASN1Object() {
    // skip they bytes that define the length and the number of bytes dictated
    // by the length
    offset += 1 + getASNLengthByteSize() + getASNLength();
  }

  /**
   * Steps into an ASN.1 object in order to parse objects within
   */
  public void stepIntoASN1Object() {
    offset += 1 + getASNLengthByteSize();
  }

  /**
   * @return the next ASN.1 object excluding the type tag and length
   */
  public byte[] extractANS1Object() {
    return extractANS1Object(false);
  }

  /**
   * @param removePadding
   *          if true, leading bytes of 0x00 will be removed
   * @return the next ASN.1 object excluding the type tag and length
   */
  public byte[] extractANS1Object(boolean removePadding) {
    int start = offset + 1 + getASNLengthByteSize();
    int length = getASNLength();

    offset = start + length;
    if (removePadding) {
      while (data[start] == 0x00) {
        start++;
        length--;
      }
    }
    return Arrays.copyOfRange(data, start, start + length);
  }

  /**
   * @param offset
   *          set the byte offset into the ASN.1 sequence
   */
  public void setOffset(int offset) {
    this.offset = offset;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  public String toString() {
    return bytesToHexString(data, 0, offset) + "| "
        + bytesToHexString(data, offset, data.length - offset);
  }
}

class ASN1FormatException extends Exception {
  private static final long serialVersionUID = 1L;

  public ASN1FormatException(String message) {
    super(message);
  }

  public ASN1FormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
