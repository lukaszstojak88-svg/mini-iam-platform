package pl.stojak.miniiam.user.mapper;

import pl.stojak.miniiam.user.domain.UserProfile;
import pl.stojak.miniiam.user.dto.response.UserResponse;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(UserProfile profile) {
        return new UserResponse(
                profile.getId(),
                profile.getKeycloakId(),
                profile.getUsername(),
                profile.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.isEnabled(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
