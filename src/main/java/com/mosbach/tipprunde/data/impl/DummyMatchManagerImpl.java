package com.mosbach.tipprunde.data.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.MatchManager;
import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;

// Sechs Testspiele relativ zur aktuellen Zeit, aktiv bei app.match.manager=dummy
@Component
@ConditionalOnProperty(name = "app.match.manager", havingValue = "dummy", matchIfMissing = true)
public class DummyMatchManagerImpl implements MatchManager {

    private static final int CURRENT_MATCHDAY = 7;
    private static final String CREST_URL = "https://crests.football-data.org/%d.png";

    @Override
    public List<Match> getMatchesByMatchday(int matchday) {
        return matchday == CURRENT_MATCHDAY ? buildMatches() : List.of();
    }

    @Override
    public Optional<Match> getMatchById(long id) {
        return buildMatches().stream().filter(match -> match.id() == id).findFirst();
    }

    @Override
    public int getCurrentMatchday() {
        return CURRENT_MATCHDAY;
    }

    // Bei jedem Aufruf neu, damit die Zeiten relativ zu jetzt bleiben
    private List<Match> buildMatches() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        return List.of(
                match(1, now.minus(Duration.ofDays(2)), MatchStatus.FINISHED,
                        "FC Bayern München", "Bayern", 5, "Borussia Dortmund", "Dortmund", 4, 3, 1),
                match(2, now.minus(Duration.ofDays(1)), MatchStatus.FINISHED,
                        "Bayer 04 Leverkusen", "Leverkusen", 3, "RB Leipzig", "RB Leipzig", 721, 2, 2),
                match(3, now.minus(Duration.ofMinutes(45)), MatchStatus.LIVE,
                        "VfB Stuttgart", "Stuttgart", 10, "Eintracht Frankfurt", "Frankfurt", 19, 1, 0),
                match(4, now.plus(Duration.ofDays(1)), MatchStatus.UPCOMING,
                        "SV Werder Bremen", "Bremen", 12, "SC Freiburg", "Freiburg", 17, null, null),
                match(5, now.plus(Duration.ofDays(2)), MatchStatus.UPCOMING,
                        "Hamburger SV", "HSV", 7, "1. FC Köln", "1. FC Köln", 1, null, null),
                match(6, now.plus(Duration.ofDays(3)), MatchStatus.CANCELLED,
                        "Borussia Mönchengladbach", "M'gladbach", 18, "1. FSV Mainz 05", "Mainz", 15, null, null));
    }

    private static Match match(long id, Instant kickoff, MatchStatus status,
            String home, String homeShort, int homeTeamId,
            String away, String awayShort, int awayTeamId,
            Integer homeGoals, Integer awayGoals) {
        return new Match(id, 900000L + id, CURRENT_MATCHDAY, kickoff, status,
                home, homeShort, String.format(CREST_URL, homeTeamId),
                away, awayShort, String.format(CREST_URL, awayTeamId),
                homeGoals, awayGoals, false, kickoff);
    }
}