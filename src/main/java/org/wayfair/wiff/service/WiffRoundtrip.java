package org.wayfair.wiff.service;

import java.util.concurrent.TimeUnit;

import org.wayfair.wiff.reporter.WiffReporter;
import org.wayfair.wiff.util.WiffPacket;
import static org.wayfair.wiff.util.ParseFunctions.*;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class WiffRoundtrip extends WiffService<WiffPacket, String> {
  private StringBuilder     message;
  private Cache<Long, Long> pushes;

  public WiffRoundtrip() {
    message = new StringBuilder(500);
    pushes = CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.SECONDS)
        .concurrencyLevel(1).build();
  }

  public WiffRoundtrip(WiffReporter<String> reporter) {
    this();
    this.reporter = reporter;
    startReporter();
  }

  @Override
  public void processData(WiffPacket packet) {
    if (packet.hasTcpHeader()) {
      try {
        long seq = packet.getSequenceNumber();

        // if it's a push add it to the cache
        if (packet.isPSH()) {
          int payloadSize = packet.getTcpPayloadLength();
          pushes.put(seq + payloadSize, packet.getTsval());
        } else if (packet.isFin() && packet.isAck()) {

          // if it's a finack, see if there is a matching push. if so, compute
          // roundtrip time
          Long pushTime = pushes.getIfPresent(seq);
          if (pushTime != null) {
            pushes.invalidate(seq);
            long time = packet.getTsval() - pushTime;
            if (time > 0 && reporter != null) {
              message.append("{ ");
              appendKeyValue(message, "source_ip", packet.getSourceIP());
              appendKeyValue(message, "source_port", packet.getSourcePort()
                  + "");
              appendKeyValue(message, "destination_ip",
                  packet.getDestinationIP());
              appendKeyValue(message, "destination_port",
                  packet.getDestinationPort() + "");
              appendKeyValue(message, "roundtrip time", time + "");
              message.append(" }");
              reporter.sendData(message.toString());
              message.delete(0, message.length());
            }
          }
        }
      } catch (Exception e) {
        LOGGER.error("", e);
      }
    }
  }

  public void stop() {
    super.stop();
    pushes.invalidateAll();
    pushes.cleanUp();
  }
}