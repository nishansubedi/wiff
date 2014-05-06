package org.wayfair.wiff.consumer;

import org.apache.log4j.Logger;
import org.wayfair.wiff.queue.WiffQueue;
import org.wayfair.wiff.queue.WiffQueue.WiffQueueAdditionException;

public abstract class WiffConsumer<T> implements Runnable {
  protected WiffQueue<T> queue;
  protected T            killPill;
  protected boolean      running;
  protected final Logger LOGGER = Logger.getLogger(this.getClass());

  public enum Consumers {
    PacketConsumer, BytesConsumer;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Runnable#run()
   */
  public abstract void run();

  /**
   * Safely stops this comsumer's execution
   * 
   */
  public void stop() {
    running = false;
    try {
      queue.add(killPill);
    } catch (WiffQueueAdditionException e) {
      LOGGER.info("Could not insert kill pill.");
    }
  }

  /**
   * Indicates if the run method is executing
   * 
   * @return whether or not the run method is executing
   */
  public boolean isRunning() {
    return running;
  }
}
