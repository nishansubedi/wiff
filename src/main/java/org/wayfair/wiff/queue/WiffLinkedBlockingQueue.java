package org.wayfair.wiff.queue;

import java.util.concurrent.LinkedBlockingQueue;

public class WiffLinkedBlockingQueue<T> implements WiffQueue<T> {

  private LinkedBlockingQueue<T> queue;

  public WiffLinkedBlockingQueue() {
    this.queue = new LinkedBlockingQueue<T>();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.wayfair.wiff.queue.WiffQueue#add(java.lang.Object)
   */
  public boolean add(T element) throws WiffQueueAdditionException {
    boolean success = false;
    try {
      queue.put(element);
      success = true;
    } catch (InterruptedException e) {
      throw new WiffQueueAdditionException(e.getMessage(), e);
    }
    return success;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.wayfair.wiff.queue.WiffQueue#remove()
   */
  public T remove() throws WiffQueueRemovalException {
    try {
      return queue.take();
    } catch (InterruptedException e) {
      throw new WiffQueueRemovalException(e.getMessage(), e);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.wayfair.wiff.queue.WiffQueue#size()
   */
  public int size() {
    return queue.size();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.wayfair.wiff.queue.WiffQueue#teardown()
   */
  public void teardown() {
  }
}
