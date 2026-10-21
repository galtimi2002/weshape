package com.weshape.backend.controller;

import com.weshape.backend.dto.UpdatePreferencesRequest;
import com.weshape.backend.dto.UpdateProfileRequest;
import com.weshape.backend.dto.UserDto;
import com.weshape.backend.security.AppPrincipal;
import com.weshape.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "The signed-in user's profile. The first call after sign-up creates it.")
    @GetMapping
    public UserDto me(@AuthenticationPrincipal AppPrincipal principal) {
        return UserDto.from(userService.requireById(principal.userId()));
    }

    @Operation(summary = "Complete or edit the profile: first and last name, country, language, theme")
    @PutMapping
    public UserDto updateProfile(@AuthenticationPrincipal AppPrincipal principal,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return UserDto.from(userService.updateProfile(principal.userId(), request));
    }

    @Operation(summary = "Save language and/or theme")
    @PutMapping("/preferences")
    public UserDto updatePreferences(@AuthenticationPrincipal AppPrincipal principal,
                                     @RequestBody UpdatePreferencesRequest request) {
        return UserDto.from(userService.updatePreferences(principal.userId(), request));
    }
}
