package org.wayfair.wiff.core;

import org.apache.log4j.Logger;
import org.jnetpcap.*;
import org.wayfair.wiff.queue.WiffQueue;
import org.wayfair.wiff.queue.WiffQueue.WiffQueueAdditionException;
import org.wayfair.wiff.util.WiffByteBuffer;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import static java.nio.file.StandardWatchEventKinds.*;

public class WiffCapture implements Runnable {
  private static Pcap               pcap;

  // Will be filled with NICs
  private List<PcapIf>              captureDevices;

  private ByteBufferHandler<String> handler;

  // For any error msgs
  private StringBuilder             errbuf;

  // Capture all packets, no trucation
  private final int                 snaplen = 64 * 1024;
  // capture final all packets
  private final int                 flags   = Pcap.MODE_PROMISCUOUS;

  // 10 seconds in millis
  private final int                 timeout = 10 * 1000;

  private final boolean             offline;
  private final boolean             streamMode;

  private boolean                   running = false;
  private String                    tcpFilter;
  private float                     totalCount;

  private String                    captureSoure;
  private WiffQueue<WiffByteBuffer> queue;
  private ByteBufferPool            pool;

  private final Logger              LOGGER  = Logger.getLogger(this.getClass());

  /**
   * Creates a capture object that pulls packets from the first capture device
   * it locates and pushes them onto the queue
   * 
   * @param queue
   *          the queue onto which this object places captured packets
   */
  public WiffCapture(final WiffQueue<WiffByteBuffer> queue, ByteBufferPool pool) {
    this("0", queue, pool, "");
  }

  /**
   * Creates a capture object that pulls packets from the specified capture
   * device it locates and pushes them onto the queue
   * 
   * @param captureSource
   *          a string containing the name of the capture device or it's index
   *          in the available capture device list
   * @param queue
   *          the queue onto which this object places captured packets
   */
  public WiffCapture(String captureSource,
      final WiffQueue<WiffByteBuffer> queue, final ByteBufferPool pool,
      String tcpFilter) {

    this.tcpFilter = tcpFilter;
    this.captureSoure = captureSource;
    this.queue = queue;
    this.pool = pool;

    errbuf = new StringBuilder();
    captureDevices = new ArrayList<PcapIf>();

    File source = new File(captureSource);
    if (source.exists()) {
      offline = source.isFile();
      streamMode = !offline && !source.isDirectory();
    } else {
      offline = false;
      streamMode = true;
    }

    if (offline) {
      // Create capture object
      LOGGER.info("Opening offline capture from file: " + captureSource);
      pcap = Pcap.openOffline(captureSource, errbuf);
    } else if (streamMode) {
      // Load and display all available capture devices
      getCaptureDevices();
      displayCaptureDevices();

      // Get the requested capture device
      PcapIf device = null;
      try {
        // read capture source as index into the capture device array
        device = captureDevices.get(Integer.parseInt(captureSource));
      } catch (NumberFormatException e) {
        // if the capture source is not numeric, then try to match on the name
        for (PcapIf d : captureDevices) {
          if (d.getName().equals(captureSource)) {
            device = d;
            break;
          }
        }
      }

      if (device != null) {
        LOGGER.info("Using: " + device.getName());

        // Create capture object
        if (Pcap.isPcap100Loaded()) {
          /*
           * http://stackoverflow.com/questions/7763321/libpcap-to-capture-10-gbps
           * -nic pretty nice article on speeding up libpcap captures
           */
          LOGGER.info("Pcap API 1.0.0 or above is loaded.");
          pcap = Pcap.create(device.getName(), errbuf);
          pcap.setSnaplen(snaplen);
          pcap.setPromisc(flags);
          pcap.setTimeout(timeout);

          pcap.setDirection(Pcap.Direction.INOUT);
          pcap.setBufferSize(128 * 1024 * 1024);
          pcap.activate();
        } else {
          pcap = Pcap.openLive(device.getName(), snaplen, flags, timeout,
              errbuf);
        }
      } else {
        LOGGER.error("Could not open specified capture device (" + captureSource
            + "). Exiting...");
        System.exit(1);
      }
    }

    handler = new ByteBufferHandler<String>() {
      // float received, dropped;
      // PcapStat stats;
      int            max_packet = 0;
      WiffByteBuffer b;

      public void nextPacket(PcapHeader arg0, ByteBuffer buffer, String arg2) {
        try {
          if (buffer.capacity() > max_packet) {
            max_packet = buffer.capacity();
            LOGGER.debug("Largest Packet Size: " + max_packet + " bytes");
          }

          b = pool.getByteBuffer();
          b.put(buffer);
          b.flip();
          queue.add(b);

        } catch (WiffQueueAdditionException e) {
          LOGGER.error("", e);
        }
        ++totalCount;
      }
    };
  }

  /**
   * Captures network packets
   */
  public void run() {
    running = true;
    if (offline || streamMode) {
      if (pcap != null) {
        LOGGER.info("Pcap major version: " + pcap.majorVersion());
        LOGGER.info("Pcap minor version: " + pcap.minorVersion());
        LOGGER.info("Pcap library version: " + Pcap.libVersion());

        // Set tcpdump filter
        if (tcpFilter != null && !tcpFilter.isEmpty()) {
          try {
            LOGGER.info("Setting tcpdump filter: " + tcpFilter);
            setStreamFilter(pcap, tcpFilter);
          } catch (IllegalArgumentException e) {
            LOGGER.error("", e);
          }
        }
      } else {
        LOGGER.error("Error while opening capture source: " + errbuf);
        System.exit(1);
      }
      pcap.loop(Pcap.LOOP_INFINITE, handler, "");
    } else {
      try {
        watchDirectory(captureSoure);
      } catch (IOException | InterruptedException e) {
        LOGGER.error("", e);
      }
    }
  }

