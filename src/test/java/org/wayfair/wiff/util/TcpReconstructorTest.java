package org.wayfair.wiff.util;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;

import org.junit.Test;

import static org.wayfair.wiff.test.TestHelperFunctions.*;

public class TcpReconstructorTest {

  @Test
  public void test() {

    ArrayList<byte[]> packets = readPacktsFromFile("./src/test/resources/http.cap");

    String expectedStream = null;
    String resource = "./src/test/resources/http_cap.txt";
    try {
      expectedStream = readFile(resource);
    } catch (IOException e) {
      fail("Could not open resource: " + resource);
    }
    
    
    TcpReconstructor recon = new TcpReconstructor();
    WiffPacket p = new WiffPacket();

    boolean complete = false;
    Iterator<byte[]> it = packets.iterator();

    while (it.hasNext() && !complete) {
      p.setPacket(ByteBuffer.wrap(it.next()));
      try {
        complete = complete || recon.ReassemblePacket(p);
      } catch (IOException e) {
        fail("Could not reassemblePacket due to IOException");
      }
    }

    if (!complete) {
      fail("The stream should be complete.");
    }
    assertEquals(expectedStream, recon.toString());
  }
}
