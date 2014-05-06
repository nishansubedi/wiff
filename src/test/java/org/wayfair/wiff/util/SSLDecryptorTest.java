package org.wayfair.wiff.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.wayfair.wiff.test.TestHelperFunctions.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import java.util.ArrayList;

import org.wayfair.wiff.util.TcpReconstructor;

public class SSLDecryptorTest {
  @Test
  public void test() {
    ArrayList<byte[]> packets = readPacktsFromFile("./src/test/resources/rsasnakeoil2.cap");

    TcpReconstructor recon = new TcpReconstructor(
        "./src/test/resources/rsasnakeoil2.key");
    WiffPacket packet = new WiffPacket();

    for (int i = 0; i < packets.size(); i++) {
      packet.setPacket(ByteBuffer.wrap(packets.get(i)));
      try {
        recon.ReassemblePacket(packet);
      } catch (IOException e) {
        fail("Could not reassemblePacket due to IOException");
      }
    }
    recon.close();

    String actual = recon.toString();
    String expected = "\"source_ip\" : \"127.0.0.1\", \"source_port\" : \"38713\", \"destination_ip\" : \"127.0.0.1\", \"destination_port\" : \"443\"\r\nGET / HTTP/1.1\r\nHost: localhost\r\nUser-Agent: Mozilla/5.0 (X11; U; Linux i686; fr; rv:1.8.0.2) Gecko/20060308 Firefox/1.5.0.2\r\nAccept: text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5\r\nAccept-Language: fr,fr-fr;q=0.8,en-us;q=0.5,en;q=0.3\r\nAccept-Encoding: gzip,deflate\r\nAccept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7\r\nKeep-Alive: 300\r\nConnection: keep-alive\r\n\r\n";

    assertEquals(expected, actual.substring(0, actual.indexOf("\r\n\r\n") + 4));

    /*
     * packets = readPacktsFromFile("./ssl_test2.pcap");
     * 
     * recon = new TcpReconstructor(443, "./test.key"); packet = new
     * WiffPacket();
     * 
     * for (int i = 0; i < packets.size(); i++) {
     * packet.setPacket(ByteBuffer.wrap(packets.get(i))); try {
     * recon.ReassemblePacket(packet); } catch (IOException e) {
     * fail("Could not reassemblePacket due to IOException"); } } recon.close();
     * 
     * actual = recon.toString(); System.out.println(actual);
     */}
}
