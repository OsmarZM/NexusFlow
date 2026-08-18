package com.nexusflow.integration;

import com.nexusflow.customer.application.CustomerService;
import com.nexusflow.customer.application.dto.CustomerRequestDTO;
import com.nexusflow.customer.application.dto.CustomerResponseDTO;
import com.nexusflow.customer.domain.CustomerStatus;
import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.InventoryResponseDTO;
import com.nexusflow.order.application.OrderService;
import com.nexusflow.order.application.dto.CreateOrderRequestDTO;
import com.nexusflow.order.application.dto.OrderItemRequestDTO;
import com.nexusflow.order.application.dto.OrderResponseDTO;
import com.nexusflow.order.domain.OrderStatus;
import com.nexusflow.payment.application.PaymentService;
import com.nexusflow.payment.application.dto.PaymentRequestDTO;
import com.nexusflow.payment.application.dto.PaymentResponseDTO;
import com.nexusflow.payment.domain.PaymentStatus;
import com.nexusflow.product.application.ProductService;
import com.nexusflow.product.application.dto.ProductRequestDTO;
import com.nexusflow.product.domain.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSagaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Test
    @DisplayName("E2E Saga: Should execute full happy path (Order -> Reserve -> Pay -> Confirm Stock)")
    void shouldExecuteFullOrderHappyPath() {
        // 1. Create Customer
        CustomerResponseDTO customer = customerService.createCustomer(
                new CustomerRequestDTO("Bruce Wayne", "bruce.integration@waynecorp.com", "88899900011", CustomerStatus.ACTIVE)
        );

        // 2. Create Product with initial stock 10
        String sku = "SSD-NVME-4TB";
        productService.createProduct(
                new ProductRequestDTO(sku, "Samsung 990 Pro 4TB", "Fast PCIe 4.0 SSD", BigDecimal.valueOf(1899.00), ProductStatus.ACTIVE, 10)
        );

        // 3. Create Order for 3 units
        CreateOrderRequestDTO orderRequest = new CreateOrderRequestDTO(
                customer.id(),
                List.of(new OrderItemRequestDTO(sku, 3))
        );
        OrderResponseDTO order = orderService.createOrder(orderRequest);

        assertThat(order.id()).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.WAITING_PAYMENT);
        assertThat(order.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(5697.00));

        // Verify intermediate stock reservation
        InventoryResponseDTO invAfterOrder = inventoryService.getInventoryBySku(sku);
        assertThat(invAfterOrder.physicalQuantity()).isEqualTo(10);
        assertThat(invAfterOrder.reservedQuantity()).isEqualTo(3);
        assertThat(invAfterOrder.availableQuantity()).isEqualTo(7);

        // 4. Process Payment (Approved)
        PaymentResponseDTO payment = paymentService.processPayment(
                new PaymentRequestDTO(order.id(), customer.id(), order.totalAmount(), false),
                "corr-saga-happy"
        );
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);

        // 5. Confirm Order & Stock Deduction
        OrderResponseDTO paidOrder = orderService.markAsPaid(order.id());
        assertThat(paidOrder.status()).isEqualTo(OrderStatus.PAID);

        // Verify final physical stock deducted
        InventoryResponseDTO finalInv = inventoryService.getInventoryBySku(sku);
        assertThat(finalInv.physicalQuantity()).isEqualTo(7);
        assertThat(finalInv.reservedQuantity()).isEqualTo(0);
        assertThat(finalInv.availableQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("E2E Saga Compensation: Payment failure should trigger reservation release and order cancellation")
    void shouldExecuteSagaCompensatingTransactionOnPaymentFailure() {
        // 1. Create Customer
        CustomerResponseDTO customer = customerService.createCustomer(
                new CustomerRequestDTO("Peter Parker", "peter.parker@dailybugle.com", "77766655544", CustomerStatus.ACTIVE)
        );

        // 2. Create Product with initial stock 5
        String sku = "CAMERA-SONY-A7IV";
        productService.createProduct(
                new ProductRequestDTO(sku, "Sony Alpha 7 IV", "Full-frame Camera", BigDecimal.valueOf(14000.00), ProductStatus.ACTIVE, 5)
        );

        // 3. Create Order for 2 units
        OrderResponseDTO order = orderService.createOrder(
                new CreateOrderRequestDTO(customer.id(), List.of(new OrderItemRequestDTO(sku, 2)))
        );

        InventoryResponseDTO invBeforePay = inventoryService.getInventoryBySku(sku);
        assertThat(invBeforePay.reservedQuantity()).isEqualTo(2);
        assertThat(invBeforePay.availableQuantity()).isEqualTo(3);

        // 4. Simulate Payment Rejection
        PaymentResponseDTO payment = paymentService.processPayment(
                new PaymentRequestDTO(order.id(), customer.id(), order.totalAmount(), true),
                "corr-saga-fail"
        );
        assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);

        // 5. Compensating Transaction: Cancel order and release stock
        OrderResponseDTO cancelledOrder = orderService.cancelOrder(order.id(), "Payment rejected by gateway");
        assertThat(cancelledOrder.status()).isEqualTo(OrderStatus.CANCELLED);

        // Verify stock is fully returned to available pool
        InventoryResponseDTO invAfterCancel = inventoryService.getInventoryBySku(sku);
        assertThat(invAfterCancel.physicalQuantity()).isEqualTo(5);
        assertThat(invAfterCancel.reservedQuantity()).isEqualTo(0);
        assertThat(invAfterCancel.availableQuantity()).isEqualTo(5);
    }
}
