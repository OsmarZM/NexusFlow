package com.nexusflow.unit.customer;

import com.nexusflow.customer.application.CustomerService;
import com.nexusflow.customer.application.dto.CustomerRequestDTO;
import com.nexusflow.customer.application.dto.CustomerResponseDTO;
import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.customer.domain.CustomerStatus;
import com.nexusflow.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    @DisplayName("Should create customer successfully when email and document are unique")
    void shouldCreateCustomerSuccessfully() {
        CustomerRequestDTO request = new CustomerRequestDTO("Bruce Wayne", "bruce@waynecorp.com", "12345678900", CustomerStatus.ACTIVE);
        UUID customerId = UUID.randomUUID();

        when(customerRepository.existsByEmail(request.email())).thenReturn(false);
        when(customerRepository.existsByDocument(request.document())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            c.setId(customerId);
            c.setCreatedAt(OffsetDateTime.now());
            c.setUpdatedAt(OffsetDateTime.now());
            return c;
        });

        CustomerResponseDTO response = customerService.createCustomer(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(customerId);
        assertThat(response.email()).isEqualTo("bruce@waynecorp.com");
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw BusinessException when email already exists")
    void shouldThrowExceptionWhenEmailExists() {
        CustomerRequestDTO request = new CustomerRequestDTO("Bruce Wayne", "bruce@waynecorp.com", "12345678900", CustomerStatus.ACTIVE);

        when(customerRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        verify(customerRepository, never()).save(any(Customer.class));
    }
}
