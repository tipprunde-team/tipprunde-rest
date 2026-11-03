package com.mosbach.tipprunde.data.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.MatchSyncManager;
import com.mosbach.tipprunde.domain.MatchUpsertRules;
import com.mosbach.tipprunde.domain.MatchUpsertRules.Scoring;
import com.mosbach.tipprunde.domain.PointsCalculator;
import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult;
import com.mosbach.tipprunde.model.match.UpsertResult.Outcome;

// KI-Log AH-15, AH-16
// Übernimmt Spiele aus der Queue in die Tabelle matches; Schlüssel ist external_id (wie das Seed-Skript).
// In derselben Transaktion werden die Punkte der Tipps gesetzt (US-24), Spiel und Punkte passen also immer zusammen.
@Component
@ConditionalOnProperty(name = "app.match.manager", havingValue = "postgres")
public class PostgresMatchSyncManagerImpl implements MatchSyncManager {

    // Zeile sperren, damit zwei gleichzeitige Events dasselbe Spiel nicht doppelt anlegen
    private static final String SELECT_FOR_UPDATE =
            "SELECT " + PostgresMatchManagerImpl.COLUMNS + " FROM matches WHERE external_id = ? FOR UPDATE";

    private static final String INSERT =
            "INSERT INTO matches (external_id, matchday, kickoff_utc, status, home_team, home_team_short, home_crest, "
            + "away_team, away_team_short, away_crest, home_goals, away_goals) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (external_id) DO NOTHING RETURNING id";

    private static final String UPDATE =
            "UPDATE matches SET matchday = ?, kickoff_utc = ?, status = ?, home_team = ?, home_team_short = ?, "
            + "home_crest = ?, away_team = ?, away_team_short = ?, away_crest = ?, home_goals = ?, away_goals = ?, "
            + "updated_at = now() WHERE external_id = ? AND manually_corrected = FALSE";

    // Tipps eines Spiels sperren, damit eine zweite Wertung desselben Spiels wartet
    private static final String SELECT_ALL_TIPS =
            "SELECT id, home_goals, away_goals, points FROM tips WHERE match_id = ? FOR UPDATE";

    private static final String SELECT_UNSCORED_TIPS =
            "SELECT id, home_goals, away_goals, points FROM tips WHERE match_id = ? AND points IS NULL FOR UPDATE";

    private static final String UPDATE_TIP_POINTS = "UPDATE tips SET points = ? WHERE id = ?";

    private static final String CLEAR_TIP_POINTS =
            "UPDATE tips SET points = NULL WHERE match_id = ? AND points IS NOT NULL";

    private final DataSource dataSource;

    public PostgresMatchSyncManagerImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UpsertResult upsert(MatchUpdate update) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                Match existing = findForUpdate(con, update.externalId());
                Outcome outcome = MatchUpsertRules.decide(existing, update);
                boolean resultChanged = outcome != Outcome.MANUALLY_CORRECTED
                        && MatchUpsertRules.resultChanged(existing, update);
                Long matchId = existing == null ? null : existing.id();
                switch (outcome) {
                    case INSERTED -> matchId = insert(con, update);
                    case UPDATED -> update(con, update);
                    default -> { }
                }
                int tipsScored = applyScoring(con, matchId, MatchUpsertRules.scoring(existing, update, outcome),
                        outcome == Outcome.UPDATED ? update.homeGoals() : existing == null ? null : existing.homeGoals(),
                        outcome == Outcome.UPDATED ? update.awayGoals() : existing == null ? null : existing.awayGoals());
                con.commit();
                return new UpsertResult(outcome, matchId, resultChanged, tipsScored);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error while saving match " + update.externalId(), e);
        }
    }

    // Setzt oder löscht die Punkte der Tipps zu diesem Spiel; Rückgabe: Anzahl geänderter Tipps
    private static int applyScoring(Connection con, Long matchId, Scoring scoring, Integer home, Integer away)
            throws SQLException {
        if (matchId == null) {
            return 0;
        }
        return switch (scoring) {
            case ALL -> scoreTips(con, SELECT_ALL_TIPS, matchId, home, away);
            case MISSING -> scoreTips(con, SELECT_UNSCORED_TIPS, matchId, home, away);
            case CLEAR -> clearPoints(con, matchId);
            case NONE -> 0;
        };
    }

    private static int scoreTips(Connection con, String select, long matchId, int home, int away) throws SQLException {
        List<long[]> changed = new ArrayList<>(); // je Eintrag: Tipp-ID und neue Punkte
        try (PreparedStatement ps = con.prepareStatement(select)) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int points = PointsCalculator.points(rs.getInt("home_goals"), rs.getInt("away_goals"), home, away);
                    Integer current = rs.getObject("points", Integer.class);
                    if (current == null || current != points) {
                        changed.add(new long[] {rs.getLong("id"), points});
                    }
                }
            }
        }
        if (changed.isEmpty()) {
            return 0;
        }
        try (PreparedStatement ps = con.prepareStatement(UPDATE_TIP_POINTS)) {
            for (long[] tip : changed) {
                ps.setInt(1, (int) tip[1]);
                ps.setLong(2, tip[0]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return changed.size();
    }

    private static int clearPoints(Connection con, long matchId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(CLEAR_TIP_POINTS)) {
            ps.setLong(1, matchId);
            return ps.executeUpdate();
        }
    }

    private static Match findForUpdate(Connection con, long externalId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SELECT_FOR_UPDATE)) {
            ps.setLong(1, externalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? PostgresMatchManagerImpl.toMatch(rs) : null;
            }
        }
    }

    private static Long insert(Connection con, MatchUpdate u) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT)) {
            ps.setLong(1, u.externalId());
            bindFields(ps, 2, u);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static void update(Connection con, MatchUpdate u) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(UPDATE)) {
            int next = bindFields(ps, 1, u);
            ps.setLong(next, u.externalId());
            ps.executeUpdate();
        }
    }

    // Gleiche Reihenfolge in INSERT und UPDATE: matchday bis away_goals
    private static int bindFields(PreparedStatement ps, int start, MatchUpdate u) throws SQLException {
        int i = start;
        ps.setInt(i++, u.matchday());
        ps.setObject(i++, OffsetDateTime.ofInstant(u.kickoffUtc(), ZoneOffset.UTC));
        ps.setString(i++, u.status().name());
        ps.setString(i++, u.homeTeam());
        ps.setString(i++, u.homeTeamShort());
        ps.setString(i++, u.homeCrest());
        ps.setString(i++, u.awayTeam());
        ps.setString(i++, u.awayTeamShort());
        ps.setString(i++, u.awayCrest());
        setGoals(ps, i++, u.homeGoals());
        setGoals(ps, i++, u.awayGoals());
        return i;
    }

    private static void setGoals(PreparedStatement ps, int index, Integer goals) throws SQLException {
        if (goals == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, goals);
        }
    }
}
