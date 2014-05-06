package org.wayfair.wiff.processor;

import org.apache.log4j.Logger;
import org.wayfair.wiff.queue.WiffQueue;

public abstract class WiffProcessor {
  protected WiffQueue<byte[]> queue;
  protected final Logger      LOGGER = Logger.getLogger(this.getClass());

  public enum Processors {
    RabbitMQProcessor;
  }

  /**
   * Creates a processor that inserts its data into the given queue
   * 
   * @param queue
   */
  public WiffProcessor(WiffQueue<byte[]> queue) {
    this.queue = queue;
  }

  /**
   * Safely stops this processor's execution
   * 
   */
  public abstract void stop();
}
