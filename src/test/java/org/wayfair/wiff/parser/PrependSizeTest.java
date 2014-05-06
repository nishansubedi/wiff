package org.wayfair.wiff.parser;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.wayfair.wiff.parser.PrependSize;

public class PrependSizeTest {

  @Test
  public void test() {
    PrependSize parser = new PrependSize();

    byte[] test = "This is a test".getBytes();
    ByteBuffer actual = ByteBuffer.wrap(parser.parse(test));

    int size = test.length;

    ByteBuffer expected = ByteBuffer.allocate(size + 4);
    expected.putInt(size);
    expected.put(test);
    expected.flip();

    assertEquals(expected, actual);
  }
}
