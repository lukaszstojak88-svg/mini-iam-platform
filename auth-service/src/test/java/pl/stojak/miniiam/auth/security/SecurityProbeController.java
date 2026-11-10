package pl.stojak.miniiam.auth.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints used to verify the security rules before business endpoints exist. */
@RestController
class SecurityProbeController {

    @GetMapping("/api/probe")
    String authenticated() {
        return "ok";
    }

    @GetMapping("/api/probe/admin")
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
        return "ok";
    }

    @GetMapping("/internal/api/probe")
    String internal() {
        return "ok";
    }

    @GetMapping("/other/probe")
    String unmapped() {
        return "ok";
    }
}
