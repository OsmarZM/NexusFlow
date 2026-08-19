package com.nexusflow.security.application;

import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.order.domain.Order;
import com.nexusflow.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("securityService")
@RequiredArgsConstructor
@Slf4j
public class ResourceSecurityService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public boolean isOrderOwner(UUID orderId, Authentication authentication) {
        if (orderId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String principalName = authentication.getName();
        return orderRepository.findById(orderId)
                .map(order -> isPrincipalMatchingCustomer(principalName, order.getCustomer()))
                .orElse(false);
    }

    public boolean isCustomerOwner(UUID customerId, Authentication authentication) {
        if (customerId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String principalName = authentication.getName();
        return customerRepository.findById(customerId)
                .map(customer -> isPrincipalMatchingCustomer(principalName, customer))
                .orElse(false);
    }

    private boolean isPrincipalMatchingCustomer(String principalName, Customer customer) {
        if (customer == null || principalName == null) {
            return false;
        }
        return principalName.equalsIgnoreCase(customer.getEmail())
                || principalName.equalsIgnoreCase(customer.getDocument())
                || principalName.equalsIgnoreCase(customer.getName());
    }
}
