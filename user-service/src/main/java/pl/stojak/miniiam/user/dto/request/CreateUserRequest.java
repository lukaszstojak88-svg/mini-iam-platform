package pl.stojak.miniiam.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * New account data. {@code temporaryPassword} is passed to Keycloak only (must be changed at first login);
 * it is never stored, logged or returned by the application.
 */
public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,100}$",
                message = "must be 3-100 characters: letters, digits, '.', '_' or '-'") String username,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @NotBlank @Size(min = 8, max = 128) String temporaryPassword) {

    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + "]";
    }
}
