package com.mosbach.tipprunde.data.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.MatchSyncManager;
import com.mosbach.tipprunde.domain.MatchUpsertRules;
import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult;
import com.mosbach.tipprunde.model.match.UpsertResult.Outcome;

// Übernimmt Spiele aus der Queue in die Tabelle matches; Schlüssel ist external_id (wie das Seed-Skript)
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
                con.commit();
                return new UpsertResult(outcome, matchId, resultChanged);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error while saving match " + update.externalId(), e);
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
