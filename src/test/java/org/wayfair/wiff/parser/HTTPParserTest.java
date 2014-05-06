package org.wayfair.wiff.parser;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;
import org.wayfair.wiff.parser.HTTPParser;

import static org.wayfair.wiff.test.TestHelperFunctions.*;

public class HTTPParserTest {

  @Test
  public void test() {
    byte[] stream = null;

    String resource = "./src/test/resources/http_gzip.txt";
    try {
      stream = readBytesFromFile(resource);
    } catch (IOException e) {
      fail("Could not open resource: " + resource);
    }

    String json = null;
    resource = "./src/test/resources/http_gzip_json.txt";
    try {
      json = readFile(resource);
    } catch (IOException e) {
      fail("Could not open resource: " + resource);
    }

    HTTPParser parser = new HTTPParser("test", true, null, null);
    assertEquals(json, new String(parser.parse(stream)));
  }
}
