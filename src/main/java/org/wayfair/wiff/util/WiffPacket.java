package org.wayfair.wiff.util;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;
import org.jnetpcap.packet.format.FormatUtils;

public class WiffPacket {
  private ByteBuffer     buffer;
  private int            size;

  protected final Logger LOGGER = Logger.getLogger(this.getClass());

  public WiffPacket() {
  }

  public WiffPacket(byte[] bytes) {
    buffer = ByteBuffer.wrap(bytes);
    size = bytes.length;
  }

  public void setPacket(ByteBuffer buffer) {
    this.buffer = buffer;
    size = buffer.limit();
  }

  /* Ethernet */

  public int getEthHeaderLength() {
    return 14;
  }

  /* IP */

  /**
   * @return the number of bytes the packet contains
   */
  public int getPacketByteLength() {
    return size;
  }

  public byte[] source() {
    int offset = getEthHeaderLength() + 12;

    byte[] bytes = new byte[4];
    buffer.clear().position(offset).limit(offset + 4);
    buffer.get(bytes);

    return bytes;
  }

  public byte[] destination() {
    int offset = getEthHeaderLength() + 16;

    byte[] bytes = new byte[4];
    buffer.clear().position(offset).limit(offset + 4);
    buffer.get(bytes);

    return bytes;
  }

  /**
   * @return the source IP address
   */
  public String getSourceIP() {
    return FormatUtils.ip(source());
  }

  /**
   * @return the destination IP address
   */
  public String getDestinationIP() {
    return FormatUtils.ip(destination());
  }

  /**
   * @return the source ip
   */
  public int getSourceIPAsInt() {
    return getUInt(source());
  }

  /**
   * @return the destination ip
   */
  public int getDestinationIPAsInt() {
    return getUInt(destination());
  }

  public int getIpHeaderLength() {
    int offset = getEthHeaderLength();

    byte[] bytes = new byte[1];
    buffer.clear().position(offset).limit(offset + 1);
    buffer.get(bytes);

    return (bytes[0] & 0x0000000F) * 4;
  }

  /* TCP */

  /**
   * @return true if a TCP header is present
   */
  public boolean hasTcpHeader() {
    int offset = getEthHeaderLength() + 9;

    byte[] bytes = new byte[1];
    buffer.clear().position(offset).limit(offset + 1);
    buffer.get(bytes);

    return (bytes[0] & 0xFF) == 0x06;
  }

  /**
   * @return the number of bytes in the TCP header
   */
  public int getTcpHeaderLength() {
    int length = 0;
    if (hasTcpHeader()) {
      int offset = getEthHeaderLength() + getIpHeaderLength() + 12;

      byte[] bytes = new byte[1];
      buffer.clear().position(offset).limit(offset + 1);
      buffer.get(bytes);

      length = (((bytes[0] & 0xff) >> 4) & 0x0000000f) * 4;
    }
    return length;
  }

  /**
   * @return the source port
   */
  public int getSourcePort() {
    int offset = getEthHeaderLength() + getIpHeaderLength();

    byte[] bytes = new byte[2];
    buffer.clear().position(offset).limit(offset + 2);
    buffer.get(bytes);

    return getUShortAsUInt(bytes);
  }

  /**
   * @return the destination port
   */
  public int getDestinationPort() {
    int offset = getEthHeaderLength() + getIpHeaderLength() + 2;

    byte[] bytes = new byte[2];
    buffer.clear().position(offset).limit(offset + 2);
    buffer.get(bytes);

    return getUShortAsUInt(bytes);
  }

  /**
   * @return the packet's sequence number
   */
  public long getSequenceNumber() {
    int offset = getEthHeaderLength() + getIpHeaderLength() + 4;

    byte[] bytes = new byte[4];
    buffer.clear().position(offset).limit(offset + 4);
    buffer.get(bytes);

    return getULong(bytes);
  }

