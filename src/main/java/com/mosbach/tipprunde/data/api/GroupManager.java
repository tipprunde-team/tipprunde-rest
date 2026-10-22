package com.mosbach.tipprunde.data.api;

import java.util.List;

import com.mosbach.tipprunde.model.group.CreateGroupRequest;
import com.mosbach.tipprunde.model.group.GroupCreated;
import com.mosbach.tipprunde.model.group.GroupDetails;
import com.mosbach.tipprunde.model.group.GroupSummary;
import com.mosbach.tipprunde.model.group.InvitePreview;

// Tippgruppen anlegen und auflisten
public interface GroupManager {

    // Legt Gruppe und Admin-Mitgliedschaft des Nutzers in einer Transaktion an
    GroupCreated createGroup(long userId, CreateGroupRequest request);

    // Alle Gruppen des Nutzers mit Mitgliederzahl, Punktesumme und Rang
    List<GroupSummary> listGroups(long userId);

    // Name, Mitglieder und (nur wenn includeInviteCode) der Einladungscode, die Mitgliedschaft prüft vorher GroupAccess
    GroupDetails getGroupDetails(long groupId, boolean includeInviteCode);

    // Vorschau für die Beitrittsseite, 404 INVITE_INVALID bei unbekanntem Code
    InvitePreview getInvitePreview(String inviteCode);

    // Tritt der Gruppe als MEMBER bei und liefert den Eintrag wie in listGroups.
    // 404 INVITE_INVALID, 409 ALREADY_MEMBER, 409 GROUP_FULL (ab 200 Mitgliedern), alles in einer Transaktion
    GroupSummary joinGroup(long userId, String inviteCode);
}
