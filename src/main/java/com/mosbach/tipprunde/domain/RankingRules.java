package com.mosbach.tipprunde.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mosbach.tipprunde.model.ranking.MemberScore;
import com.mosbach.tipprunde.model.ranking.RankingEntry;

// Rangregel der Rangliste ohne Datenbank: gleiche Punkte bekommen gleichen Rang, danach wird übersprungen (1, 2, 2, 4)
public final class RankingRules {

    // Volle Punktzahl nach dem Schema 4/3/2/0 gilt als exakter Tipp
    public static final int EXACT_TIP_POINTS = 4;

    private RankingRules() {
    }

    // Absteigend nach Punkten, bei gleichen Punkten alphabetisch nach Benutzername, dann nach ID (stabile Reihenfolge)
    public static List<RankingEntry> rank(List<MemberScore> scores) {
        List<MemberScore> sorted = scores.stream()
                .sorted(Comparator.comparingInt(MemberScore::points).reversed()
                        .thenComparing(score -> score.username().toLowerCase(Locale.ROOT))
                        .thenComparingLong(MemberScore::userId))
                .toList();
        List<RankingEntry> ranking = new ArrayList<>(sorted.size());
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            MemberScore score = sorted.get(i);
            // Neuer Rang nur, wenn die Punkte kleiner sind als beim Vorgänger, sonst Rang des Vorgängers
            if (i == 0 || score.points() != sorted.get(i - 1).points()) {
                rank = i + 1;
            }
            ranking.add(new RankingEntry(rank, score.userId(), score.username(), score.points(), score.exactTips()));
        }
        return ranking;
    }
}
