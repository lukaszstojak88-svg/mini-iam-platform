package pl.stojak.miniiam.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/users/{userId}/roles}. Allowed values are checked by the service (ADMIN, MANAGER, AUDITOR). */
public record AssignRoleRequest(@NotBlank @Size(max = 64) String roleName) {
}
