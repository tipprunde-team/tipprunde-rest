package com.mosbach.tipprunde.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mosbach.tipprunde.data.api.GroupManager;
import com.mosbach.tipprunde.model.group.CreateGroupRequest;
import com.mosbach.tipprunde.model.group.GroupCreated;
import com.mosbach.tipprunde.model.group.GroupSummary;
import com.mosbach.tipprunde.security.CurrentUserId;

import jakarta.validation.Valid;

// Tippgruppen anlegen und eigene Gruppen auflisten, beides nur mit Token
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupManager groupManager;

    public GroupController(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    // Die Nutzer-ID kommt aus dem geprüften Token, nie aus dem Body
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupCreated create(@CurrentUserId long userId, @Valid @RequestBody CreateGroupRequest request) {
        return groupManager.createGroup(userId, request);
    }

    @GetMapping
    public List<GroupSummary> list(@CurrentUserId long userId) {
        return groupManager.listGroups(userId);
    }
}
