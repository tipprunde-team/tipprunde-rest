package com.mosbach.tipprunde.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mosbach.tipprunde.data.api.MatchManager;
import com.mosbach.tipprunde.data.api.TipManager;
import com.mosbach.tipprunde.domain.TipRules;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchEntry;
import com.mosbach.tipprunde.model.match.MatchdayResponse;
import com.mosbach.tipprunde.model.tip.Tip;
import com.mosbach.tipprunde.security.CurrentUserId;
import com.mosbach.tipprunde.security.MembershipCheck;

// Matchday of a group with lock state and own tip (US-17, UC-01)
@RestController
@RequestMapping("/api/groups/{groupId}/matches")
public class MatchController {

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);

    private static final int FIRST_MATCHDAY = 1;
    private static final int LAST_MATCHDAY = 34;

    private final MembershipCheck membershipCheck;
    private final MatchManager matchManager;
    private final TipManager tipManager;
    private final Clock clock;

    public MatchController(MembershipCheck membershipCheck, MatchManager matchManager, TipManager tipManager,
            Clock clock) {
        this.membershipCheck = membershipCheck;
        this.matchManager = matchManager;
        this.tipManager = tipManager;
        this.clock = clock;
    }

    // Without ?matchday the current matchday is returned
    @GetMapping
    public MatchdayResponse getMatchday(@PathVariable("groupId") long groupId,
            @RequestParam(name = "matchday", required = false) Integer matchday,
            @CurrentUserId long userId) {
        log.info("Matchday requested: groupId={}, matchday={}, userId={}", groupId, matchday, userId);

        // Order as in the API reference: token (interceptor) > membership > input
        membershipCheck.requireMember(groupId, userId);
        int selectedMatchday = matchday != null ? validMatchday(matchday) : matchManager.getCurrentMatchday();

        // Lock state depends on server time only, never on the client clock
        Instant now = Instant.now(clock);
        List<Match> matches = matchManager.getMatchesByMatchday(selectedMatchday);
        Map<Long, Tip> ownTips = tipManager.getOwnTipsForMatchday(groupId, userId, selectedMatchday).stream()
                .collect(Collectors.toMap(Tip::matchId, Function.identity()));

        List<MatchEntry> entries = matches.stream()
                .map(match -> MatchEntry.of(match, ownTips.get(match.id()), TipRules.isLocked(match, now)))
                .toList();
        log.info("Matchday {} delivered with {} matches", selectedMatchday, entries.size());
        return new MatchdayResponse(selectedMatchday, now.truncatedTo(ChronoUnit.MILLIS), entries);
    }

    private static int validMatchday(int matchday) {
        if (matchday < FIRST_MATCHDAY || matchday > LAST_MATCHDAY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Der Spieltag muss zwischen 1 und 34 liegen.");
        }
        return matchday;
    }
}
