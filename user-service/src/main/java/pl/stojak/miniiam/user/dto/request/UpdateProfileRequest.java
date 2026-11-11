package pl.stojak.miniiam.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Editable profile fields. The username cannot be changed. First and last name are required, so Keycloak never asks
 * the user to complete the profile at sign-in.
 */
public record UpdateProfileRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName) {
}
