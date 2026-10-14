package com.mosbach.tipprunde.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mosbach.tipprunde.data.api.UserManager;
import com.mosbach.tipprunde.model.user.RegisterRequest;
import com.mosbach.tipprunde.model.user.UserResponse;

import jakarta.validation.Valid;

// Registrierung unter /api/users, ohne Token erreichbar
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManager userManager;

    public UserController(UserManager userManager) {
        this.userManager = userManager;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userManager.register(request);
    }
}
