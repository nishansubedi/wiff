package org.wayfair.wiff.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import org.wayfair.wiff.reporter.WiffReporter;

import java.util.concurrent.TimeUnit;

import org.wayfair.wiff.util.TcpReconstructor;
import org.wayfair.wiff.util.WiffConnection;
import org.wayfair.wiff.util.WiffPacket;

public class WiffStitch extends WiffService<WiffPacket, byte[]> {
  private Cache<WiffConnection, TcpReconstructor>           connections;
  private RemovalListener<WiffConnection, TcpReconstructor> connectionsRemovalListener;

  private Object                                            lock = new Object();

  public WiffStitch() {
    this(1);
  }

  public WiffStitch(WiffReporter<byte[]> reporter) {
    this(1, reporter);
  }

  public WiffStitch(int cacheTime, WiffReporter<byte[]> reporter) {
    this(cacheTime);
    this.reporter = reporter;
    startReporter();
  }

  public WiffStitch(int cacheTime) {
    connectionsRemovalListener = new RemovalListener<WiffConnection, TcpReconstructor>() {
      public void onRemoval(
          RemovalNotification<WiffConnection, TcpReconstructor> notification) {
        TcpReconstructor r = notification.getValue();
        if (reporter != null) {
          try {
            byte[] content = r.getBytes();
            if (content != null && content.length > 0) {
              reporter.sendData(content);
            }
          } catch (InterruptedException e) {
            LOGGER.error("", e);
          }
        }
      }
    };

    connections = CacheBuilder.newBuilder().concurrencyLevel(1)
        .expireAfterAccess(cacheTime, TimeUnit.SECONDS).recordStats()
        .removalListener(connectionsRemovalListener).build();
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.wayfair.wiff.service.WiffService#processPacket(org.wayfair.wiff.core
   * .WiffPacket)
   */
  public void processData(WiffPacket packet) {
    if (packet.hasTcpHeader()) {
      try {
        // See if we this is part of an existing session
        WiffConnection c = new WiffConnection(packet);

        TcpReconstructor r;
        synchronized (lock) {
          r = connections.getIfPresent(c);
          // create a new session if there isn't already one or if the existing
          // one is closed
          if (r == null || r.isClosed()) {
            r = new TcpReconstructor();
            connections.put(c, r);
          }
        }

        // if this packet ends the session, invalidate it in the cache
        if (r.ReassemblePacket(packet)) {
          connections.invalidate(c);
        }
      } catch (Exception e) {
        LOGGER.error("", e);
      }
    }
  }

  @Override
  public void stop() {
    // Make sure to send over any remaining completed sessions
    connections.cleanUp();
    super.stop();
  }
}