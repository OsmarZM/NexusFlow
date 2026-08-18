package com.nexusflow.unit.order;

import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.customer.domain.CustomerStatus;
import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.StockReservationResponseDTO;
import com.nexusflow.inventory.domain.ReservationStatus;
import com.nexusflow.order.application.OrderService;
import com.nexusflow.order.application.dto.CreateOrderRequestDTO;
import com.nexusflow.order.application.dto.OrderItemRequestDTO;
import com.nexusflow.order.application.dto.OrderResponseDTO;
import com.nexusflow.order.domain.Order;
import com.nexusflow.order.domain.OrderRepository;
import com.nexusflow.order.domain.OrderStatus;
import com.nexusflow.product.domain.Product;
import com.nexusflow.product.domain.ProductRepository;
import com.nexusflow.product.domain.ProductStatus;
import com.nexusflow.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private com.nexusflow.messaging.producer.OrderEventProducer orderEventProducer;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("Should create order and reserve stock for all items")
    void shouldCreateOrderAndReserveStock() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(customerId)
                .name("Tony Stark")
                .email("tony@stark.com")
                .status(CustomerStatus.ACTIVE)
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .sku("GPU-RTX5070")
                .name("RTX 5070")
                .price(BigDecimal.valueOf(3500.00))
                .status(ProductStatus.ACTIVE)
                .build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findBySku("GPU-RTX5070")).thenReturn(Optional.of(product));
        when(inventoryService.reserveStock(any(), eq(true))).thenReturn(
                new StockReservationResponseDTO(UUID.randomUUID(), UUID.randomUUID(), "GPU-RTX5070", 2, ReservationStatus.RESERVED, null, null)
        );
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequestDTO request = new CreateOrderRequestDTO(
                customerId,
                List.of(new OrderItemRequestDTO("GPU-RTX5070", 2))
        );

        OrderResponseDTO response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.WAITING_PAYMENT);
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(7000.00));
        assertThat(response.items()).hasSize(1);
        verify(inventoryService, times(1)).reserveStock(any(), eq(true));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should throw BusinessException when customer is inactive")
    void shouldThrowExceptionWhenCustomerInactive() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(customerId)
                .name("Tony Stark")
                .email("tony@stark.com")
                .status(CustomerStatus.INACTIVE)
                .build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        CreateOrderRequestDTO request = new CreateOrderRequestDTO(
                customerId,
                List.of(new OrderItemRequestDTO("GPU-RTX5070", 1))
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Customer account is not active");

        verify(orderRepository, never()).save(any(Order.class));
    }
}
