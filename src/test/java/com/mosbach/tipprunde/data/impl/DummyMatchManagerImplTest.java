package com.mosbach.tipprunde.data.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;

// Prüft die Testspiele, ohne Spring und ohne Datenbank
class DummyMatchManagerImplTest {

    private final DummyMatchManagerImpl manager = new DummyMatchManagerImpl();

    @Test
    void currentMatchdayHasSixMatches() {
        int matchday = manager.getCurrentMatchday();
        assertEquals(6, manager.getMatchesByMatchday(matchday).size());
    }

    @Test
    void otherMatchdayIsEmpty() {
        assertTrue(manager.getMatchesByMatchday(1).isEmpty());
    }

    @Test
    void statusMixIsComplete() {
        List<Match> matches = manager.getMatchesByMatchday(manager.getCurrentMatchday());
        assertEquals(2, count(matches, MatchStatus.FINISHED));
        assertEquals(1, count(matches, MatchStatus.LIVE));
        assertEquals(2, count(matches, MatchStatus.UPCOMING));
        assertEquals(1, count(matches, MatchStatus.CANCELLED));
    }

    @Test
    void finishedMatchesHaveResultAndLieInThePast() {
        Instant now = Instant.now();
        manager.getMatchesByMatchday(manager.getCurrentMatchday()).stream()
                .filter(match -> match.status() == MatchStatus.FINISHED)
                .forEach(match -> {
                    assertTrue(match.hasResult());
                    assertTrue(match.kickoffUtc().isBefore(now));
                });
    }

    @Test
    void upcomingMatchesHaveNoResultAndLieInTheFuture() {
        Instant now = Instant.now();
        manager.getMatchesByMatchday(manager.getCurrentMatchday()).stream()
                .filter(match -> match.status() == MatchStatus.UPCOMING)
                .forEach(match -> {
                    assertFalse(match.hasResult());
                    assertTrue(match.kickoffUtc().isAfter(now));
                });
    }

    @Test
    void matchCanBeFoundById() {
        assertEquals("FC Bayern München", manager.getMatchById(1).orElseThrow().homeTeam());
        assertTrue(manager.getMatchById(99).isEmpty());
    }

    private static long count(List<Match> matches, MatchStatus status) {
        return matches.stream().filter(match -> match.status() == status).count();
    }
}