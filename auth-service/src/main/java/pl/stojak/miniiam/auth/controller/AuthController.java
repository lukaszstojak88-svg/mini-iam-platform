package pl.stojak.miniiam.auth.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.stojak.miniiam.auth.dto.response.CurrentUserResponse;
import pl.stojak.miniiam.auth.service.CurrentUserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    /** Roles and basic access information of the current user (spec §6.2). Available to every authenticated caller. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public CurrentUserResponse me(JwtAuthenticationToken authentication) {
        return currentUserService.describe(authentication);
    }
}
