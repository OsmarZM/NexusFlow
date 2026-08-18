package com.nexusflow.security.api;

import com.nexusflow.security.application.AuthService;
import com.nexusflow.security.application.dto.AuthRequestDTO;
import com.nexusflow.security.application.dto.AuthResponseDTO;
import com.nexusflow.security.application.dto.RegisterUserDTO;
import com.nexusflow.security.application.dto.UserProfileDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, authentication and profile")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and obtain JWT access token")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterUserDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserProfileDTO> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(authService.getCurrentUserProfile(authentication));
    }
}
