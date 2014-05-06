package org.wayfair.wiff.processor;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.wayfair.wiff.queue.WiffQueue;
import org.wayfair.wiff.queue.WiffQueue.WiffQueueAdditionException;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * Consumes data from RabbitMQ as a data source for processing in WIFF
 * 
 */
public class RabbitMQProcessor extends WiffProcessor {
  private Connection connection;
  private Channel    channel;

  /**
   * Creates a processor that consumes messages in RabbitMQ and places them onto
   * the given queue
   * 
   * @param host
   *          the RabbitMQ host
   * @param port
   *          the RabbitMQ port
   * @param username
   *          the RabbitMQ username
   * @param password
   *          the RabbitMQ user's password
   * @param rabbitQueueName
   *          the name of the queue in RabbitMQ to receive data from
   * @param queue
   *          the queue onto which data will be stored by this processor
   */
  public RabbitMQProcessor(String host, int port, String username,
      String password, String rabbitQueueName, WiffQueue<byte[]> queue) {
    super(queue);

    // connect to rabbitmq
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUsername(username);
    factory.setPassword(password);

    try {
      connection = factory.newConnection();
      channel = connection.createChannel();

      // create a consumer. rabbit will push messages to this consumer
      channel.basicConsume(rabbitQueueName, true, new RabbitComsumer(queue));
    } catch (IOException e) {
      LOGGER.error("", e);
    }
  }

  public void stop() {
    try {
      channel.close();
      connection.close();
    } catch (IOException e) {
      LOGGER.error("", e);
    }
  }
}

class RabbitComsumer implements Consumer {
  private WiffQueue<byte[]> queue;
  private final Logger      LOGGER = Logger.getLogger(this.getClass());

  /**
   * Creates a RabbitMQ consumer
   * 
   * @param queue
   *          the queue into which consumed messages will be inserted
   */
  public RabbitComsumer(WiffQueue<byte[]> queue) {
    this.queue = queue;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.rabbitmq.client.Consumer#handleDelivery(java.lang.String,
   * com.rabbitmq.client.Envelope, com.rabbitmq.client.AMQP.BasicProperties,
   * byte[])
   */
  @Override
  public void handleDelivery(String consumerTag, Envelope envelope,
      BasicProperties properties, byte[] body) throws IOException {
    try {
      queue.add(body);
    } catch (WiffQueueAdditionException e) {
      LOGGER.error("", e);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.rabbitmq.client.Consumer#handleConsumeOk(java.lang.String)
   */
  @Override
  public void handleConsumeOk(String consumerTag) {
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.rabbitmq.client.Consumer#handleCancelOk(java.lang.String)
   */
  @Override
  public void handleCancelOk(String consumerTag) {
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.rabbitmq.client.Consumer#handleCancel(java.lang.String)
   */
  @Override
  public void handleCancel(String consumerTag) throws IOException {
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.rabbitmq.client.Consumer#handleShutdownSignal(java.lang.String,
   * com.rabbitmq.client.ShutdownSignalException)
   */
  @Override
  public void handleShutdownSignal(String consumerTag,
      ShutdownSignalException sig) {
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.rabbitmq.client.Consumer#handleRecoverOk(java.lang.String)
   */
  @Override
  public void handleRecoverOk(String consumerTag) {
  }
}
