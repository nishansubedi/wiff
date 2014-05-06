package org.wayfair.wiff.consumer;

import java.nio.ByteBuffer;

import org.wayfair.wiff.core.Wiff;
import org.wayfair.wiff.queue.WiffQueue;
import org.wayfair.wiff.queue.WiffQueue.WiffQueueRemovalException;

import org.wayfair.wiff.service.WiffService;

/**
 * Consumes byte arrays from a given queue and calls each service's processData
 * function on the data consumed.
 * 
 */
public class BytesConsumer extends WiffConsumer<byte[]> implements Runnable {

  /**
   * @param queue
   *          the queue from which data is retrieved
   */
  public BytesConsumer(WiffQueue<byte[]> queue) {
    this.queue = queue;
    killPill = "KILL".getBytes();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.wayfair.wiff.consumer.WiffConsumer#run()
   */
  @Override
  public void run() {
    @SuppressWarnings("unchecked")
    WiffService<byte[], ?>[] services = (WiffService<byte[], ?>[]) Wiff
        .getServices();

    ByteBuffer killSignal = ByteBuffer.wrap(killPill);

    running = true;
    while (running) {
      try {
        byte[] buffer = queue.remove();

        // check for kilk signal
        if (ByteBuffer.wrap(buffer).compareTo(killSignal) == 0) {
          break;
        }

        // pass data through services
        if (services != null) {
          for (WiffService<byte[], ?> service : services) {
            service.processData(buffer);
          }
        }
      } catch (WiffQueueRemovalException e) {
        LOGGER.error("", e);
      }
    }
  }
}