  /**
   * Stop capturing packets
   */
  public void stop() {
    running = false;
    if (offline || streamMode) {
      pcap.breakloop();
    }
  }

  /**
   * Loads all available capture devices
   */
  private void getCaptureDevices() {
    int r = Pcap.findAllDevs(captureDevices, errbuf);
    if (r == Pcap.ERROR || captureDevices.isEmpty()) {
      LOGGER.error("Can't read list of devices, error is %s" + errbuf);
      System.exit(1);
    }
  }

  /**
   * Display available capture devices to the user
   */
  private void displayCaptureDevices() {
    LOGGER.info("Network devices found:");

    int i = 0;
    String description;
    for (PcapIf device : captureDevices) {
      description = (i++) + ": " + device.getName() + " ";
      description += (device.getDescription() != null) ? device
          .getDescription() : "No description available";
      LOGGER.info(description);
    }
  }

  /**
   * Sets a tcpdump filter on the incoming packets
   */
  /**
   * @param pcap
   *          the pcap object on which the filter will be applied
   * @param packet_filter
   *          the TCP filter
   * @throws IllegalArgumentException
   */
  private void setStreamFilter(Pcap pcap, String packet_filter)
      throws IllegalArgumentException {
    PcapBpfProgram program = new PcapBpfProgram();
    int optimize = 0; // 0 = false
    int netmask = 0xFFFFFF00; // 255.255.255.0

    if (pcap.compile(program, packet_filter, optimize, netmask) != Pcap.OK) {
      throw new IllegalArgumentException(
          "Problem compiling tcpdump filter with error" + pcap.getErr());
    }

    if (pcap.setFilter(program) != Pcap.OK) {
      throw new IllegalArgumentException(
          "Problem setting tcpdump filter with error" + pcap.getErr());
    }
  }

  /**
   * @return the number of dropped packets
   */
  public long getDroppedPackets() {
    PcapStat stats = new PcapStat();
    pcap.stats(stats);
    return stats.getDrop();
  }

  /**
   * Watches the specified directory for capture files. If more than two files
   * are present all but the two most recent files are read. We leave two files
   * in case they are still being written to.
   * 
   * @param directory
   *          the path to the directory to watch
   * @throws IOException
   * @throws InterruptedException
   */
  private void watchDirectory(String directory) throws IOException,
      InterruptedException {
    // Get path and create watch
    Path path = Paths.get(directory);
    WatchService watcher = FileSystems.getDefault().newWatchService();
    path.register(watcher, ENTRY_CREATE);

    File file = null;
    File[] files = null;
    File dir = path.toFile();
    File oldestCapture = null;

    FileFilter filter = new FileFilter() {
      public boolean accept(File file) {
        return file.isFile() && file.getName().matches(".*\\.p?cap.*");
      }
    };

    int numFiles = dir.listFiles(filter).length;
    int captureIndex = -1;
    long start = 0, timeModified;
    WatchKey watchKey = null;
    while (running) {
      if (numFiles < 3) {
        watchKey = watcher.take();
        numFiles += watchKey.pollEvents().size();
      }

      // get files
      files = dir.listFiles(new FileFilter() {
        public boolean accept(File file) {
          return file.isFile();
        }
      });

      // process batch
      for (int i = 0; i < numFiles - 2; i++) {
        // get oldest file
        timeModified = Long.MAX_VALUE;
        for (int j = 0; j < files.length; j++) {
          file = files[j];
          if (file != null && file.lastModified() < timeModified) {
            timeModified = file.lastModified();
            oldestCapture = file;
            captureIndex = j;
          }
        }
        if (oldestCapture != null) {
          if (LOGGER.isDebugEnabled()) {
            start = System.currentTimeMillis();
          }
          // Open cature file
          pcap = Pcap.openOffline(oldestCapture.getPath(), errbuf);

          /*
           * Start the processing the file. The loop should break automatically
           * when complete
           */
          if (pcap != null) {
            // set tcp filter
            if (tcpFilter != null && !tcpFilter.isEmpty()) {
              setStreamFilter(pcap, tcpFilter);
            }

            pcap.loop(Pcap.LOOP_INFINITE, handler, "");
            pcap.close();

            /*
             * Delete file so that we don't fill the disk with data that has
             * already been processed.
             */
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug("Deleting file " + oldestCapture.getName());
            }
            if (!oldestCapture.delete() && running) {
              LOGGER
                  .error("Could not delete capture file after processing. Exiting...");
              System.exit(1);
            }
            oldestCapture = null;
            files[captureIndex] = null;
            captureIndex = -1;
            pcap = null;

            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug("Total received from this file: " + totalCount);
              LOGGER.debug("Queue size: " + queue.size());
              LOGGER.debug("Time to process: "
                  + ((System.currentTimeMillis() - start)) + " milliseconds\n");
              totalCount = 0;
              LOGGER.debug("Pool size: " + pool.available());
            }
          } else {
            LOGGER.error("Unable to read " + oldestCapture.getPath()
                + ". Retrying...");
          }
        }
        if (!running) {
          break;
        }
      }
      numFiles = 2;
      if (watchKey != null) {
        watchKey.reset();
      }
    }
  }
}
