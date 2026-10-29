package com.mosbach.tipprunde.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult;
import com.mosbach.tipprunde.model.match.UpsertResult.Outcome;

import tools.jackson.databind.json.JsonMapper;

// Events genau im Format des Match-Service (Vertrag D.2) ohne Queue und ohne Datenbank verarbeiten
class MatchEventListenerTest {

    private final List<MatchUpdate> saved = new ArrayList<>();
    private final MatchEventListener listener = new MatchEventListener(update -> {
        saved.add(update);
        return new UpsertResult(Outcome.INSERTED, 42L, update.status() == MatchStatus.FINISHED);
    }, JsonMapper.builder().build());

    private static byte[] event(String file) throws IOException {
        try (InputStream in = MatchEventListenerTest.class.getResourceAsStream("/events/" + file)) {
            return in.readAllBytes();
        }
    }

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void finishedEventIsSavedWithResult() throws IOException {
        UpsertResult result = listener.handle(event("match-updated-finished.json"));

        MatchUpdate update = saved.get(0);
        assertEquals(565833, update.externalId());
        assertEquals(7, update.matchday());
        assertEquals(Instant.parse("2026-10-23T18:30:00Z"), update.kickoffUtc());
        assertEquals(MatchStatus.FINISHED, update.status());
        assertEquals("VfB Stuttgart", update.homeTeam());
        assertEquals("M'gladbach", update.awayTeamShort());
        assertEquals("https://crests.football-data.org/18.png", update.awayCrest());
        assertEquals(2, update.homeGoals());
        assertEquals(3, update.awayGoals());
        assertEquals(42L, result.matchId());
    }

    @Test
    void upcomingEventIsSavedWithoutResult() throws IOException {
        listener.handle(event("match-updated-upcoming.json"));

        MatchUpdate update = saved.get(0);
        assertEquals(MatchStatus.UPCOMING, update.status());
        assertNull(update.homeGoals());
        assertNull(update.awayGoals());
    }

    @Test
    void scoreOfAnUnfinishedMatchIsIgnored() {
        listener.handle(json("{\"type\":\"MATCH_UPDATED\",\"externalId\":1,\"matchday\":8,"
                + "\"kickoffUtc\":\"2026-10-30T19:30:00Z\",\"status\":\"LIVE\",\"homeTeam\":{\"name\":\"A\"},"
                + "\"awayTeam\":{\"name\":\"B\"},\"score\":{\"home\":1,\"away\":0}}"));
        assertNull(saved.get(0).homeGoals());
    }

    @Test
    void brokenOrIncompleteEventsAreRejectedWithoutRequeue() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.handle(json("not json")));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.handle(json("{\"type\":\"OTHER\"}")));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.handle(json(
                "{\"type\":\"MATCH_UPDATED\",\"externalId\":1,\"matchday\":8,\"status\":\"UPCOMING\"}")));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.handle(json(
                "{\"type\":\"MATCH_UPDATED\",\"externalId\":1,\"matchday\":8,\"kickoffUtc\":\"2026-10-30T19:30:00Z\","
                        + "\"status\":\"WHATEVER\",\"homeTeam\":{\"name\":\"A\"},\"awayTeam\":{\"name\":\"B\"}}")));
        assertEquals(0, saved.size());
    }
}
