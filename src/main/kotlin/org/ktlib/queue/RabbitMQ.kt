package org.ktlib.queue

import com.rabbitmq.client.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ktlib.Json
import org.ktlib.TypeRef
import org.ktlib.config
import org.ktlib.error.ErrorReporter
import org.ktlib.lazyConfig
import java.io.IOException

/**
 * Implementation of Queue interface for RabbitMQ
 */
object RabbitMQ : Queue {
    private val logger = KotlinLogging.logger {}
    private val requeueOnHandlerError by lazyConfig("queue.rabbitmq.requeueOnError", false)

    private val factory: ConnectionFactory by lazy {
        ConnectionFactory().apply {
            username = config("queue.rabbitmq.username")
            password = config("queue.rabbitmq.password")
            host = config("queue.rabbitmq.host")
            port = config("queue.rabbitmq.port")
            if (config("queue.rabbitmq.useSsl", false)) useSslProtocol()
        }
    }

    private val pubConn: Connection by lazy { factory.newConnection() }
    private val recConn: Connection by lazy { factory.newConnection() }

    private val pubChannelHolder = ThreadLocal<Channel>()
    private val recChannelHolder = ThreadLocal<Channel>()

    private val pubChannel: Channel
        get() = getChannel(pubChannelHolder, pubConn)

    private val recChannel: Channel
        get() = getChannel(recChannelHolder, recConn)

    private fun getChannel(holder: ThreadLocal<Channel>, conn: Connection): Channel {
        val channel = holder.get()
        return if (channel != null && channel.isOpen) {
            channel
        } else {
            conn.createChannel().apply { holder.set(this) }
        }
    }

    override val connected: Boolean
        get() = try {
            pubConn.isOpen
        } catch (t: Throwable) {
            false
        }

    override fun createQueue(queueName: String, durable: Boolean, exclusive: Boolean, autoDelete: Boolean) {
        pubChannel.queueDeclare(queueName, durable, exclusive, autoDelete, null)
    }

    override fun exists(queueName: String) = try {
        pubChannel.queueDeclarePassive(queueName)
        true
    } catch (e: IOException) {
        false
    }

    override fun <T : Any> listen(
        queueName: String,
        handler: (T) -> Queue.HandlerResult,
        messageClass: TypeRef<T>
    ): String {
        logger.info { "Adding listener to queue $queueName" }
        recChannel.basicQos(1)
        return recChannel.basicConsume(queueName, false, createConsumer(queueName, handler, messageClass))
    }

    private fun <T : Any> createConsumer(
        queueName: String,
        handler: (T) -> Queue.HandlerResult,
        messageClass: TypeRef<T>
    ) =
        object : DefaultConsumer(recChannel) {
            override fun handleDelivery(
                consumerTag: String,
                envelope: Envelope,
                properties: AMQP.BasicProperties,
                body: ByteArray
            ) {
                try {
                    logger.debug { "Message received on $queueName: ${String(body)}" }
                    when (handler.invoke(Json.deserialize(body, messageClass))) {
                        Queue.HandlerResult.Ack -> channel.basicAck(envelope.deliveryTag, false)
                        Queue.HandlerResult.NackRequeue -> channel.basicNack(envelope.deliveryTag, false, true)
                        Queue.HandlerResult.NackNoRequeue -> channel.basicNack(envelope.deliveryTag, false, false)
                    }
                } catch (t: Throwable) {
                    logger.error { "Error handling queue message ${envelope.deliveryTag}" }
                    ErrorReporter.report(t)
                    channel.basicNack(envelope.deliveryTag, false, requeueOnHandlerError)
                }
            }
        }

    override fun messageCount(queueName: String) = pubChannel.messageCount(queueName)

    override fun publish(queueName: String, data: ByteArray) {
        logger.debug { "Message published to $queueName: ${String(data)}" }
        pubChannel.basicPublish("", queueName, null, data);
    }

    override fun removeQueue(queueName: String, onlyIfEmpty: Boolean, onlyIfUnused: Boolean) {
        pubChannel.queueDelete(queueName, onlyIfUnused, onlyIfEmpty)
    }

    override fun stopListening(consumerTag: String) {
        recChannel.basicCancel(consumerTag)
    }
}