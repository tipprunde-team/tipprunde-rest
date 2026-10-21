package com.mosbach.tipprunde.data.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.TipManager;
import com.mosbach.tipprunde.model.tip.Tip;

// Tips in table tips, PreparedStatement only
@Component
public class PostgresTipManagerImpl implements TipManager {

    private static final String TIP_COLUMNS =
            "id, group_id, user_id, match_id, home_goals, away_goals, points, updated_at";

    private static final String SELECT_OWN_TIPS_FOR_MATCHDAY =
            "SELECT t.id, t.group_id, t.user_id, t.match_id, t.home_goals, t.away_goals, t.points, t.updated_at "
            + "FROM tips t JOIN matches m ON m.id = t.match_id "
            + "WHERE t.group_id = ? AND t.user_id = ? AND m.matchday = ?";

    // Upsert: a second tip on the same match replaces the first one
    private static final String UPSERT_TIP =
            "INSERT INTO tips (group_id, user_id, match_id, home_goals, away_goals, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (group_id, user_id, match_id) DO UPDATE SET "
            + "home_goals = EXCLUDED.home_goals, away_goals = EXCLUDED.away_goals, updated_at = EXCLUDED.updated_at "
            + "RETURNING " + TIP_COLUMNS;

    private final DataSource dataSource;

    public PostgresTipManagerImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Tip> getOwnTipsForMatchday(long groupId, long userId, int matchday) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_OWN_TIPS_FOR_MATCHDAY)) {
            statement.setLong(1, groupId);
            statement.setLong(2, userId);
            statement.setInt(3, matchday);
            try (ResultSet result = statement.executeQuery()) {
                List<Tip> tips = new ArrayList<>();
                while (result.next()) {
                    tips.add(toTip(result));
                }
                return tips;
            }
        } catch (SQLException e) {
            // becomes 500 INTERNAL_ERROR in GlobalExceptionHandler
            throw new IllegalStateException("Database error while loading tips", e);
        }
    }

    @Override
    public Tip saveTip(long groupId, long userId, long matchId, int home, int away, Instant now) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT_TIP)) {
            statement.setLong(1, groupId);
            statement.setLong(2, userId);
            statement.setLong(3, matchId);
            statement.setInt(4, home);
            statement.setInt(5, away);
            // updated_at is the server time, never a value from the client
            statement.setObject(6, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return toTip(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error while saving tip", e);
        }
    }

    private static Tip toTip(ResultSet row) throws SQLException {
        return new Tip(
                row.getLong("id"),
                row.getLong("group_id"),
                row.getLong("user_id"),
                row.getLong("match_id"),
                row.getInt("home_goals"),
                row.getInt("away_goals"),
                row.getObject("points", Integer.class),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
