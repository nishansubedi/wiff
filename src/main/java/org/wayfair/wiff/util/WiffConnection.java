package org.wayfair.wiff.util;



public class WiffConnection {
  private String sourceIp;
  private int    sourcePort;
  private String destinationIp;
  private int    destinationPort;

  /**
   * @param sourceIp
   *          a connection's source IP address
   * @param sourcePort
   *          a connection's source port
   * @param destinationIp
   *          a connection's destination IP address
   * @param destinationPort
   *          a connection's destination port
   */
  public WiffConnection(String sourceIp, int sourcePort, String destinationIp,
      int destinationPort) {
    this.sourceIp = sourceIp;
    this.sourcePort = sourcePort;
    this.destinationIp = destinationIp;
    this.destinationPort = destinationPort;
  }

  /**
   * @param packet
   *          a packet object describing this TCP packet
   * @throws Exception
   */
  public WiffConnection(WiffPacket packet) throws Exception {
    sourceIp = packet.getSourceIP();
    sourcePort = packet.getSourcePort();
    destinationIp = packet.getDestinationIP();
    destinationPort = packet.getDestinationPort();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    } else if (o == this) {
      return true;
    } else {
      if (!(o instanceof WiffConnection))
        return false;
      WiffConnection conn = (WiffConnection) o;

      return (conn.getSourceIp().equals(sourceIp)
          && conn.getSourcePort() == sourcePort
          && conn.getDestinationIp().equals(destinationIp) && conn
          .getDestinationPort() == destinationPort)
          || (conn.getSourceIp().equals(destinationIp)
              && conn.getSourcePort() == destinationPort
              && conn.getDestinationIp().equals(sourceIp) && conn
              .getDestinationPort() == sourcePort);
    }
  }

  /**
   * @return the connection's source IP address
   */
  public String getSourceIp() {
    return sourceIp;
  }

  /**
   * @return the connection's source port
   */
  public int getSourcePort() {
    return sourcePort;
  }

  /**
   * @return the connection's destination IP address
   */
  public String getDestinationIp() {
    return destinationIp;
  }

  /**
   * @return the connection's destination port
   */
  public int getDestinationPort() {
    return destinationPort;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    return ((sourceIp.hashCode() ^ sourcePort) ^ (destinationIp.hashCode() ^ destinationPort));
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "\nSource IP: " + sourceIp + "\nSource Port: " + sourcePort
        + "\nDestination IP: " + destinationIp + "\nDestination Port: "
        + destinationPort;
  }
}