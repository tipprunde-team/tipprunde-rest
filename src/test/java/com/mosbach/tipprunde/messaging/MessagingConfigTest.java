package com.mosbach.tipprunde.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.mosbach.tipprunde.data.api.MatchSyncManager;
import com.mosbach.tipprunde.model.match.UpsertResult;
import com.mosbach.tipprunde.model.match.UpsertResult.Outcome;

// Verdrahtung ohne echte Queue: Empfang nur mit QUEUE_URL und Postgres, amqps-URL wird mit TLS gelesen
class MessagingConfigTest {

    // Testwert ohne echtes Passwort, Port 1 ist absichtlich nicht erreichbar
    private static final String FAKE_URL = "amqps://testuser:testpass@localhost:1/testvhost";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class))
            .withUserConfiguration(QueueConfig.class, MatchEventListener.class)
            .withBean(MatchSyncManager.class, () -> update -> new UpsertResult(Outcome.UNCHANGED, 1L, false))
            .withPropertyValues("app.queue.name=tipprunde.match-events",
                    "spring.rabbitmq.listener.simple.auto-startup=false");

    @Test
    void withoutQueueUrlNothingIsActive() {
        runner.withPropertyValues("spring.rabbitmq.addresses=", "app.match.manager=postgres").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MatchEventListener.class);
            assertThat(context).doesNotHaveBean(Queue.class);
        });
    }

    @Test
    void withQueueUrlListenerAndDurableQueueExist() {
        runner.withPropertyValues("spring.rabbitmq.addresses=" + FAKE_URL, "app.match.manager=postgres").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MatchEventListener.class);
            Queue queue = context.getBean(Queue.class);
            assertThat(queue.getName()).isEqualTo("tipprunde.match-events");
            assertThat(queue.isDurable()).isTrue();
        });
    }

    @Test
    void amqpsUrlUsesTlsUserAndVhost() {
        runner.withPropertyValues("spring.rabbitmq.addresses=" + FAKE_URL, "app.match.manager=postgres").run(context -> {
            com.rabbitmq.client.ConnectionFactory factory =
                    context.getBean(CachingConnectionFactory.class).getRabbitConnectionFactory();
            assertThat(factory.isSSL()).isTrue();
            assertThat(factory.getUsername()).isEqualTo("testuser");
            assertThat(factory.getVirtualHost()).isEqualTo("testvhost");
        });
    }

    @Test
    void dummyModeStartsWithoutListener() {
        runner.withPropertyValues("spring.rabbitmq.addresses=" + FAKE_URL, "app.match.manager=dummy").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MatchEventListener.class);
        });
    }
}
