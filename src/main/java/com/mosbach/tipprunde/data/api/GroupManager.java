package com.mosbach.tipprunde.data.api;

import java.util.List;

import com.mosbach.tipprunde.model.group.CreateGroupRequest;
import com.mosbach.tipprunde.model.group.GroupCreated;
import com.mosbach.tipprunde.model.group.GroupSummary;

// Tippgruppen anlegen und auflisten
public interface GroupManager {

    // Legt Gruppe und Admin-Mitgliedschaft des Nutzers in einer Transaktion an
    GroupCreated createGroup(long userId, CreateGroupRequest request);

    // Alle Gruppen des Nutzers mit Mitgliederzahl, Punktesumme und Rang
    List<GroupSummary> listGroups(long userId);
}
