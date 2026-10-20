package com.mosbach.tipprunde.data.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.TipManager;
import com.mosbach.tipprunde.model.tip.Tip;

// Tips in table tips, PreparedStatement only
@Component
public class PostgresTipManagerImpl implements TipManager {

    private static final String SELECT_OWN_TIPS_FOR_MATCHDAY =
            "SELECT t.id, t.group_id, t.user_id, t.match_id, t.home_goals, t.away_goals, t.points, t.updated_at "
            + "FROM tips t JOIN matches m ON m.id = t.match_id "
            + "WHERE t.group_id = ? AND t.user_id = ? AND m.matchday = ?";

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
                    tips.add(new Tip(
                            result.getLong("id"),
                            result.getLong("group_id"),
                            result.getLong("user_id"),
                            result.getLong("match_id"),
                            result.getInt("home_goals"),
                            result.getInt("away_goals"),
                            result.getObject("points", Integer.class),
                            result.getObject("updated_at", OffsetDateTime.class).toInstant()));
                }
                return tips;
            }
        } catch (SQLException e) {
            // becomes 500 INTERNAL_ERROR in GlobalExceptionHandler
            throw new IllegalStateException("Database error while loading tips", e);
        }
    }
}
