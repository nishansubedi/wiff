package org.wayfair.wiff.core;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import org.apache.log4j.Logger;
import org.wayfair.wiff.util.WiffByteBuffer;

public class ByteBufferPool {
  private ArrayList<WiffByteBuffer> buffers;
  private ArrayList<Boolean>        available;
  private Semaphore                 sema;

  private int                       size;

  private final Logger              LOGGER = Logger.getLogger(this.getClass());

  /**
   * Create a pool of 100 buffers, each of the theoretical max datagram size
   * (65535 bytes)
   */
  public ByteBufferPool() {
    this(100, 65535);
  }

  /**
   * @param size
   *          the number of buffers in the pool
   * @param bufferCapacity
   *          the size of each buffer in bytes
   */
  public ByteBufferPool(int size, int bufferCapacity) {
    buffers = new ArrayList<WiffByteBuffer>(size);
    available = new ArrayList<Boolean>(size);
    sema = new Semaphore(size);

    loadByteBuffers(size, bufferCapacity);
  }

  /**
   * Gets an available buffer from the pool
   * 
   * @return an available WiffByteBuffer, null if none are free
   */
  public synchronized WiffByteBuffer getByteBuffer() {
    WiffByteBuffer buffer = null;
    try {
      sema.acquire();
      for (int i = 0; i < size; i++) {
        if (available.get(i)) {
          buffer = buffers.get(i);
          available.set(i, false);
          break;
        }
      }
    } catch (InterruptedException e) {
      LOGGER.info("", e);
    }

    if (buffer == null) {
      sema.release();
    }
    return buffer;
  }

  /**
   * Returns a buffer to the pool
   * 
   * @param buffer
   *          the buffer to return to the pool
   */
  public void returnBuffer(WiffByteBuffer buffer) {
    buffer.clear();
    available.set(buffer.getID(), true);

    sema.release();
  }

  /**
   * Adds buffers to the pool
   * 
   * @param num
   *          the number of buffers to add to the pool
   * @param bufferCapacity
   *          the capacity in bytes of the new buffers
   */
  private void loadByteBuffers(int num, int bufferCapacity) {
    for (int i = 0; i < num; i++) {
      buffers.add(new WiffByteBuffer(i, bufferCapacity));
      available.add(true);
    }
    size += num;
    LOGGER.info("Pool Size: " + size);
  }

  public int size() {
    return size;
  }

  public int available() {
    return sema.availablePermits();
  }
}
