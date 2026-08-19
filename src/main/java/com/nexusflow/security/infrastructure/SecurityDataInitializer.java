package com.nexusflow.security.infrastructure;

import com.nexusflow.security.domain.Role;
import com.nexusflow.security.domain.User;
import com.nexusflow.security.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    @Transactional
    public void run(String... args) {
        userRepository.findByUsername("admin").ifPresentOrElse(
                admin -> {
                    admin.setPassword(passwordEncoder.encode("Admin@123456"));
                    Set<Role> roles = new HashSet<>();
                    roles.add(Role.ADMIN);
                    roles.add(Role.WAREHOUSE_OPERATOR);
                    roles.add(Role.FINANCE);
                    admin.setRoles(roles);
                    userRepository.save(admin);
                    log.info("Default admin user password synchronized successfully");
                },
                () -> {
                    Set<Role> roles = new HashSet<>();
                    roles.add(Role.ADMIN);
                    roles.add(Role.WAREHOUSE_OPERATOR);
                    roles.add(Role.FINANCE);
                    User admin = User.builder()
                            .username("admin")
                            .email("admin@nexusflow.com")
                            .password(passwordEncoder.encode("Admin@123456"))
                            .fullName("NexusFlow System Administrator")
                            .enabled(true)
                            .roles(roles)
                            .build();
                    userRepository.save(admin);
                    log.info("Default admin user created successfully");
                }
        );
    }
}
