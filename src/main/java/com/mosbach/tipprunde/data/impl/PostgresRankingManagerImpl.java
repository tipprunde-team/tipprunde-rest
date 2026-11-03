package com.mosbach.tipprunde.data.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.RankingManager;
import com.mosbach.tipprunde.domain.RankingRules;
import com.mosbach.tipprunde.model.ranking.MemberScore;
import com.mosbach.tipprunde.model.ranking.RankingEntry;

// KI-Log MA-20 "Rangliste im Format des API-Designs mit Tests, passend zu web #52."
// Punktesummen aus den Tabellen group_members, users und tips, die Rangvergabe macht RankingRules
@Component
public class PostgresRankingManagerImpl implements RankingManager {

    // Alle Mitglieder zählen mit, auch ohne Tipps (LEFT JOIN, fehlende Punkte zählen als 0)
    private static final String SELECT_SCORES_START = """
            SELECT u.id AS user_id, u.username,
                   COALESCE(SUM(t.points), 0) AS pts,
                   COUNT(*) FILTER (WHERE t.points = ?) AS exact_tips
            FROM group_members gm
            JOIN users u ON u.id = gm.user_id
            LEFT JOIN tips t ON t.group_id = gm.group_id AND t.user_id = gm.user_id
            """;
    private static final String MATCHDAY_FILTER =
            "AND t.match_id IN (SELECT id FROM matches WHERE matchday = ?) ";
    private static final String SELECT_SCORES_END = "WHERE gm.group_id = ? GROUP BY u.id, u.username";

    private final DataSource dataSource;

    public PostgresRankingManagerImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<RankingEntry> getRanking(long groupId, Integer matchday) {
        // Der Spieltag steht nur als Parameter im SQL, nie als Text
        String sql = SELECT_SCORES_START + (matchday != null ? MATCHDAY_FILTER : "") + SELECT_SCORES_END;
        try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int index = 1;
            ps.setInt(index++, RankingRules.EXACT_TIP_POINTS);
            if (matchday != null) {
                ps.setInt(index++, matchday);
            }
            ps.setLong(index, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<MemberScore> scores = new ArrayList<>();
                while (rs.next()) {
                    scores.add(new MemberScore(rs.getLong("user_id"), rs.getString("username"),
                            rs.getInt("pts"), rs.getInt("exact_tips")));
                }
                return RankingRules.rank(scores);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankfehler beim Lesen der Rangliste", e);
        }
    }
}
