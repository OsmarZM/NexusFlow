package com.nexusflow.unit.security;

import com.nexusflow.security.application.AuthService;
import com.nexusflow.security.application.JwtService;
import com.nexusflow.security.application.dto.AuthRequestDTO;
import com.nexusflow.security.application.dto.AuthResponseDTO;
import com.nexusflow.security.application.dto.RegisterUserDTO;
import com.nexusflow.security.domain.Role;
import com.nexusflow.security.domain.User;
import com.nexusflow.security.domain.UserRepository;
import com.nexusflow.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Should successfully authenticate user and return JWT token")
    void shouldLoginSuccessfully() {
        AuthRequestDTO request = new AuthRequestDTO("admin", "Admin@123456");
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .email("admin@nexusflow.com")
                .fullName("Administrator")
                .roles(Set.of(Role.ADMIN))
                .build();

        when(userRepository.findByUsernameOrEmail("admin")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(jwtService.generateToken(user)).thenReturn("mock-jwt-token");

        AuthResponseDTO response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.roles()).contains("ADMIN");
        verify(authenticationManager, times(1)).authenticate(any());
    }

    @Test
    @DisplayName("Should register new user with hashed password and return token")
    void shouldRegisterNewUserSuccessfully() {
        RegisterUserDTO request = new RegisterUserDTO("tony", "tony@stark.com", "Secret@123", "Tony Stark", Set.of(Role.CUSTOMER));

        when(userRepository.existsByUsername("tony")).thenReturn(false);
        when(userRepository.existsByEmail("tony@stark.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret@123")).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token");

        AuthResponseDTO response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.email()).isEqualTo("tony@stark.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw BusinessException when registering with duplicate username")
    void shouldThrowExceptionWhenUsernameAlreadyExists() {
        RegisterUserDTO request = new RegisterUserDTO("tony", "tony@stark.com", "Secret@123", "Tony Stark", Set.of(Role.CUSTOMER));

        when(userRepository.existsByUsername("tony")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already taken");

        verify(userRepository, never()).save(any(User.class));
    }
}
