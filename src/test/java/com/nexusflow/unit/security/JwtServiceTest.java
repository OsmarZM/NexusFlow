package com.nexusflow.unit.security;

import com.nexusflow.security.application.JwtService;
import com.nexusflow.security.domain.Role;
import com.nexusflow.security.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secretKey, 60, "nexusflow-test-issuer");
    }

    @Test
    @DisplayName("Should generate and extract claims from valid JWT token")
    void shouldGenerateAndExtractClaims() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("brucewayne")
                .email("bruce@waynecorp.com")
                .fullName("Bruce Wayne")
                .roles(Set.of(Role.ADMIN, Role.CUSTOMER))
                .enabled(true)
                .build();

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("brucewayne");
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("Should return false when validating token against wrong user")
    void shouldReturnFalseForDifferentUser() {
        User user1 = User.builder()
                .id(UUID.randomUUID())
                .username("brucewayne")
                .email("bruce@wayne.com")
                .roles(Set.of(Role.ADMIN))
                .build();

        User user2 = User.builder()
                .id(UUID.randomUUID())
                .username("clarkkent")
                .email("clark@dailyplanet.com")
                .roles(Set.of(Role.CUSTOMER))
                .build();

        String token = jwtService.generateToken(user1);

        assertThat(jwtService.isTokenValid(token, user2)).isFalse();
    }
}
