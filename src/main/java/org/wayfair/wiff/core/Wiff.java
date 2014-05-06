package org.wayfair.wiff.core;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import org.wayfair.wiff.queue.*;
import org.wayfair.wiff.processor.*;
import org.wayfair.wiff.processor.WiffProcessor.Processors;
import org.wayfair.wiff.service.*;
import org.wayfair.wiff.service.WiffService.Services;
import org.wayfair.wiff.reporter.*;
import org.wayfair.wiff.reporter.WiffReporter.Reporters;
import org.wayfair.wiff.parser.*;
import org.wayfair.wiff.parser.Parser.Parsers;
import org.wayfair.wiff.consumer.*;
import org.wayfair.wiff.consumer.WiffConsumer.Consumers;

import org.wayfair.wiff.util.WiffByteBuffer;

import java.io.IOException;
import java.util.HashMap;

abstract public class Wiff {
  private static WiffProperties          properties;
  private static HashMap<String, String> cliOptions = new HashMap<String, String>();
  private static String                  configFile = "config/wiff-dev.conf";

  private static WiffQueue               queue;
  private static WiffCapture             capture;
  private static Thread                  captureThread;
  private static WiffProcessor           processor;
  private static WiffService<?, ?>[]     services;
  private static WiffConsumer<?>[]       consumers;
  private static Thread[]                consumerThreads;
  public static ByteBufferPool           pool;

  private static Logger                  LOGGER;
  private static Level                   log_level  = Level.INFO;

  /**
   * Initializes objects according to the application config and starts capture
   * and consumer threads.
   * 
   * @param args
   *          arguments from the command line
   */
  public static void main(String[] args) {
    // Configure log4j
    PropertyConfigurator.configure("config/log4j.properties");
    LOGGER = Logger.getLogger(Wiff.class);

    // load cli options in order to get the config file location
    processCLIOptions(args);
    if (cliOptions.containsKey("config-file")) {
      configFile = cliOptions.get("config-file");
    }

    // load properties from file
    properties = WiffProperties.createFromFile(configFile);

    // overwrite properties passed in from the command line
    properties.setProperities(cliOptions);
    LOGGER.debug("Properties:" + properties);

    if (properties.contains("log_level")) {
      log_level = Level.toLevel(properties.getString("log_level"));
    }
    Logger.getRootLogger().setLevel(log_level);

    attachShutDownHook();

    initializeCapture();
  }

  /**
   * Populates cliOptions HashMap with the options given to this program from
   * the command line
   * 
   * @param options
   *          the program arguments
   */
  private static void processCLIOptions(String[] options) {
    for (String option : options) {
      String[] tuple = option.split("\\s*=\\s*");

      // remove any dash chars from the key
      tuple[0] = tuple[0].replaceAll("^[\\s-]+", "");

      cliOptions.put(tuple[0], tuple[1]);
    }
  }

