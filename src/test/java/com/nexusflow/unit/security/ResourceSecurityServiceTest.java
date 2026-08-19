package com.nexusflow.unit.security;

import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.order.domain.Order;
import com.nexusflow.order.domain.OrderRepository;
import com.nexusflow.security.application.ResourceSecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceSecurityServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private ResourceSecurityService resourceSecurityService;

    @Test
    @DisplayName("Should return true when authenticated user matches order customer email")
    void shouldReturnTrueWhenUserMatchesOrderCustomerEmail() {
        UUID orderId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .email("tony@stark.com")
                .name("Tony Stark")
                .build();

        Order order = Order.builder()
                .id(orderId)
                .customer(customer)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Authentication auth = new UsernamePasswordAuthenticationToken("tony@stark.com", "credentials", Collections.emptyList());

        boolean isOwner = resourceSecurityService.isOrderOwner(orderId, auth);
        assertThat(isOwner).isTrue();
    }

    @Test
    @DisplayName("Should return false when authenticated user does not match order customer")
    void shouldReturnFalseWhenUserDoesNotMatchOrderCustomer() {
        UUID orderId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .email("tony@stark.com")
                .build();

        Order order = Order.builder()
                .id(orderId)
                .customer(customer)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Authentication auth = new UsernamePasswordAuthenticationToken("attacker@evil.com", "credentials", Collections.emptyList());

        boolean isOwner = resourceSecurityService.isOrderOwner(orderId, auth);
        assertThat(isOwner).isFalse();
    }
}
