package org.ktlib.queue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import java.util.*

data class Message(val value: String)

private fun createQueue(): String {
    val queueName = UUID.randomUUID().toString()
    RabbitMQ.ensureQueueExists(queueName)
    return queueName
}

class RabbitMQTests : StringSpec({
    "connected" {
        RabbitMQ.connected shouldBe true
    }

    "publish message" {
        val queueName = createQueue()

        RabbitMQ.publish(queueName, Message("myValue"))
        delay(1000)
        val count = RabbitMQ.messageCount(queueName)
        RabbitMQ.removeQueue(queueName, onlyIfEmpty = false, onlyIfUnused = false)

        count shouldBe 1
    }

    "create listener" {
        val queueName = createQueue()
        var messageReceived: Message? = null
        val listener = RabbitMQ.listen(queueName) { message: Message ->
            messageReceived = message
            Queue.HandlerResult.Ack
        }

        RabbitMQ.publish(queueName, Message("myValue"))

        delay(1000)
        RabbitMQ.stopListening(listener)
        RabbitMQ.removeQueue(queueName, onlyIfEmpty = false, onlyIfUnused = false)

        messageReceived?.value shouldBe "myValue"
    }
})