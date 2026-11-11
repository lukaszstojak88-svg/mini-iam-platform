package pl.stojak.miniiam.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.stojak.miniiam.user.dto.request.CreateUserRequest;
import pl.stojak.miniiam.user.dto.request.UpdateProfileRequest;
import pl.stojak.miniiam.user.dto.request.UpdateStatusRequest;
import pl.stojak.miniiam.user.dto.response.PageResponse;
import pl.stojak.miniiam.user.dto.response.UserResponse;
import pl.stojak.miniiam.user.service.Actor;
import pl.stojak.miniiam.user.service.UserService;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** User management API (spec §6.1). Authorization rules follow the matrix in spec §2.1. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) @Size(max = 100) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.list(search, pageable);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(JwtAuthenticationToken authentication) {
        return userService.getOwnProfile(actor(authentication));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request,
                                 JwtAuthenticationToken authentication) {
        return userService.updateOwnProfile(request, actor(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                               JwtAuthenticationToken authentication) {
        UserResponse created = userService.create(request, actor(authentication));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProfileRequest request,
                               JwtAuthenticationToken authentication) {
        return userService.update(id, request, actor(authentication));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request,
                                     JwtAuthenticationToken authentication) {
        return userService.updateStatus(id, request.enabled(), actor(authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        userService.delete(id, actor(authentication));
        return ResponseEntity.noContent().build();
    }

    private static Actor actor(JwtAuthenticationToken authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
        return new Actor(authentication.getName(),
                authentication.getToken().getClaimAsString("preferred_username"), roles);
    }
}
