package org.wayfair.wiff.util;

import java.util.Arrays;

public class ByteArrayFunctions {
  private static final char[] hexArray = "0123456789abcdef".toCharArray();

  /**
   * returns the integer value represented by a set of bytes
   * 
   * @param bytes
   *          the byte array that contains the representation of an integer
   * @param offset
   *          the offset at which to begin reading bytes
   * @param length
   *          the number of bytes to read
   * @return the integer value represented by a set of bytes
   */
  public static int bytesToInt(byte[] bytes, int offset, int length) {
    int result = 0x00;
    for (int i = offset; i < offset + length; i++) {
      result = (result << 8) | (bytes[i] & 0xff);
    }
    return result;
  }

  /**
   * returns a hex string representing a set of bytes
   * 
   * @param bytes
   *          the byte array that contains data we want to display as hex
   * @param offset
   *          the offset at which to begin reading bytes
   * @param length
   *          the number of bytes to read
   * @return the hex string representing a set of bytes
   */
  public static String bytesToHexString(byte[] bytes, int offset, int length) {
    StringBuilder result = new StringBuilder(length * 2 + 2);
    result.append("0x");
    for (int i = offset; i < offset + length; i++) {
      result.append(hexArray[(bytes[i] & 0xff) >> 4]);
      result.append(hexArray[(bytes[i] & 0x0f)]);
      result.append(" ");
    }
    return result.toString();
  }

  /**
   * @param bytes
   *          a byte array containing entries of the format: 4 byte integer
   *          (length) followed by a sequence of bytes of that length (message)
   * @param offset
   *          the offset at which to begin reading bytes
   * @return the message portion of the entry at the given offset
   */
  public static byte[] extractMessage(byte[] bytes, int offset) {
    int size = 0;

    // interpret first four bytes as the size
    size = ((bytes[offset] & 0xff) << 24) | ((bytes[offset + 1] & 0xff) << 16)
        | ((bytes[offset + 2] & 0xff) << 8) | (bytes[offset + 3] & 0xff);
    offset += 4;

    // cut out the bytes according to the size
    return Arrays.copyOfRange(bytes, offset, offset + size);
  }

  public static byte[] xor(byte[] array1, byte[] array2) {
    byte[] result = null;

    int length;
    if (array1.length < array2.length) {
      length = array1.length;
    } else {
      length = array2.length;
    }

    if (length > 0) {
      result = new byte[array1.length];
      for (int i = 0; i < length; i++) {
        result[i] = (byte) (array1[i] ^ array2[i]);
      }
    }
    return result;
  }
}
