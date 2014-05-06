package org.wayfair.wiff.queue;

public interface WiffQueue<T> {

  /**
   * Places an element on the queue
   * 
   * @param e
   *          the element to be inserted
   * @return true if the value was successfully inserted. Otherwise false.
   * @throws WiffQueueAdditionException
   */
  public boolean add(T e) throws WiffQueueAdditionException;

  /**
   * Returns the oldest value on the queue
   * 
   * @return the queue's oldest element
   * @throws WiffQueueRemovalException
   */
  public T remove() throws WiffQueueRemovalException;

  /**
   * Returns the number of elements in the queue
   * 
   * @return the number of elements in the queue
   */
  public int size();

  /**
   * Releases the resources used by this queue.
   */
  public void teardown();

  class WiffQueueAdditionException extends Exception {
    private static final long serialVersionUID = 1L;

    public WiffQueueAdditionException(String message) {
      super(message);
    }

    public WiffQueueAdditionException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  class WiffQueueRemovalException extends Exception {
    private static final long serialVersionUID = 1L;

    public WiffQueueRemovalException(String message) {
      super(message);
    }

    public WiffQueueRemovalException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
