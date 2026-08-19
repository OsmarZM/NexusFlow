package com.nexusflow.order.api;

import com.nexusflow.order.application.OrderService;
import com.nexusflow.order.application.dto.CreateOrderRequestDTO;
import com.nexusflow.order.application.dto.OrderResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order processing and orchestration endpoints")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.isCustomerOwner(#request.customerId(), authentication)")
    @Operation(summary = "Create a new order with automatic inventory reservation")
    public ResponseEntity<OrderResponseDTO> createOrder(@Valid @RequestBody CreateOrderRequestDTO request) {
        OrderResponseDTO response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPERATOR', 'FINANCE') or @securityService.isOrderOwner(#id, authentication)")
    @Operation(summary = "Get order details by ID (Admin, Operator or Order Owner)")
    public ResponseEntity<OrderResponseDTO> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_OPERATOR', 'FINANCE')")
    @Operation(summary = "List all paginated orders (Restricted to internal roles)")
    public ResponseEntity<Page<OrderResponseDTO>> listOrders(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.listOrders(pageable));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isCustomerOwner(#customerId, authentication)")
    @Operation(summary = "List orders by customer ID (Admin or Customer Owner)")
    public ResponseEntity<Page<OrderResponseDTO>> listOrdersByCustomer(
            @PathVariable UUID customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.listOrdersByCustomer(customerId, pageable));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isOrderOwner(#id, authentication)")
    @Operation(summary = "Cancel an order and release reserved stock (Admin or Order Owner)")
    public ResponseEntity<OrderResponseDTO> cancelOrder(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "Customer requested cancellation") String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(id, reason));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Confirm payment and deduct physical stock (Finance/Admin only)")
    public ResponseEntity<OrderResponseDTO> markAsPaid(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.markAsPaid(id));
    }
}