  /**
   * Initializes the queue, capture, service, and reporter objects and spawns
   * any necessary threads
   * 
   */
  private static void initializeCapture() {
    String captureSource = properties.getString("capture_source", "0");

    // See if the capture source is a processor
    boolean isProcessor = properties.contains("wiff.processor." + captureSource
        + ".type");

    if (isProcessor) {
      // Create incoming packet queue
      queue = new WiffLinkedBlockingQueue<byte[]>();

      // create processor
      processor = createProcessor(captureSource, queue);
    } else {

      // Create ByteBuffer pool
      float poolSize = properties.getFloat("pool_size", 512);
      int bufferCapacity = properties.getInt("buffer_capacity", 65535);

      Double numBuffers = Math.pow(2, 20) / bufferCapacity * poolSize;
      pool = new ByteBufferPool(numBuffers.intValue(), bufferCapacity);

      // Create incoming packet queue
      queue = new WiffLinkedBlockingQueue<WiffByteBuffer>();

      // Create capture object
      String tcpFilter = properties.getString("tcpdump_filter");
      capture = new WiffCapture(captureSource, queue, pool, tcpFilter);
    }

    // Initialize services
    String servicesNames = properties.getString("wiff.services");
    if (servicesNames != null) {
      createServices(servicesNames.split(","));
    }

    // Start capture
    if (capture != null) {
      // Start Capture (Producer) thread
      captureThread = new Thread(capture);
      captureThread.setName("CaptureThread");
      captureThread.setPriority(Thread.MAX_PRIORITY);
      captureThread.start();
    }

    // Create consumers
    int numConsumers = properties.getInt("consumer_count", 1);
    consumers = new WiffConsumer[numConsumers];
    consumerThreads = new Thread[numConsumers];

    String consumerType = properties.getString("consumer_type");
    for (int i = 0; i < numConsumers; i++) {
      WiffConsumer<?> consumer = null;
      switch (Consumers.valueOf(consumerType)) {
        case BytesConsumer:
          consumer = new BytesConsumer(queue);
          break;
        case PacketConsumer:
        default:
          consumer = new PacketConsumer(queue, pool);
          break;
      }
      consumers[i] = consumer;

      // Start consumer threads
      Thread thread = new Thread(consumer);
      thread.setName("ConsumerThread-" + i);
      consumerThreads[i] = thread;
      thread.start();
    }
  }

  /**
   * Creates a processor according to the config file
   * 
   * @param processorName
   *          the name of the processor to create
   * @param queue
   *          the queue into which this processor will place its data
   * @return a processor object
   */
  private static WiffProcessor createProcessor(String processorName,
      WiffQueue queue) {
    LOGGER.info("Creating instance of processor " + processorName + "...");

    String prefix = "wiff.processor." + processorName + ".";
    String processorType = properties.getString(prefix + "type", "");

    // Common properties
    String host = properties.getString(prefix + "host");
    int port = properties.getInt(prefix + "port");

    WiffProcessor processor = null;
    switch (Processors.valueOf(processorType)) {
      case RabbitMQProcessor:
        String user = properties.getString(prefix + "user", "guest");
        String pass = properties.getString(prefix + "pass", "guest");
        String queueName = properties.getString(prefix + "queue");

        processor = new RabbitMQProcessor(host, port, user, pass, queueName,
            queue);
        break;
      default:
        break;
    }
    return processor;
  }

  /**
   * Instantiates a set of services as defined in the configuration
   * 
   * @param serviceNames
   *          the set of names of services to create
   */
  private static void createServices(String[] serviceNames) {
    services = new WiffService[serviceNames.length];

    int index = 0;
    for (String serviceName : serviceNames) {
      String serivcePrefix = "wiff.service." + serviceName + ".";

      // Create reporter
      WiffReporter reporter = null;
      if (properties.contains(serivcePrefix + "reporter")) {
        try {
          reporter = createReporter(properties.getString(serivcePrefix
              + "reporter"));
        } catch (IOException e) {
          LOGGER.error("", e);
        }
      }

      // Initialize services
      String serviceType = properties.getString(serivcePrefix + "type");
      switch (Services.valueOf(serviceType)) {
        case WiffStitch:
          int cacheTime = properties.getInt(serivcePrefix + "cachetime", 1);
          services[index] = new WiffStitch(cacheTime, reporter);
          break;
        case ParseBytes:
          services[index] = new ParseBytes(reporter);
          break;
        case WiffRoundtrip:
          services[index] = new WiffRoundtrip(reporter);
        default:
          break;
      }
      index++;

      LOGGER.info("Service " + serviceName + " has been started.");
    }
  }

