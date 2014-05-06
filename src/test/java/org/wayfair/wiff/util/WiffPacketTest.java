package org.wayfair.wiff.util;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.Test;
import org.wayfair.wiff.util.WiffPacket;

import static org.wayfair.wiff.test.TestHelperFunctions.*;

public class WiffPacketTest {

  @Test
  public void test() {
    ArrayList<byte[]> packets = readPacktsFromFile("./src/test/resources/http_gzip.cap");

    String payload = null;
    String resource = "./src/test/resources/http_gzip.txt";
    try {
      payload = readFile(resource);
      payload = payload.substring(payload.indexOf("\r\n") + 2,
          payload.indexOf("\r\n\r\n") + 4);
    } catch (IOException e) {
      fail("Could not open resource: " + resource);
    }

    WiffPacket p = new WiffPacket(packets.get(3));

    assertEquals(14, p.getEthHeaderLength());
    assertEquals(511, p.getPacketByteLength());
    assertEquals("192.168.69.2", p.getSourceIP());
    assertEquals("192.168.69.1", p.getDestinationIP());
    assertEquals(-1062714110, p.getSourceIPAsInt());
    assertEquals(-1062714111, p.getDestinationIPAsInt());
    assertEquals(20, p.getIpHeaderLength());
    assertEquals(true, p.hasTcpHeader());
    assertEquals(32, p.getTcpHeaderLength());
    assertEquals(34059, p.getSourcePort());
    assertEquals(80, p.getDestinationPort());
    assertEquals(2415239731L, p.getSequenceNumber());
    assertEquals(2518192935L, p.getAckNumber());
    assertEquals(445, p.getTcpPayloadLength());

    assertEquals(payload, new String(p.getTcpPayload()));
    assertEquals(false, p.isSyn());
    assertEquals(false, p.isFin());
    assertEquals(true, p.isAck());
    assertEquals(true, p.isPSH());
    assertEquals(2011387883, p.getTsval());
  }
}
