package org.wayfair.wiff.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.wayfair.wiff.core.Wiff;
import org.wayfair.wiff.util.WiffPacket;
import org.wayfair.wiff.util.ssl.SSLDecryptor;

public class TcpReconstructor {
  private int[]                   src_addr      = new int[2];
  private int[]                   src_port      = new int[2];
  private long[]                  seq_no        = new long[2];
  private boolean[]               finished      = new boolean[2];
  private static int              ssl_port;
  private String                  serverIP;

  private LinkedList<TcpFragment> fragments     = new LinkedList<TcpFragment>();

  private LinkedList<byte[]>      out           = new LinkedList<byte[]>();

  private int                     bytes_written = 0;

  private final Logger            LOGGER        = Logger.getLogger(this
                                                    .getClass());

  private SSLDecryptor            decryptor;

  static {
    ssl_port = Wiff.getSSLPort();
  }

  public TcpReconstructor() {
    decryptor = new SSLDecryptor();
  }

  public TcpReconstructor(String privateKeyFile) {
    decryptor = new SSLDecryptor(privateKeyFile);
  }

  /**
   * Puts this packet's payload in its proper location with in the TCP stream,
   * or creates a fragment if it cannot find its location.
   * 
   * @param p
   *          the packet object describing this packet
   * @return true, if this packet completes the TCP stream, otherwise false.
   * @throws IOException
   */
  public synchronized boolean ReassemblePacket(WiffPacket p) throws IOException {
    if (isClosed()) {
      return false;
    }
    return reassemble_tcp(p.getSequenceNumber(), p.getAckNumber(),
        p.getTcpPayload(), p.isSyn(), p.isFin(), p.isAck(), p.isPSH(),
        p.getSourceIPAsInt(), p.getSourcePort(), p.getDestinationIPAsInt(),
        p.getDestinationPort(), p.getSourceIP(), p.getDestinationIP());
  }

  /**
   * Puts this packet's payload in its proper location with in the TCP stream,
   * or creates a fragment if it cannot find its location.
   * 
   * @param sequence
   *          the packet's sequence number
   * @param acknowledge
   *          the packet's acknowledge number
   * @param data
   *          the packet's payload
   * @param synflag
   *          indicates whether the SYN flag raised on this packet
   * @param finflag
   *          indicates whether the FIN flag raised on this packet
   * @param ackflag
   *          indicates whether the ACK flag raised on this packet
   * @param pshflag
   *          indicates whether the PSH flag raised on this packet
   * @param net_src
   *          the packet's source IP address as an int
   * @param srcport
   *          the packet's source port
   * @param net_src
   *          the packet's destination IP address as an int
   * @param srcport
   *          the packet's destination port
   * @return true, if this packet completes the TCP stream, otherwise false.
   * @throws IOException
   */
  public boolean reassemble_tcp(long sequence, long acknowledge, byte[] data,
      boolean synflag, boolean finflag, boolean ackflag, boolean pshflag,
      int net_src, int srcport, int net_dst, int dstport, String src_ip,
      String dst_ip) throws IOException {

    int src_index = -1;

    // Gather information from the handshake
    if (synflag) {
      src_index = ackflag ? 1 : 0;

      if (src_addr[src_index] == 0) {
        src_addr[src_index] = net_src;
        src_port[src_index] = srcport;
        seq_no[src_index] = sequence + 1;
        LOGGER.debug("SYN: " + sequence + " " + src_index);

        if (src_index == 1) {
          byte[] tmp = ("\"source_ip\" : \"" + dst_ip
              + "\", \"source_port\" : \"" + dstport
              + "\", \"destination_ip\" : \"" + src_ip
              + "\", \"destination_port\" : \"" + srcport + "\"\r\n")
              .getBytes();
          out.add(tmp);
          bytes_written += tmp.length;
          serverIP = src_ip;
        }

        /*
         * Due to multi-threading we could have fragments before the connection
         * is established. Grab any packets we are expecting.
         */
        while (check_fragments())
          ;

        /*
         * We can derive the client info if the server's packet is processed
         * first.
         */
        if (src_index == 1 && src_addr[0] == 0) {
          src_addr[0] = net_dst;
          src_port[0] = dstport;
          seq_no[0] = acknowledge;
          LOGGER.debug(" Derived SYN: " + seq_no[0]);
          while (check_fragments())
            ;
        }
      } else if (src_index == 1) {
        LOGGER.debug("Additional Stream");
        addFragment(sequence, acknowledge, data, synflag, ackflag, finflag);
      }

      // the handshake is only the beginning
      return false;
    }

    /*
     * For multi-threading: we may process a packet before we got the syn. Save
     * it for later.
     */
    if (src_addr[0] == 0 || src_addr[1] == 0) {
      addFragment(sequence, acknowledge, data, synflag, ackflag, finflag);
      return false;
    }

    // Make sure there are no fragments that might fit
    while (check_fragments())
      ;

    src_index = (src_addr[0] == net_src && src_port[0] == srcport) ? 0 : 1;
    if (seq_no[src_index] == sequence
        && acknowledge == seq_no[(src_index + 1) % 2]) {
      /*
       * We are expecting this packet. Grab it's data and calculate the next
       * sequence numbe.
       */
      write_packet_data(data, src_index == 0);
      seq_no[src_index] += data.length;
      LOGGER.debug("On time: " + sequence);

      // See if adding this packets makes a fragment fit
      while (check_fragments())
        ;
    } else {
      // Out of order. Create fragment
      addFragment(sequence, acknowledge, data, synflag, ackflag, finflag);
      LOGGER.debug("Out of Order: " + sequence + " Expecting: "
          + seq_no[src_index]);
      return false;
    }

    // teardown
    if (finflag) {
      LOGGER.debug("Finished: " + src_index);
      finished[src_index] = true;
      seq_no[src_index]++;
    }
    if (isClosed()) {

      while (isClosed() && findNewStream()) {
        check_fragments();
      }
    }
    if (isClosed()) {
      reset_tcp_reassembly();
      return true;
    }
    return false;
  }

