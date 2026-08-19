package com.nexusflow.security.infrastructure;

import com.nexusflow.security.domain.Role;
import com.nexusflow.security.domain.User;
import com.nexusflow.security.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${nexusflow.security.admin-password:Admin@123456}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        userRepository.findByUsername("admin").ifPresentOrElse(
                admin -> {
                    // In production-grade apps, do NOT overwrite an existing admin's password on restart.
                    log.info("System administrator account 'admin' exists and is ready.");
                },
                () -> {
                    Set<Role> roles = new HashSet<>();
                    roles.add(Role.ADMIN);
                    roles.add(Role.WAREHOUSE_OPERATOR);
                    roles.add(Role.FINANCE);
                    User admin = User.builder()
                            .username("admin")
                            .email("admin@nexusflow.com")
                            .password(passwordEncoder.encode(adminPassword))
                            .fullName("NexusFlow System Administrator")
                            .enabled(true)
                            .roles(roles)
                            .build();
                    userRepository.save(admin);
                    log.info("Initial system administrator 'admin' initialized successfully.");
                }
        );
    }
}
