package org.wayfair.wiff.service;

import org.apache.log4j.Logger;
import org.wayfair.wiff.reporter.WiffReporter;

public abstract class WiffService<T, S> {

  protected final Logger    LOGGER = Logger.getLogger(this.getClass());

  protected WiffReporter<S> reporter;
  protected Thread          reporterThread;

  public enum Services {
    WiffStitch, WiffRoundtrip, ParseBytes;
  }

  public WiffService() {
  }

  /**
   * @param reporter
   *          a reporter to which this service will send data
   */
  public WiffService(WiffReporter<S> reporter) {
    this.reporter = reporter;
  }

  public abstract void processData(T input);

  /**
   * Starts the reporter on its own thread
   */
  public void startReporter() {
    if (reporter != null) {
      reporterThread = new Thread(reporter);
      reporterThread.start();
    }
  }

  /**
   * Safely stops this service
   */
  public void stop() {
    stopReporter();
  }

  /**
   * Stops the reporter thread
   */
  public void stopReporter() {
    if (reporter != null) {
      LOGGER.info("Calling shut down on reporter " + reporter.getClass());
      try {
        reporter.stop();

        /*
         * Send kill message to reporter. This is for the case in which the
         * reporter's message queue is empty and it is waiting and therefore
         * does not stop execution.
         */
        reporter.sendData(reporter.getKillPill());

        // Wait for reporter to die
        reporterThread.join();
      } catch (InterruptedException e) {
        LOGGER.error("", e);
      }
    }
  }
}