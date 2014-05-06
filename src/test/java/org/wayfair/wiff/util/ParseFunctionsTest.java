package org.wayfair.wiff.util;

import static org.junit.Assert.*;
import static org.wayfair.wiff.util.ParseFunctions.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;
import org.junit.Test;

public class ParseFunctionsTest {
  byte[]               bytes;
  private final Logger LOGGER = Logger.getLogger(this.getClass());

  public ParseFunctionsTest() {
    // These integers represent a byte array. Do the conversion
    String compressed = "31 -117 8 0 0 0 0 0 0 0 -77 -55 40 -55 -51 -79 -29 -78 -55 72 77 76 -79 -29 -30 -76 41 -55 44 -55 73 -75 115 45 -55 72 45 74 77 -52 81 112 -83 72 -52 45 -56 73 85 8 72 76 79 -75 -47 -121 72 114 -39 -24 67 84 -37 36 -27 -89 84 2 53 113 98 85 14 52 76 31 -94 0 -88 30 108 9 23 0 -45 110 12 67 109 0 0 0";
    String[] bytesAsInts = compressed.split("\\s");

    bytes = new byte[bytesAsInts.length];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = Byte.parseByte(bytesAsInts[i]);
    }
  }

  public StringBuilder testAppendValue() {
    StringBuilder json = new StringBuilder("{ ");
    appendKeyValue(json, "here", "there");
    appendKeyValue(json, "everywhere", "nowhere");
    json.append(" }");

    assertEquals("{ \"here\" : \"there\", \"everywhere\" : \"nowhere\" }",
        json.toString());

    return json;
  }

  public void testAppendObject(StringBuilder object) {
    StringBuilder json = new StringBuilder("{ ");
    appendKeyObject(json, "test1", object);
    appendKeyObject(json, "test1_copy", object);
    json.append(" }");

    assertEquals("{ \"test1\" : " + object.toString() + ", \"test1_copy\" : "
        + object.toString() + " }", json.toString());
  }

  public void testEscape() {
    assertEquals("testing \\\" \\\\ \\/ \\r\\n \\t end",
        escape("testing \" \\ / \r\n \t end").toString());
  }

  public String testGZIPDecompression() {
    // Decompress the gzipped bytes and compare to expected output
    String decompressed = decompress(bytes);
    assertEquals(
        "<html>\n<head>\n\t<title>Ethereal Example Page</title>\n</head>\n<body>\n\t\tEthereal Example Page\n\t</body>\n</html>\n\n",
        decompressed);
    return decompressed;
  }

  public void testGZIPCompression(String decompressed) {
    try {
      assertTrue(ByteBuffer.wrap(bytes).equals(
          ByteBuffer.wrap(compress(decompressed))));
    } catch (IOException e) {
      LOGGER.error("", e);
    }
  }

  @Test
  public void test() {
    // Test JSON String helpers
    StringBuilder obj = testAppendValue();
    testAppendObject(obj);
    testEscape();

    // Test GZIP decompression
    String decompressed = testGZIPDecompression();

    // Test GZIP compression
    testGZIPCompression(decompressed);
  }
}
