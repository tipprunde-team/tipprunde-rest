package com.mosbach.tipprunde.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.error.ApiException;

// Temporary membership check until GroupAccess is merged; then switch MatchController and delete this class
@Component
public class MembershipCheck {

    private static final String SELECT_GROUP_AND_MEMBERSHIP =
            "SELECT EXISTS (SELECT 1 FROM groups WHERE id = ?) AS group_exists, "
            + "EXISTS (SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?) AS is_member";

    private final DataSource dataSource;

    public MembershipCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // 404 GROUP_NOT_FOUND or 403 NOT_A_MEMBER, same codes as GroupAccess.requireMember
    public void requireMember(long groupId, long userId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_GROUP_AND_MEMBERSHIP)) {
            statement.setLong(1, groupId);
            statement.setLong(2, groupId);
            statement.setLong(3, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (!result.getBoolean("group_exists")) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Diese Tippgruppe gibt es nicht.");
                }
                if (!result.getBoolean("is_member")) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER",
                            "Du bist kein Mitglied dieser Tippgruppe.");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error while checking membership", e);
        }
    }
}
