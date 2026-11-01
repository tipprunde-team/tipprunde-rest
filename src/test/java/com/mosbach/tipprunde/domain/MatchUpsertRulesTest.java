package com.mosbach.tipprunde.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult.Outcome;

// Idempotente Übernahme: neu, gleich, geändert, manuell korrigiert; Auslöser für die Punkte
class MatchUpsertRulesTest {

    private static final Instant KICKOFF = Instant.parse("2026-10-23T18:30:00Z");

    private static MatchUpdate update(MatchStatus status, Integer home, Integer away) {
        return new MatchUpdate(565833, 7, KICKOFF, status, "VfB Stuttgart", "Stuttgart", "c10",
                "Borussia Mönchengladbach", "M'gladbach", "c18", home, away);
    }

    private static Match existing(MatchStatus status, Integer home, Integer away, boolean manual) {
        return new Match(11L, 565833L, 7, KICKOFF, status, "VfB Stuttgart", "Stuttgart", "c10",
                "Borussia Mönchengladbach", "M'gladbach", "c18", home, away, manual, KICKOFF);
    }

    @Test
    void unknownMatchIsInserted() {
        assertEquals(Outcome.INSERTED, MatchUpsertRules.decide(null, update(MatchStatus.UPCOMING, null, null)));
    }

    @Test
    void sameStateIsUnchangedSoADuplicateEventChangesNothing() {
        assertEquals(Outcome.UNCHANGED, MatchUpsertRules.decide(existing(MatchStatus.FINISHED, 2, 3, false),
                update(MatchStatus.FINISHED, 2, 3)));
    }

    @Test
    void newStatusOrResultIsAnUpdate() {
        assertEquals(Outcome.UPDATED, MatchUpsertRules.decide(existing(MatchStatus.UPCOMING, null, null, false),
                update(MatchStatus.FINISHED, 2, 3)));
        assertEquals(Outcome.UPDATED, MatchUpsertRules.decide(existing(MatchStatus.FINISHED, 2, 2, false),
                update(MatchStatus.FINISHED, 2, 3)));
    }

    @Test
    void manuallyCorrectedMatchIsNeverOverwritten() {
        assertEquals(Outcome.MANUALLY_CORRECTED, MatchUpsertRules.decide(existing(MatchStatus.FINISHED, 1, 1, true),
                update(MatchStatus.FINISHED, 2, 3)));
    }

    @Test
    void resultChangesWhenAMatchFinishesOrItsResultIsCorrected() {
        assertTrue(MatchUpsertRules.resultChanged(existing(MatchStatus.LIVE, null, null, false),
                update(MatchStatus.FINISHED, 2, 3)));
        assertTrue(MatchUpsertRules.resultChanged(existing(MatchStatus.FINISHED, 2, 2, false),
                update(MatchStatus.FINISHED, 2, 3)));
        assertTrue(MatchUpsertRules.resultChanged(null, update(MatchStatus.FINISHED, 0, 0)));
    }

    @Test
    void noResultChangeForSameResultOrUnfinishedMatch() {
        assertFalse(MatchUpsertRules.resultChanged(existing(MatchStatus.FINISHED, 2, 3, false),
                update(MatchStatus.FINISHED, 2, 3)));
        assertFalse(MatchUpsertRules.resultChanged(existing(MatchStatus.UPCOMING, null, null, false),
                update(MatchStatus.LIVE, null, null)));
        assertFalse(MatchUpsertRules.resultChanged(null, update(MatchStatus.CANCELLED, null, null)));
    }
}
