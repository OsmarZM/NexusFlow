package com.nexusflow.unit.security;

import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.order.domain.Order;
import com.nexusflow.order.domain.OrderRepository;
import com.nexusflow.security.application.ResourceSecurityService;
import com.nexusflow.security.domain.User;
import com.nexusflow.security.domain.UserRepository;
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

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ResourceSecurityService resourceSecurityService;

    @Test
    @DisplayName("Should return true when User has explicit customerId matching target Customer")
    void shouldReturnTrueWhenUserHasExplicitCustomerId() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        User user = User.builder()
                .id(UUID.randomUUID())
                .username("batman")
                .email("bruce@wayne.com")
                .customerId(customerId)
                .build();

        Order order = Order.builder()
                .id(orderId)
                .customer(Customer.builder().id(customerId).build())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("batman")).thenReturn(Optional.of(user));

        Authentication auth = new UsernamePasswordAuthenticationToken("batman", "pwd", Collections.emptyList());

        boolean isOwner = resourceSecurityService.isOrderOwner(orderId, auth);
        assertThat(isOwner).isTrue();
    }

    @Test
    @DisplayName("Should return true via fallback when authenticated username matches customer email")
    void shouldReturnTrueWhenUsernameMatchesCustomerEmailFallback() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Customer customer = Customer.builder()
                .id(customerId)
                .email("tony@stark.com")
                .name("Tony Stark")
                .build();

        Order order = Order.builder()
                .id(orderId)
                .customer(customer)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("tony@stark.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("tony@stark.com")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        Authentication auth = new UsernamePasswordAuthenticationToken("tony@stark.com", "credentials", Collections.emptyList());

        boolean isOwner = resourceSecurityService.isOrderOwner(orderId, auth);
        assertThat(isOwner).isTrue();
    }

    @Test
    @DisplayName("Should return false when authenticated user does not match order customer")
    void shouldReturnFalseWhenUserDoesNotMatchOrderCustomer() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Customer customer = Customer.builder()
                .id(customerId)
                .email("tony@stark.com")
                .name("Tony Stark")
                .build();

        Order order = Order.builder()
                .id(orderId)
                .customer(customer)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findByUsername("attacker")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("attacker")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        Authentication auth = new UsernamePasswordAuthenticationToken("attacker", "credentials", Collections.emptyList());

        boolean isOwner = resourceSecurityService.isOrderOwner(orderId, auth);
        assertThat(isOwner).isFalse();
    }
}
