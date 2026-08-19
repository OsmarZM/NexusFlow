package com.nexusflow.security.application;

import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.order.domain.OrderRepository;
import com.nexusflow.security.domain.User;
import com.nexusflow.security.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component("securityService")
@RequiredArgsConstructor
@Slf4j
public class ResourceSecurityService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public boolean isOrderOwner(UUID orderId, Authentication authentication) {
        if (orderId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return orderRepository.findById(orderId)
                .map(order -> isCustomerOwner(order.getCustomer().getId(), authentication))
                .orElse(false);
    }

    public boolean isCustomerOwner(UUID customerId, Authentication authentication) {
        if (customerId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String principalName = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(principalName)
                .or(() -> userRepository.findByEmail(principalName));

        // 1. Direct explicit link check: user.getCustomerId() == target customerId
        if (userOpt.isPresent() && userOpt.get().getCustomerId() != null) {
            return userOpt.get().getCustomerId().equals(customerId);
        }

        // 2. Fallback check: matching customer email/document with user email or principal name
        return customerRepository.findById(customerId)
                .map(customer -> isPrincipalMatchingCustomer(principalName, userOpt.orElse(null), customer))
                .orElse(false);
    }

    private boolean isPrincipalMatchingCustomer(String principalName, User user, Customer customer) {
        if (customer == null || principalName == null) {
            return false;
        }

        if (user != null && user.getEmail() != null && user.getEmail().equalsIgnoreCase(customer.getEmail())) {
            return true;
        }

        return principalName.equalsIgnoreCase(customer.getEmail())
                || principalName.equalsIgnoreCase(customer.getDocument())
                || principalName.equalsIgnoreCase(customer.getName());
    }
}
