package com.mosbach.tipprunde.data.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import com.mosbach.tipprunde.model.group.GroupDetails;
import com.mosbach.tipprunde.model.group.GroupSummary;
import com.mosbach.tipprunde.model.group.InvitePreview;
import com.mosbach.tipprunde.model.group.Member;
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
    // Höchstzahl der Mitglieder einer Gruppe (FA-23.1)
    public static final int MAX_MEMBERS = 200;

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

    private static final String SELECT_GROUP = "SELECT name, invite_code FROM groups WHERE id = ?";
    // Admin zuerst, sonst nach Beitrittszeit
    private static final String SELECT_MEMBERS =
            "SELECT gm.user_id, u.username, gm.role, gm.joined_at FROM group_members gm "
                    + "JOIN users u ON u.id = gm.user_id WHERE gm.group_id = ? "
                    + "ORDER BY (gm.role = 'ADMIN') DESC, gm.joined_at, gm.user_id";

    private static final String SELECT_INVITE_PREVIEW =
            "SELECT g.id, g.name, (SELECT COUNT(*) FROM group_members m WHERE m.group_id = g.id) AS member_count "
                    + "FROM groups g WHERE g.invite_code = ?";
    // FOR UPDATE sperrt die Gruppenzeile: gleichzeitige Beitritte laufen nacheinander und überschreiten die Grenze nicht
    private static final String LOCK_GROUP_BY_CODE = "SELECT id FROM groups WHERE invite_code = ? FOR UPDATE";
    private static final String COUNT_MEMBERS =
            "SELECT COUNT(*) AS member_count, COUNT(*) FILTER (WHERE user_id = ?) AS is_member "
                    + "FROM group_members WHERE group_id = ?";
    private static final String INSERT_MEMBER =
            "INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')";

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

    @Override
    public GroupDetails getGroupDetails(long groupId, boolean includeInviteCode) {
        try (Connection con = dataSource.getConnection()) {
            String name;
            String inviteCode;
            try (PreparedStatement ps = con.prepareStatement(SELECT_GROUP)) {
                ps.setLong(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND",
                                "Diese Tippgruppe gibt es nicht.");
                    }
                    name = rs.getString("name");
                    inviteCode = rs.getString("invite_code");
                }
            }
            List<Member> members = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(SELECT_MEMBERS)) {
                ps.setLong(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp joinedAt = rs.getTimestamp("joined_at");
                        members.add(new Member(rs.getLong("user_id"), rs.getString("username"),
                                rs.getString("role"), joinedAt.toInstant()));
                    }
                }
            }
            return new GroupDetails(groupId, name, includeInviteCode ? inviteCode : null, members);
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankfehler beim Lesen der Gruppendetails", e);
        }
    }

    @Override
    public InvitePreview getInvitePreview(String inviteCode) {
        try (Connection con = dataSource.getConnection();
                PreparedStatement ps = con.prepareStatement(SELECT_INVITE_PREVIEW)) {
            ps.setString(1, inviteCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw invalidInvite();
                }
                return new InvitePreview(rs.getLong("id"), rs.getString("name"), rs.getInt("member_count"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankfehler bei der Einladungsvorschau", e);
        }
    }

    @Override
    public GroupSummary joinGroup(long userId, String inviteCode) {
        long groupId;
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                groupId = lockGroup(con, inviteCode);
                int memberCount;
                boolean alreadyMember;
                try (PreparedStatement ps = con.prepareStatement(COUNT_MEMBERS)) {
                    ps.setLong(1, userId);
                    ps.setLong(2, groupId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        memberCount = rs.getInt("member_count");
                        alreadyMember = rs.getInt("is_member") > 0;
                    }
                }
                // Wer schon Mitglied ist, bekommt auch bei voller Gruppe ALREADY_MEMBER
                if (alreadyMember) {
                    log.warn("Beitritt abgelehnt: Nutzer {} ist schon Mitglied von Gruppe {}", userId, groupId);
                    throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER",
                            "Du bist schon Mitglied dieser Tippgruppe.");
                }
                if (memberCount >= MAX_MEMBERS) {
                    log.warn("Beitritt abgelehnt: Gruppe {} ist voll", groupId);
                    throw new ApiException(HttpStatus.CONFLICT, "GROUP_FULL",
                            "Diese Tippgruppe ist voll. Es sind höchstens " + MAX_MEMBERS + " Mitglieder möglich.");
                }
                try (PreparedStatement ps = con.prepareStatement(INSERT_MEMBER)) {
                    ps.setLong(1, groupId);
                    ps.setLong(2, userId);
                    ps.executeUpdate();
                }
                con.commit();
                log.info("Nutzer {} ist Gruppe {} beigetreten", userId, groupId);
            } catch (ApiException | SQLException e) {
                con.rollback();
                if (e instanceof SQLException sql && FOREIGN_KEY_VIOLATION.equals(sql.getSQLState())) {
                    // Token ist gültig, den Nutzer gibt es aber nicht mehr
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            "Nicht angemeldet oder Sitzung abgelaufen.");
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankfehler beim Beitritt zur Gruppe", e);
        }
        return listGroups(userId).stream()
                .filter(group -> group.id() == groupId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gruppe nach dem Beitritt nicht gefunden"));
    }

    private static long lockGroup(Connection con, String inviteCode) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(LOCK_GROUP_BY_CODE)) {
            ps.setString(1, inviteCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw invalidInvite();
                }
                return rs.getLong("id");
            }
        }
    }

    private static ApiException invalidInvite() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVITE_INVALID",
                "Dieser Einladungscode ist ungültig.");
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
