package org.wayfair.wiff.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ParseFunctions {
  /**
   * Appends a key-value pair to a JSON Stringbuilder
   * 
   * @param json
   *          the StringBuilder to which the key-value pair should be appended
   * @param key
   *          the key
   * @param value
   *          the value
   */
  public static void appendKeyValue(StringBuilder json, String key,
      CharSequence value) {
    int length = json.length();
    char lastChar = json.charAt(length - 2);
    if (length > 0 && lastChar != '{') {
      json.append(", ");
    }
    json.append("\"").append(key).append("\" : \"").append(escape(value))
        .append("\"");
  }

  /**
   * Appends a key-object pair to a JSON Stringbuilder
   * 
   * @param json
   *          the StringBuilder to which the key-object pair should be appended
   * @param key
   *          the key
   * @param object
   *          the object
   */
  public static void appendKeyObject(StringBuilder json, String key,
      CharSequence object) {
    int length = json.length();
    char lastChar = json.charAt(length - 2);
    if (length > 0 && lastChar != '{') {
      json.append(", ");
    }
    json.append("\"").append(key).append("\" : ").append(object);
  }

  /**
   * Escapes special characters
   * 
   * @param str
   *          the character sequence to be escaped
   * @return the escaped character sequence
   */
  public static CharSequence escape(CharSequence str) {
    StringBuilder escaped = new StringBuilder();
    char c;
    for (int i = 0; i < str.length(); i++) {
      c = str.charAt(i);
      switch (c) {
        case '"':
          escaped.append("\\\"");
          break;
        case '\\':
          escaped.append("\\\\");
          break;
        case '/':
          escaped.append("\\/");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\t':
          escaped.append("\\t");
          break;
        default:
          if (!Character.isISOControl(c)) {
            escaped.append(c);
          }
          break;
      }
    }
    return escaped;
  }

  /**
   * Compresses a String using gzip
   * 
   * @param str
   *          the string to compress
   * @return the compressed string in bytes
   * @throws IOException
   */
  public static byte[] compress(String str) throws IOException {
    if (str == null || str.length() == 0) {
      return null;
    }
    ByteArrayOutputStream obj = new ByteArrayOutputStream();
    GZIPOutputStream gzip = new GZIPOutputStream(obj);
    gzip.write(str.getBytes());
    gzip.close();
    return obj.toByteArray();
  }

  /**
   * Decode gzip compressed data into a String
   * 
   * @param bytes
   *          the gzipped data
   * @return the decompressed data as a String
   */
  public static String decompress(byte[] bytes) {
    String data = "";
    if (bytes != null && bytes.length > 0) {
      try {
        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(
            bytes));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];

        int len;
        while ((len = gis.read(buffer)) != -1) {
          bos.write(buffer, 0, len);
        }

        gis.close();
        data = bos.toString();

        bos.close();
      } catch (IOException e) {
        data = new String(bytes);
      }
    }
    return data;
  }
}
