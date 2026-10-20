package com.mosbach.tipprunde.data.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.GroupManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.group.CreateGroupRequest;
import com.mosbach.tipprunde.model.group.GroupCreated;
import com.mosbach.tipprunde.model.group.GroupSummary;
import com.mosbach.tipprunde.security.Membership;

// Gruppen in den Tabellen groups und group_members, alle Abfragen mit PreparedStatement
@Component
public class PostgresGroupManagerImpl implements GroupManager {

    private static final Logger log = LoggerFactory.getLogger(PostgresGroupManagerImpl.class);

    // SQLSTATE für Verletzung einer UNIQUE-Regel bzw. eines Fremdschlüssels
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    // Bei 55^10 möglichen Codes reicht ein Wiederholungsversuch praktisch immer, die Grenze schützt vor Endlosschleifen
    private static final int MAX_CODE_ATTEMPTS = 10;

    private static final String INSERT_GROUP =
            "INSERT INTO groups (name, invite_code) VALUES (?, ?) RETURNING id";
    private static final String INSERT_ADMIN =
            "INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'ADMIN')";

    // Punkte je Mitglied (null zählt als 0), Rang per RANK() innerhalb der Gruppe, nur für die Gruppen des Nutzers
    private static final String SELECT_GROUPS = """
            WITH my_groups AS (
                SELECT group_id, role FROM group_members WHERE user_id = ?
            ),
            points AS (
                SELECT gm.group_id, gm.user_id, COALESCE(SUM(t.points), 0) AS pts
                FROM group_members gm
                JOIN my_groups mg ON mg.group_id = gm.group_id
                LEFT JOIN tips t ON t.group_id = gm.group_id AND t.user_id = gm.user_id
                GROUP BY gm.group_id, gm.user_id
            ),
            ranked AS (
                SELECT group_id, user_id, pts,
                       RANK() OVER (PARTITION BY group_id ORDER BY pts DESC) AS rnk,
                       COUNT(*) OVER (PARTITION BY group_id) AS member_count
                FROM points
            )
            SELECT g.id, g.name, mg.role, r.member_count, r.rnk, r.pts
            FROM my_groups mg
            JOIN groups g ON g.id = mg.group_id
            JOIN ranked r ON r.group_id = mg.group_id AND r.user_id = ?
            ORDER BY g.name, g.id
            """;

    private final DataSource dataSource;
    private final InviteCodeGenerator codeGenerator;

    public PostgresGroupManagerImpl(DataSource dataSource, InviteCodeGenerator codeGenerator) {
        this.dataSource = dataSource;
        this.codeGenerator = codeGenerator;
    }

    @Override
    public GroupCreated createGroup(long userId, CreateGroupRequest request) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            // Bei einer Kollision des Codes (UNIQUE) wird die ganze Transaktion mit neuem Code wiederholt
            for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
                String inviteCode = codeGenerator.generate();
                try {
                    long groupId = insertGroup(con, request.name(), inviteCode);
                    insertAdmin(con, groupId, userId);
                    con.commit();
                    log.info("Gruppe {} von Nutzer {} angelegt", groupId, userId);
                    return new GroupCreated(groupId, request.name(), inviteCode, Membership.ADMIN);
                } catch (SQLException e) {
                    con.rollback();
                    if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                        log.warn("Einladungscode schon vergeben, neuer Versuch {}", attempt);
                        continue;
                    }
                    if (FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
                        // Token ist gültig, den Nutzer gibt es aber nicht mehr
                        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                "Nicht angemeldet oder Sitzung abgelaufen.");
                    }
                    throw e;
                }
            }
            throw new IllegalStateException("Kein freier Einladungscode gefunden");
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankfehler beim Anlegen der Gruppe", e);
        }
    }

    @Override
    public List<GroupSummary> listGroups(long userId) {
        try (Connection con = dataSource.getConnection();
                PreparedStatement ps = con.prepareStatement(SELECT_GROUPS)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<GroupSummary> groups = new ArrayList<>();
                while (rs.next()) {
                    groups.add(new GroupSummary(rs.getLong("id"), rs.getString("name"), rs.getString("role"),
                            rs.getInt("member_count"), rs.getInt("rnk"), rs.getInt("pts")));
                }
                return groups;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankfehler beim Lesen der Gruppen", e);
        }
    }

    private static long insertGroup(Connection con, String name, String inviteCode) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_GROUP)) {
            ps.setString(1, name);
            ps.setString(2, inviteCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void insertAdmin(Connection con, long groupId, long userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_ADMIN)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }
}
