package com.nexusflow.order.application;

import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.customer.domain.CustomerStatus;
import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.StockReservationRequestDTO;
import com.nexusflow.order.application.dto.CreateOrderRequestDTO;
import com.nexusflow.order.application.dto.OrderItemRequestDTO;
import com.nexusflow.order.application.dto.OrderResponseDTO;
import com.nexusflow.order.domain.Order;
import com.nexusflow.order.domain.OrderItem;
import com.nexusflow.order.domain.OrderRepository;
import com.nexusflow.order.domain.OrderStatus;
import com.nexusflow.product.domain.Product;
import com.nexusflow.product.domain.ProductRepository;
import com.nexusflow.product.domain.ProductStatus;
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
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Transactional
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request) {
        log.info("Initiating order creation for customer ID: {}", request.customerId());

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + request.customerId()));

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new BusinessException("Customer account is not active. Current status: " + customer.getStatus());
        }

        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .customer(customer)
                .status(OrderStatus.CREATED)
                .build();

        for (OrderItemRequestDTO itemRequest : request.items()) {
            String sku = itemRequest.sku().trim().toUpperCase();
            Product product = productRepository.findBySku(sku)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with SKU: " + sku));

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException("Product with SKU '" + sku + "' is inactive and cannot be ordered.");
            }

            // Reserve stock with Pessimistic Lock to guarantee zero race condition overselling
            inventoryService.reserveStock(
                    new StockReservationRequestDTO(orderId, sku, itemRequest.quantity(), 30),
                    true
            );

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .sku(sku)
                    .unitPrice(product.getPrice())
                    .quantity(itemRequest.quantity())
                    .build();
            orderItem.calculateSubtotal();

            order.addItem(orderItem);
        }

        order.setStatus(OrderStatus.WAITING_PAYMENT);
        Order savedOrder = orderRepository.save(order);
        log.info("Order successfully created with ID: {} and total: {}", savedOrder.getId(), savedOrder.getTotalAmount());

        return OrderResponseDTO.fromEntity(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderById(UUID id) {
        return orderRepository.findByIdWithDetails(id)
                .map(OrderResponseDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> listOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(OrderResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> listOrdersByCustomer(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable)
                .map(OrderResponseDTO::fromEntity);
    }

    @Transactional
    public OrderResponseDTO cancelOrder(UUID id, String reason) {
        log.info("Cancelling order ID: {} - Reason: {}", id, reason);

        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BusinessException("Cannot cancel an order in state: " + order.getStatus());
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponseDTO.fromEntity(order);
        }

        // Release stock reservations (Saga compensation)
        for (OrderItem item : order.getItems()) {
            inventoryService.releaseReservation(order.getId(), item.getSku());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order updated = orderRepository.save(order);
        log.info("Order ID: {} cancelled and reserved stock released.", id);
        return OrderResponseDTO.fromEntity(updated);
    }

    @Transactional
    public OrderResponseDTO markAsPaid(UUID id) {
        log.info("Marking order ID: {} as PAID", id);

        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));

        if (order.getStatus() != OrderStatus.WAITING_PAYMENT && order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException("Order cannot be marked as PAID from status: " + order.getStatus());
        }

        // Confirm stock deduction
        for (OrderItem item : order.getItems()) {
            inventoryService.confirmReservation(order.getId(), item.getSku());
        }

        order.setStatus(OrderStatus.PAID);
        Order updated = orderRepository.save(order);
        log.info("Order ID: {} successfully paid and confirmed.", id);
        return OrderResponseDTO.fromEntity(updated);
    }
}