  public long getAckNumber() {
    int offset = getEthHeaderLength() + getIpHeaderLength() + 8;

    byte[] bytes = new byte[4];
    buffer.clear().position(offset).limit(offset + 4);
    buffer.get(bytes);

    return getULong(bytes);
  }

  /**
   * @return the number of bytes in the packet's payload
   */
  public int getTcpPayloadLength() {
    // payload = total size - tcp header - ip header
    return getPacketByteLength() - getTcpHeaderLength() - getIpHeaderLength()
        - getEthHeaderLength();
  }

  public int getTcpPayloadOffset() {
    return getEthHeaderLength() + getTcpHeaderLength() + getIpHeaderLength();
  }

  /**
   * @return the packet's payload
   */
  public byte[] getTcpPayload() {
    byte[] payload = null;
    if (hasTcpHeader()) {
      int firstByte = getTcpPayloadOffset();
      int length = getTcpPayloadLength();

      payload = new byte[length];
      buffer.clear().position(firstByte).limit(firstByte + length);
      buffer.get(payload);
    }
    return payload;
  }

  /**
   * @return true if the packet's SYN flag is raised
   */
  public boolean isSyn() {
    int offset = getEthHeaderLength() + getIpHeaderLength() + 13;

    byte[] bytes = new byte[1];
    buffer.clear().position(offset).limit(offset + 1);
    buffer.get(bytes);

    return (bytes[0] & 0x02) == 0x02;
  }

  /**
   * @return true if the packet's FIN flag is raised
   */
  public boolean isFin() {
    int offset = getEthHeaderLength() + getIpHeaderLength() + 13;

    byte[] bytes = new byte[1];
    buffer.clear().position(offset).limit(offset + 1);
    buffer.get(bytes);

    return (bytes[0] & 0x01) == 0x01;
  }

  /**
   * @return true if the packet's ACK flag is raised
   */
  public boolean isAck() {
    int offset = getEthHeaderLength() + getIpHeaderLength() + 13;

    byte[] bytes = new byte[1];
    buffer.clear().position(offset).limit(offset + 1);
    buffer.get(bytes);

    return (bytes[0] & 0x10) == 0x10;
  }

  /**
   * @return true if the packet's PSH flag is raised
   */
  public boolean isPSH() {
    int offset = getEthHeaderLength() + getIpHeaderLength() + 13;

    byte[] bytes = new byte[1];
    buffer.clear().position(offset).limit(offset + 1);
    buffer.get(bytes);

    return (bytes[0] & 0x08) == 0x08;
  }

  public long getTsval() {
    if (hasTcpHeader()) {
      int offset = getEthHeaderLength() + getIpHeaderLength() + 20;
      int headerEnd = offset + getTcpHeaderLength() - 20;

      byte[] option = new byte[2]; // , optLen;
      while (offset + 9 < headerEnd) {
        buffer.clear().position(offset).limit(offset + 2);
        buffer.get(option);

        if ((option[0] & 0xff) == 0x08 && (option[1] & 0xff) == 0x0a) {
          byte[] bytes = new byte[4];
          buffer.clear().position(offset + 2).limit(offset + 6);
          buffer.get(bytes);
          return getULong(bytes);
        }
        offset++;
      }
    }
    return -1;
  }

  private int getUInt(byte[] bytes) {
    return ((bytes[0] & 0xff) << 24 | (bytes[1] & 0xff) << 16
        | (bytes[2] & 0xff) << 8 | (bytes[3] & 0xff) << 0) & 0xffffffff;
  }

  private long getULong(byte[] bytes) {
    return ((bytes[0] & 0xff) << 24 | (bytes[1] & 0xff) << 16
        | (bytes[2] & 0xff) << 8 | (bytes[3] & 0xff) << 0) & 0xffffffffL;
  }

  private int getUShortAsUInt(byte[] bytes) {
    return (bytes[0] << 8 | bytes[1] & 0xff) & 0xffff;
  }

  public byte[] getBytes() {
    byte[] bytes = new byte[size];
    buffer.clear().limit(size);
    buffer.get(bytes);
    return bytes;
  }
}