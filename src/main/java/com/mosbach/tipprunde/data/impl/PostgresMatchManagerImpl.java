package com.mosbach.tipprunde.data.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.MatchManager;
import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;

// Matches from table matches, active with app.match.manager=postgres
@Component
@ConditionalOnProperty(name = "app.match.manager", havingValue = "postgres")
public class PostgresMatchManagerImpl implements MatchManager {

    private static final String COLUMNS =
            "id, external_id, matchday, kickoff_utc, status, home_team, home_team_short, home_crest, "
            + "away_team, away_team_short, away_crest, home_goals, away_goals, manually_corrected, updated_at";

    private static final String SELECT_BY_MATCHDAY =
            "SELECT " + COLUMNS + " FROM matches WHERE matchday = ? ORDER BY kickoff_utc, id";

    private static final String SELECT_BY_ID = "SELECT " + COLUMNS + " FROM matches WHERE id = ?";

    // Matchday of the next unfinished match, otherwise the highest one, otherwise 1
    private static final String SELECT_CURRENT_MATCHDAY =
            "SELECT COALESCE("
            + "(SELECT matchday FROM matches WHERE status IN ('UPCOMING', 'LIVE') ORDER BY kickoff_utc, id LIMIT 1), "
            + "(SELECT MAX(matchday) FROM matches), 1)";

    private final DataSource dataSource;

    public PostgresMatchManagerImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Match> getMatchesByMatchday(int matchday) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_BY_MATCHDAY)) {
            statement.setInt(1, matchday);
            try (ResultSet result = statement.executeQuery()) {
                List<Match> matches = new ArrayList<>();
                while (result.next()) {
                    matches.add(toMatch(result));
                }
                return matches;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error while loading matchday", e);
        }
    }

    @Override
    public Optional<Match> getMatchById(long id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(toMatch(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error while loading match", e);
        }
    }

    @Override
    public int getCurrentMatchday() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_CURRENT_MATCHDAY);
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Database error while loading current matchday", e);
        }
    }

    // One row of table matches as record; TIMESTAMPTZ is read as UTC instant
    private static Match toMatch(ResultSet row) throws SQLException {
        return new Match(
                row.getLong("id"),
                row.getLong("external_id"),
                row.getInt("matchday"),
                row.getObject("kickoff_utc", OffsetDateTime.class).toInstant(),
                MatchStatus.valueOf(row.getString("status")),
                row.getString("home_team"),
                row.getString("home_team_short"),
                row.getString("home_crest"),
                row.getString("away_team"),
                row.getString("away_team_short"),
                row.getString("away_crest"),
                row.getObject("home_goals", Integer.class),
                row.getObject("away_goals", Integer.class),
                row.getBoolean("manually_corrected"),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
