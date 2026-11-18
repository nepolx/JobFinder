package ru.vacancy.notifier.mq;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

/** Тонкий адаптер Spring AMQP: всё решение принимает {@link EventMessageProcessor}. */
@Component
public class EventListener {

    private final EventMessageProcessor processor;

    public EventListener(EventMessageProcessor processor) {
        this.processor = processor;
    }

    // Ручное подтверждение включено в application.yml (acknowledge-mode: manual).
    @RabbitListener(queues = "${notifier.events-queue:vacancy.events}")
    public void onMessage(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        EventMessageProcessor.Decision decision =
                processor.process(message.getBody(), message.getMessageProperties().getHeaders());
        if (decision == EventMessageProcessor.Decision.ACK) {
            channel.basicAck(tag, false);
        } else {
            channel.basicNack(tag, false, false); // requeue=false -> dead-letter -> retry-очередь
        }
    }
}
