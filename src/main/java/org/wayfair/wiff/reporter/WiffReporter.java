package org.wayfair.wiff.reporter;

import java.util.concurrent.LinkedBlockingQueue;

import org.wayfair.wiff.parser.Parser;

import org.apache.log4j.Logger;

public abstract class WiffReporter<T> implements Runnable {
  protected LinkedBlockingQueue<T> messages;

  protected Parser<T, ?>           parser;

  protected boolean                running = false;
  protected T                      killSignal;

  protected final Logger           LOGGER  = Logger.getLogger(this.getClass());

  public enum Reporters {
    RabbitMQClient, ElasticsearchClient;
  }

  /**
   * Creates an instance of a reporter
   * 
   * @param killSignal
   *          the object that, if sent to this report, will cause it to shutdown
   */
  public WiffReporter(T killSignal) {
    this.killSignal = killSignal;
    messages = new LinkedBlockingQueue<T>();
  }

  /**
   * Creates an instance of a reporter
   * 
   * @param killSignal
   *          the object that, if sent to this report, will cause it to shutdown
   * @param parser
   *          a Parser object that, if present, will be used by this reporter to
   *          transform the data it receives
   */
  public WiffReporter(T killSignal, Parser<T, ?> parser) {
    this(killSignal);
    this.parser = parser;
  }

  /**
   * @param value
   * @throws InterruptedException
   */
  public void sendData(T value) throws InterruptedException {
    messages.put(value);
  }

  /**
   * @return
   * @throws InterruptedException
   */
  protected T receiveData() throws InterruptedException {
    return messages.take();
  }

  /**
   * @return
   */
  public T getKillPill() {
    return killSignal;
  }

  /**
   * Releases resources used by this reporter
   */
  public void cleanup() {
    messages.clear();
  }

  /**
   * Stops the execution of the run method and releases resources.
   */
  public void stop() {
    running = false;
  }

  /**
   * Determines if this reporter is still active.
   * 
   * @return true, if this reporter is still active.
   */
  public boolean isRunning() {
    return running;
  }
}