  /**
   * Checks for a new SYN,SYN-ACK pair and loads the next expected sequence
   * numbers
   * 
   * @return true if a new stream has been found. Otherwise, false.
   */
  private boolean findNewStream() {
    TcpFragment fragment;
    Iterator<TcpFragment> it = fragments.iterator();
    while (it.hasNext()) {
      fragment = it.next();
      if (fragment.syn && fragment.ack) {
        seq_no[0] = fragment.ack_no;
        seq_no[1] = fragment.seq_no + 1;

        finished[0] = false;
        finished[1] = false;

        it.remove();
        LOGGER.debug("Found stream: " + seq_no[0]);

        return true;
      }
    }
    return false;
  }

  /**
   * Creates an object containing information needed to put a TCP packet's
   * payload in the right place within a TCP stream and stores it for use later
   * 
   * @param sequence
   *          the packet's sequence number
   * @param acknowledge
   *          the packet's acknowledge number
   * @param data
   *          the packet's payload
   * @param syn
   *          indicates whether the SYN flag raised on this packet
   * @param ack
   *          indicates whether the ACK flag raised on this packet
   * @param fin
   *          indicates whether the FIN flag raised on this packet
   */
  private void addFragment(long sequence, long acknowledge, byte[] data,
      boolean syn, boolean ack, boolean fin) {
    if (fin || syn || data.length > 0) {
      LOGGER.debug("Made Fragment: " + sequence);
      fragments
          .add(new TcpFragment(sequence, acknowledge, data, syn, ack, fin));
    }
  }

  /**
   * Searches through the TCP fragments for one that fits. If it finds one, it
   * is written out to the TCP stream
   * 
   * @param index
   *          0 if it should find fragments from the client, 1 if for the server
   * @return true, if a fragment is located. Otherwise, false.
   * @throws IOException
   */
  private boolean check_fragments() throws IOException {
    int index;
    TcpFragment fragment;
    Iterator<TcpFragment> it = fragments.iterator();
    while (it.hasNext()) {
      fragment = it.next();
      index = -1;
      if (fragment.seq_no == seq_no[0] && fragment.ack_no == seq_no[1]) {
        index = 0;
      } else if (fragment.seq_no == seq_no[1] && fragment.ack_no == seq_no[0]) {
        index = 1;
      }
      if (index > -1) {
        write_packet_data(fragment.data, index == 0);
        seq_no[index] += fragment.data.length;
        if (fragment.fin) {
          LOGGER.debug("Finished: " + index);
          finished[index] = true;
          seq_no[index]++;
        }
        it.remove();
        LOGGER.debug("Found Fragment: " + seq_no[index]);
        return true;
      }
    }
    return false;
  }

