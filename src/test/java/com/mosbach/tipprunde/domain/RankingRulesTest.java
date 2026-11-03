package com.mosbach.tipprunde.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mosbach.tipprunde.model.ranking.MemberScore;
import com.mosbach.tipprunde.model.ranking.RankingEntry;

// KI-Log MA-20 "Rangliste im Format des API-Designs mit Tests, passend zu web #52."
class RankingRulesTest {

    private static MemberScore score(long id, String name, int points) {
        return new MemberScore(id, name, points, 0);
    }

    private static List<Integer> ranks(List<RankingEntry> ranking) {
        return ranking.stream().map(RankingEntry::rank).toList();
    }

    private static List<String> names(List<RankingEntry> ranking) {
        return ranking.stream().map(RankingEntry::username).toList();
    }

    @Test
    void tiedMembersShareARankAndTheNextRankIsSkipped() {
        // Beispiel aus der API-Referenz: 1, 2, 2, 4
        List<RankingEntry> ranking = RankingRules.rank(List.of(
                score(1, "arlind", 27), score(2, "max", 31), score(3, "niklas", 31), score(4, "maximilian", 35)));

        assertEquals(List.of(1, 2, 2, 4), ranks(ranking));
        assertEquals(List.of("maximilian", "max", "niklas", "arlind"), names(ranking));
    }

    @Test
    void threeWayTieGivesRanksOneOneOneFour() {
        List<RankingEntry> ranking = RankingRules.rank(List.of(
                score(1, "a", 5), score(2, "b", 5), score(3, "c", 5), score(4, "d", 2)));

        assertEquals(List.of(1, 1, 1, 4), ranks(ranking));
    }

    @Test
    void membersWithoutPointsAllShareRankOne() {
        List<RankingEntry> ranking = RankingRules.rank(List.of(score(1, "a", 0), score(2, "b", 0)));

        assertEquals(List.of(1, 1), ranks(ranking));
    }

    @Test
    void membersWithZeroPointsComeAfterMembersWithPoints() {
        List<RankingEntry> ranking = RankingRules.rank(List.of(score(1, "a", 0), score(2, "b", 3)));

        assertEquals(List.of("b", "a"), names(ranking));
        assertEquals(List.of(1, 2), ranks(ranking));
    }

    @Test
    void sameRankIsSortedAlphabeticallyIgnoringCase() {
        List<RankingEntry> ranking = RankingRules.rank(List.of(score(1, "Zoe", 4), score(2, "anna", 4), score(3, "Bernd", 4)));

        assertEquals(List.of("anna", "Bernd", "Zoe"), names(ranking));
    }

    @Test
    void sameNameAndPointsFallBackToUserId() {
        List<RankingEntry> ranking = RankingRules.rank(List.of(score(9, "max", 4), score(2, "max", 4)));

        assertEquals(List.of(2L, 9L), ranking.stream().map(RankingEntry::userId).toList());
    }

    @Test
    void emptyGroupGivesEmptyRanking() {
        assertTrue(RankingRules.rank(List.of()).isEmpty());
    }

    @Test
    void singleMemberIsRankOne() {
        assertEquals(List.of(1), ranks(RankingRules.rank(List.of(score(1, "a", 7)))));
    }

    @Test
    void pointsAndExactTipsAreKept() {
        RankingEntry entry = RankingRules.rank(List.of(new MemberScore(7, "niklas", 31, 3))).get(0);

        assertEquals(new RankingEntry(1, 7, "niklas", 31, 3), entry);
    }

    @Test
    void exactTipCountsFourPoints() {
        assertEquals(4, RankingRules.EXACT_TIP_POINTS);
    }
}
