package com.mosbach.tipprunde.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.MatchSyncManager;
import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Liest MATCH_UPDATED aus der Queue und übernimmt das Spiel idempotent; aktiv nur mit QUEUE_URL und Postgres
@Component
@ConditionalOnExpression("!'${spring.rabbitmq.addresses:}'.isEmpty() and '${app.match.manager:postgres}' == 'postgres'")
public class MatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(MatchEventListener.class);

    private final MatchSyncManager syncManager;
    private final JsonMapper jsonMapper;

    public MatchEventListener(MatchSyncManager syncManager, JsonMapper jsonMapper) {
        this.syncManager = syncManager;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = "${app.queue.name}")
    public void onMessage(Message message) {
        handle(message.getBody());
    }

    // Eigene Methode ohne AMQP-Typen, damit sie ohne Queue testbar ist
    public UpsertResult handle(byte[] body) {
        MatchUpdate update;
        try {
            update = MatchEventMapper.toUpdate(jsonMapper.readValue(body, MatchEvent.class));
        } catch (JacksonException | IllegalArgumentException e) {
            // Ungültige Nachricht verwerfen statt endlos neu zustellen
            log.warn("Match event rejected: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Invalid match event", e);
        }
        UpsertResult result = syncManager.upsert(update);
        log.info("Match event processed: externalId={} status={} outcome={} matchId={} resultChanged={}",
                update.externalId(), update.status(), result.outcome(), result.matchId(), result.resultChanged());
        return result;
    }
}
