package org.wayfair.wiff.util;

import java.nio.ByteBuffer;

public class WiffByteBuffer {
  private final int        id;
  private final ByteBuffer buffer;

  public WiffByteBuffer(int id, int size) {
    this.id = id;
    buffer = ByteBuffer.allocate(size);
  }

  public WiffByteBuffer(int id, ByteBuffer buffer) {
    this.id = id;
    this.buffer = buffer;
  }

  public ByteBuffer asByteBuffer() {
    return buffer;
  }

  public void put(ByteBuffer src) {
    buffer.put(src);
  }

  public void put(byte[] src) {
    buffer.put(src);
  }

  public void flip() {
    buffer.flip();
  }

  public int getID() {
    return id;
  }

  public void clear() {
    buffer.clear();
  }

  public int compareTo(WiffByteBuffer b) {
    return buffer.compareTo(b.asByteBuffer());
  }
}
