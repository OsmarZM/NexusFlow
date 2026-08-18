package com.nexusflow.security.application.dto;

import java.util.Set;
import java.util.UUID;

public record AuthResponseDTO(
        String accessToken,
        String tokenType,
        long expiresInMinutes,
        UUID userId,
        String username,
        String email,
        String fullName,
        Set<String> roles
) {}
