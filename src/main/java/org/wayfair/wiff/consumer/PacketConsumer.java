package org.wayfair.wiff.consumer;

import java.nio.ByteBuffer;

import org.wayfair.wiff.core.ByteBufferPool;
import org.wayfair.wiff.core.Wiff;
import org.wayfair.wiff.queue.WiffQueue;
import org.wayfair.wiff.service.WiffService;
import org.wayfair.wiff.util.WiffByteBuffer;
import org.wayfair.wiff.util.WiffPacket;

/**
 * Consumes WiffByteBuffers from a given queue and calls each service's
 * processData function on the data consumed. If a pool was given as well, all
 * buffers taken from the queue will be returned to it.
 * 
 */
public class PacketConsumer extends WiffConsumer<WiffByteBuffer> {
  private ByteBufferPool pool;

  /**
   * @param queue
   *          the queue from which data is retrieved
   */
  public PacketConsumer(WiffQueue<WiffByteBuffer> queue) {
    this(queue, null);
  }

  /**
   * @param queue
   *          the queue from which data is retrieved
   * @param pool
   *          the pool to which buffers taken from the queue are returned
   */
  public PacketConsumer(WiffQueue<WiffByteBuffer> queue, ByteBufferPool pool) {
    this.queue = queue;
    this.pool = pool;
    killPill = new WiffByteBuffer(-1, ByteBuffer.wrap("KILL".getBytes()));
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.wayfair.wiff.consumer.WiffConsumer#run()
   */
  public void run() {
    @SuppressWarnings("unchecked")
    WiffService<WiffPacket, ?>[] services = (WiffService<WiffPacket, ?>[]) Wiff
        .getServices();

    // A reusable wrapper of packet info
    WiffPacket packet = new WiffPacket();

    running = true;
    while (running) {
      try {
        WiffByteBuffer buffer = queue.remove();

        // check for kill signal
        if (buffer.compareTo(killPill) == 0) {
          break;
        }

        packet.setPacket(buffer.asByteBuffer());

        // pass packet info through services
        if (services != null) {
          for (WiffService<WiffPacket, ?> service : services) {
            service.processData(packet);
          }
        }

        // if a pool was given, return the buffer to it.
        if (pool != null) {
          pool.returnBuffer(buffer);
        }
      } catch (Exception e) {
        LOGGER.error("", e);
      }
    }
  }
}
