package com.mosbach.tipprunde.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.error.ApiException;

// Zentrale Prüfung von Mitgliedschaft und Admin-Rolle, wird von jedem /api/groups/{groupId}/**-Endpunkt benutzt
@Component
public class GroupAccess {

    // Eine Abfrage für beide Fälle: ohne Zeile gibt es die Gruppe nicht, ohne role ist der Nutzer kein Mitglied
    private static final String SELECT_MEMBERSHIP =
            "SELECT gm.role, gm.joined_at FROM groups g "
                    + "LEFT JOIN group_members gm ON gm.group_id = g.id AND gm.user_id = ? "
                    + "WHERE g.id = ?";

    private final DataSource dataSource;

    public GroupAccess(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // 404 GROUP_NOT_FOUND bei unbekannter Gruppe, 403 NOT_A_MEMBER bei fremder Gruppe
    public Membership requireMember(long groupId, long userId) {
        try (Connection con = dataSource.getConnection();
                PreparedStatement ps = con.prepareStatement(SELECT_MEMBERSHIP)) {
            ps.setLong(1, userId);
            ps.setLong(2, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND",
                            "Diese Tippgruppe gibt es nicht.");
                }
                String role = rs.getString("role");
                if (role == null) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER",
                            "Du bist kein Mitglied dieser Tippgruppe.");
                }
                Timestamp joinedAt = rs.getTimestamp("joined_at");
                return new Membership(groupId, userId, role, joinedAt.toInstant());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankfehler bei der Prüfung der Mitgliedschaft", e);
        }
    }

    // wie requireMember, zusätzlich 403 NOT_GROUP_ADMIN für normale Mitglieder
    public Membership requireAdmin(long groupId, long userId) {
        Membership membership = requireMember(groupId, userId);
        if (!membership.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_GROUP_ADMIN",
                    "Das darf nur ein Admin der Tippgruppe.");
        }
        return membership;
    }
}
