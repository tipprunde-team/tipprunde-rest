package com.mosbach.tipprunde.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mosbach.tipprunde.data.api.RankingManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.ranking.RankingResponse;
import com.mosbach.tipprunde.security.CurrentUserId;
import com.mosbach.tipprunde.security.GroupAccess;

// KI-Log MA-20 "Rangliste im Format des API-Designs mit Tests, passend zu web #52."
// Rangliste einer Gruppe: gesamt oder je Spieltag (US-25, US-26), nur für Mitglieder
@RestController
@RequestMapping("/api/groups/{groupId}/ranking")
public class RankingController {

    private static final Logger log = LoggerFactory.getLogger(RankingController.class);

    private static final int FIRST_MATCHDAY = 1;
    private static final int LAST_MATCHDAY = 34;

    private final GroupAccess groupAccess;
    private final RankingManager rankingManager;

    public RankingController(GroupAccess groupAccess, RankingManager rankingManager) {
        this.groupAccess = groupAccess;
        this.rankingManager = rankingManager;
    }

    // Ohne ?matchday kommt die Gesamtrangliste, matchday ist dann null
    @GetMapping
    public RankingResponse ranking(@PathVariable("groupId") long groupId,
            @RequestParam(name = "matchday", required = false) Integer matchday,
            @CurrentUserId long userId) {
        log.info("Ranking requested: groupId={}, matchday={}, userId={}", groupId, matchday, userId);
        // Reihenfolge wie in der API-Referenz: Token (Interceptor) > Mitgliedschaft > Eingabe
        groupAccess.requireMember(groupId, userId);
        Integer selected = matchday == null ? null : validMatchday(matchday);
        return new RankingResponse(groupId, selected, rankingManager.getRanking(groupId, selected));
    }

    private static int validMatchday(int matchday) {
        if (matchday < FIRST_MATCHDAY || matchday > LAST_MATCHDAY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Der Spieltag muss zwischen 1 und 34 liegen.");
        }
        return matchday;
    }
}