  /**
   * Writes packet's payload to the TCP stream
   * 
   * @param index
   *          0 if the data is from the client, 1 if from the server
   * @param data
   *          the data to we written
   * @throws IOException
   */
  private void write_packet_data(byte[] data, boolean isClient)
      throws IOException {
    if (data.length > 0) {
      byte[] d = data;
      if (isSSL() && decryptor != null) {
        d = decryptor.process(data, isClient, serverIP);
      }
      if (d != null && d.length > 0) {
        out.add(d);
        bytes_written += data.length;
      }
    }
  }

  /**
   * Determines if the TCP stream is complete.
   * 
   * @return true, if the TCP stream is complete.
   */
  public boolean isClosed() {
    return finished[0] && finished[1];
  }

  /**
   * Closes a TCP stream if it is not already closed.
   * 
   * @throws IOException
   */
  public synchronized void close() {
    if (!isClosed()) {
      try {
        finished[0] = true;
        finished[1] = true;

        while (isClosed() && findNewStream()) {
          check_fragments();

          finished[0] = true;
          finished[1] = true;
        }
        reset_tcp_reassembly();
      } catch (IOException e) {
        LOGGER.error("", e);
      }
    }
  }

  /**
   * Clears all internal storage with the exception of the tcp steam, if closed.
   */
  private void reset_tcp_reassembly() {
    src_addr = null;
    src_port = null;
    seq_no = null;

    fragments.clear();
    fragments = null;
  }

  private ByteArrayOutputStream getOutputStream() throws IOException {
    if (out.size() < 2) {
      return null;
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream(bytes_written);

    byte[] bytes = out.poll();
    while (bytes != null) {
      output.write(bytes);
      bytes = out.poll();
    }
    return output;

  }

  /**
   * Returns the TCP stream as a byte array, if the stream is closed. Otherwise,
   * null.
   * 
   * @return the bytes of the TCP stream
   */
  public byte[] getBytes() {
    byte[] bytes = null;
    if (isClosed()) {
      try {
        ByteArrayOutputStream outStream = getOutputStream();
        if (outStream != null) {
          bytes = outStream.toByteArray();
        }
      } catch (IOException e) {
        LOGGER.error("", e);
      }
    }
    return bytes;
  }

  /**
   * Returns the TCP stream as a string, if the stream is closed. Otherwise,
   * null.
   * 
   * @return the bytes of the TCP stream
   */
  @Override
  public String toString() {
    String stream = null;
    if (isClosed()) {
      byte[] bytes = getBytes();
      if (bytes != null && bytes.length > 0) {
        stream = new String(bytes);
      }
    }
    return stream;
  }

  private boolean isSSL() {
    return src_port[1] == ssl_port;
  }
}

class TcpFragment {
  public long   seq_no = 0;
  public long   ack_no = 0;
  public byte[] data   = null;
  public boolean syn, ack, fin;

  /**
   * Creates an object containing information needed to put a TCP packet's
   * payload in the right place within a TCP stream
   * 
   * @param sequence
   *          the packet's sequence number
   * @param acknowledge
   *          the packet's acknowledge number
   * @param data
   *          the packet's payload
   * @param syn
   *          indicates whether the SYN flag raised on this packet
   * @param ack
   *          indicates whether the ACK flag raised on this packet
   * @param fin
   *          indicates whether the FIN flag raised on this packet
   */
  public TcpFragment(long sequence, long acknowledge, byte[] data, boolean syn,
      boolean ack, boolean fin) {
    seq_no = sequence;
    ack_no = acknowledge;
    this.data = data;
    this.syn = syn;
    this.ack = ack;
    this.fin = fin;
  }
}