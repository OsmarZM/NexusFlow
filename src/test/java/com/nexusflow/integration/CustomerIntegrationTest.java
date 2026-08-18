package com.nexusflow.integration;

import com.nexusflow.customer.application.CustomerService;
import com.nexusflow.customer.application.dto.CustomerRequestDTO;
import com.nexusflow.customer.application.dto.CustomerResponseDTO;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.customer.domain.CustomerStatus;
import com.nexusflow.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @DisplayName("E2E: Should create, persist and retrieve customer from real PostgreSQL instance")
    void shouldCreateAndPersistCustomer() {
        CustomerRequestDTO request = new CustomerRequestDTO(
                "Diana Prince",
                "diana@themyscira.com",
                "99887766554",
                CustomerStatus.ACTIVE
        );

        CustomerResponseDTO response = customerService.createCustomer(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isEqualTo("diana@themyscira.com");

        var persisted = customerRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Diana Prince");
        assertThat(persisted.get().getDocument()).isEqualTo("99887766554");
    }

    @Test
    @DisplayName("E2E: Should reject duplicate email on real database constraints")
    void shouldRejectDuplicateEmail() {
        CustomerRequestDTO request1 = new CustomerRequestDTO("Clark Kent", "clark@dailyplanet.com", "11122233344", CustomerStatus.ACTIVE);
        CustomerRequestDTO request2 = new CustomerRequestDTO("Superman", "clark@dailyplanet.com", "55566677788", CustomerStatus.ACTIVE);

        customerService.createCustomer(request1);

        assertThatThrownBy(() -> customerService.createCustomer(request2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }
}
