package pl.stojak.miniiam.user.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Profile returned by {@code GET /api/users/{id}}: the profile fields (flattened) plus {@code editable}, i.e.
 * whether the caller may change this profile with {@code PUT /api/users/{id}}. A MANAGER may not edit ADMIN
 * accounts; the UI uses the flag to hide the edit action instead of failing on save.
 */
public record UserDetailsResponse(@JsonUnwrapped UserResponse user, boolean editable) {
}
