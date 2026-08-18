package com.nexusflow.security.application.dto;

import com.nexusflow.security.domain.Role;
import com.nexusflow.security.domain.User;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserProfileDTO(
        UUID id,
        String username,
        String email,
        String fullName,
        boolean enabled,
        Set<String> roles,
        OffsetDateTime createdAt
) {
    public static UserProfileDTO fromEntity(User user) {
        return new UserProfileDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.isEnabled(),
                user.getRoles().stream().map(Role::name).collect(Collectors.toSet()),
                user.getCreatedAt()
        );
    }
}