  /**
   * Instantiates a reporter as defined in the configuration
   * 
   * @param reporterName
   *          the name of the reporter to create
   */
  private static WiffReporter createReporter(String reporterName)
      throws IOException {
    LOGGER.info("Creating instance of reporter " + reporterName + "...");

    String reporterPrefix = "wiff.reporter." + reporterName + ".";

    // Common properties
    String host = properties.getString(reporterPrefix + "host");
    int port = properties.getInt(reporterPrefix + "port");
    String reporterType = properties.getString(reporterPrefix + "type");
    int reportInterval = properties.getInt(reporterPrefix + "report_interval",
        0);

    // Create parser
    Parser parser = null;
    if (properties.contains(reporterPrefix + "parser")) {
      parser = createParser(properties.getString(reporterPrefix + "parser"));
    }

    WiffReporter reporter = null;
    switch (Reporters.valueOf(reporterType)) {
      case RabbitMQClient:
        String queueName = properties.getString(reporterPrefix + "queue");
        String exchangeName = properties.getString(reporterPrefix + "exchange");
        String user = properties.getString(reporterPrefix + "user", "guest");
        String pass = properties.getString(reporterPrefix + "pass", "guest");

        reporter = new RabbitMQClient(host, port, user, pass, exchangeName,
            queueName, reportInterval, null, parser);
        break;
      case ElasticsearchClient:
        reporter = new ElasticsearchClient(host, port, reportInterval, parser);
        break;
      default:
        break;
    }
    return reporter;
  }

  /**
   * Instantiates a parser as defined in the configuration
   * 
   * @param parserName
   *          the name of the parser to create
   */
  private static Parser createParser(String parserName) {
    LOGGER.info("Creating instance of parser " + parserName + "...");

    String parserPrefix = "wiff.parser." + parserName + ".";
    String parserType = properties.getString(parserPrefix + "type", "");

    String indexName = properties
        .getString(parserPrefix + "index_name", "wiff");
    Parser parser = null;
    switch (Parsers.valueOf(parserType)) {
      case HTTPParser:
        boolean includeBody = properties.getBoolean(parserPrefix
            + "include_body", true);

        String dataSource = properties.getString(parserPrefix + "data_source");
        String inputSource = properties
            .getString(parserPrefix + "input_source");

        parser = new HTTPParser(indexName, includeBody, dataSource, inputSource);
        break;
      case PrependSize:
        parser = new PrependSize();
        break;
      case ESBulkIndex:
        parser = new ESBulkIndex(indexName);
        break;
      default:
        break;
    }
    return parser;
  }

  /**
   * Returns an array of the services that Wiff is running
   * 
   * @return an iterator over services
   */
  public static WiffService<?, ?>[] getServices() {
    return services;
  }

  public static int getSSLPort() {
    int port = 443;
    if (properties != null) {
      port = properties.getInt("ssl_port", 443);
    }
    return port;
  }

  public static String getRSAPrivateKeyFile() {
    String file = null;
    if (properties != null) {
      file = properties.getString("ssl_rsa_private_key");
      if (file != null) {
        file = file.trim();
      }
    }
    return file;
  }

  /**
   * Attaches a shutdown hook that will safely release the resources used by
   * this application
   */
  private static void attachShutDownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() {
        try {
          // stop processor
          if (processor != null) {
            LOGGER.info("Stopping processor...");
            processor.stop();
            LOGGER.info("done.");
          }

          // stop capturing packets
          if (capture != null) {
            LOGGER.info("Stopping capture...");
            capture.stop();
            LOGGER.info("done.");
          }

          // Stop consumer threads
          if (consumers != null) {
            LOGGER.info("Stopping consumers...");

            for (WiffConsumer<?> consumer : consumers) {
              consumer.stop();
            }
            for (Thread thread : consumerThreads) {
              thread.join();
            }
            LOGGER.info("done.");
          }

          // Stop the services. Each service is responsible for shutting down
          // its reporters.
          if (services != null) {
            LOGGER.info("Stopping services...");
            for (WiffService<?, ?> service : services) {
              service.stop();
            }
            LOGGER.info("done.");
          }

          // clean up the queue
          LOGGER.info("Destroying the queue...");
          queue.teardown();
          LOGGER.info("done.");
          LOGGER.info("Wiff has been shutdown.");
        } catch (InterruptedException e) {
          LOGGER.error("", e);
        }
      }
    }));
    LOGGER.info("Shutdown hook attached.");
  }
}
