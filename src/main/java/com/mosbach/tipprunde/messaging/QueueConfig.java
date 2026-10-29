package com.mosbach.tipprunde.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Legt die Queue dauerhaft an, falls es sie noch nicht gibt (der Match-Service macht dasselbe)
@Configuration
@ConditionalOnExpression("!'${spring.rabbitmq.addresses:}'.isEmpty()")
public class QueueConfig {

    @Bean
    public Queue matchEventsQueue(@Value("${app.queue.name}") String name) {
        return new Queue(name, true);
    }
}
