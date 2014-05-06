package org.wayfair.wiff.core;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.junit.Test;
import org.wayfair.wiff.util.WiffByteBuffer;
import org.wayfair.wiff.util.WiffPacket;

import static org.wayfair.wiff.test.TestHelperFunctions.*;

public class ByteBufferPoolTest {

  @Test
  public void test() {
    ArrayList<byte[]> packets = readPacktsFromFile("./src/test/resources/sample.pcap");
    ByteBufferPool pool = new ByteBufferPool(10, 35000);

    Consumer consumer1 = new Consumer(pool, packets, true);
    Consumer consumer2 = new Consumer(pool, packets, false);
    try {
      Thread c1 = new Thread(consumer1);
      Thread c2 = new Thread(consumer2);
      c1.start();
      c2.start();

      c1.join();
      c2.join();
    } catch (InterruptedException e) {
      fail("Test was interrupted.");
    }

    assertEquals(0, consumer1.getSum());
    assertEquals(23675, consumer2.getSum());
  }
}

class Consumer implements Runnable {
  private ByteBufferPool    pool;
  private ArrayList<byte[]> packets;
  private boolean           evens;

  private int               sum;

  public Consumer(ByteBufferPool pool, ArrayList<byte[]> packets, boolean evens) {
    this.pool = pool;
    this.packets = packets;
    this.evens = evens;
  }

  @Override
  public void run() {
    WiffPacket packet = new WiffPacket();
    int i = evens ? 0 : 1;
    while (i < packets.size()) {
      WiffByteBuffer b = pool.getByteBuffer();
      b.put(ByteBuffer.wrap(packets.get(i)));
      b.flip();
      packet.setPacket(b.asByteBuffer());
      sum += packet.getTcpPayloadLength();
      pool.returnBuffer(b);
      i += 2;
    }
  }

  public int getSum() {
    return sum;
  }
}
