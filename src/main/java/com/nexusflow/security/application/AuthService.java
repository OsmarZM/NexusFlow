package com.nexusflow.security.application;

import com.nexusflow.security.application.dto.AuthRequestDTO;
import com.nexusflow.security.application.dto.AuthResponseDTO;
import com.nexusflow.security.application.dto.RegisterUserDTO;
import com.nexusflow.security.application.dto.UserProfileDTO;
import com.nexusflow.security.domain.Role;
import com.nexusflow.security.domain.User;
import com.nexusflow.security.domain.UserRepository;
import com.nexusflow.shared.exception.BusinessException;
import com.nexusflow.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponseDTO login(AuthRequestDTO request) {
        log.info("Authenticating user: {}", request.usernameOrEmail());

        User user = userRepository.findByUsernameOrEmail(request.usernameOrEmail().trim())
                .orElseThrow(() -> new BusinessException("Invalid username/email or password"));

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        request.password()
                )
        );

        String token = jwtService.generateToken(user);
        log.info("User {} successfully authenticated", user.getUsername());

        return new AuthResponseDTO(
                token,
                "Bearer",
                120L,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles().stream().map(Role::name).collect(Collectors.toSet())
        );
    }

    @Transactional
    public AuthResponseDTO register(RegisterUserDTO request) {
        log.info("Registering new user with username: {} and email: {}", request.username(), request.email());

        if (userRepository.existsByUsername(request.username().trim())) {
            throw new BusinessException("Username '" + request.username() + "' is already taken.");
        }
        if (userRepository.existsByEmail(request.email().trim())) {
            throw new BusinessException("Email '" + request.email() + "' is already registered.");
        }

        Set<Role> roles = (request.roles() != null && !request.roles().isEmpty())
                ? request.roles()
                : Set.of(Role.CUSTOMER);

        User user = User.builder()
                .username(request.username().trim().toLowerCase())
                .email(request.email().trim().toLowerCase())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .enabled(true)
                .roles(roles)
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);
        log.info("User {} registered with ID: {}", saved.getUsername(), saved.getId());

        return new AuthResponseDTO(
                token,
                "Bearer",
                120L,
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getFullName(),
                saved.getRoles().stream().map(Role::name).collect(Collectors.toSet())
        );
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getCurrentUserProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("No authenticated principal found.");
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found for username: " + username));

        return UserProfileDTO.fromEntity(user);
    }
}
