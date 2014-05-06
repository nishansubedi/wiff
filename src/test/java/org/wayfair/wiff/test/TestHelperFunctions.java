package org.wayfair.wiff.test;

import org.jnetpcap.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.jnetpcap.Pcap;

public class TestHelperFunctions {
  public static ArrayList<byte[]> readPacktsFromFile(String filePath) {
    final ArrayList<byte[]> packets = new ArrayList<byte[]>();
    StringBuilder errbuf = new StringBuilder();
    Pcap pcap = Pcap.openOffline(filePath, errbuf);
    ByteBufferHandler<String> handler = new ByteBufferHandler<String>() {
      public void nextPacket(PcapHeader arg0, ByteBuffer buffer, String arg2) {
        byte[] b = new byte[buffer.capacity()];
        buffer.get(b);
        packets.add(b);
      }
    };

    pcap.loop(Pcap.LOOP_INFINITE, handler, "");
    return packets;
  }

  public static byte[] readBytesFromFile(String path) throws IOException {
    return Files.readAllBytes(Paths.get(path));
  }

  public static String readFile(String path) throws IOException {
    return new String(readBytesFromFile(path));
  }
}
