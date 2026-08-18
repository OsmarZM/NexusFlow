package com.nexusflow.customer.application;

import com.nexusflow.customer.application.dto.CustomerRequestDTO;
import com.nexusflow.customer.application.dto.CustomerResponseDTO;
import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.customer.domain.CustomerStatus;
import com.nexusflow.shared.exception.BusinessException;
import com.nexusflow.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerResponseDTO createCustomer(CustomerRequestDTO request) {
        log.info("Registering new customer with email: {} and document: {}", request.email(), request.document());

        if (customerRepository.existsByEmail(request.email())) {
            throw new BusinessException("A customer with email '" + request.email() + "' already exists.");
        }
        if (customerRepository.existsByDocument(request.document())) {
            throw new BusinessException("A customer with document '" + request.document() + "' already exists.");
        }

        Customer customer = Customer.builder()
                .name(request.name().trim())
                .email(request.email().trim().toLowerCase())
                .document(request.document().trim())
                .status(request.status() != null ? request.status() : CustomerStatus.ACTIVE)
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("Customer successfully created with ID: {}", saved.getId());
        return CustomerResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponseDTO getCustomerById(UUID id) {
        return customerRepository.findById(id)
                .map(CustomerResponseDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponseDTO> listCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(CustomerResponseDTO::fromEntity);
    }

    @Transactional
    public CustomerResponseDTO updateCustomer(UUID id, CustomerRequestDTO request) {
        log.info("Updating customer ID: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + id));

        if (!customer.getEmail().equalsIgnoreCase(request.email().trim()) && customerRepository.existsByEmail(request.email().trim())) {
            throw new BusinessException("Email '" + request.email() + "' is already in use by another customer.");
        }

        if (!customer.getDocument().equalsIgnoreCase(request.document().trim()) && customerRepository.existsByDocument(request.document().trim())) {
            throw new BusinessException("Document '" + request.document() + "' is already in use by another customer.");
        }

        customer.setName(request.name().trim());
        customer.setEmail(request.email().trim().toLowerCase());
        customer.setDocument(request.document().trim());
        if (request.status() != null) {
            customer.setStatus(request.status());
        }

        Customer updated = customerRepository.save(customer);
        return CustomerResponseDTO.fromEntity(updated);
    }

    @Transactional
    public void deleteCustomer(UUID id) {
        log.info("Deactivating/Deleting customer ID: {}", id);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + id));
        customer.setStatus(CustomerStatus.INACTIVE);
        customerRepository.save(customer);
    }
}
