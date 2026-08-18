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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order processing and orchestration endpoints")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a new order with automatic inventory reservation")
    public ResponseEntity<OrderResponseDTO> createOrder(@Valid @RequestBody CreateOrderRequestDTO request) {
        OrderResponseDTO response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details by ID")
    public ResponseEntity<OrderResponseDTO> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping
    @Operation(summary = "List paginated orders")
    public ResponseEntity<Page<OrderResponseDTO>> listOrders(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.listOrders(pageable));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "List orders by customer ID")
    public ResponseEntity<Page<OrderResponseDTO>> listOrdersByCustomer(
            @PathVariable UUID customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.listOrdersByCustomer(customerId, pageable));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order and release reserved stock")
    public ResponseEntity<OrderResponseDTO> cancelOrder(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "Customer requested cancellation") String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(id, reason));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Simulate payment confirmation and deduct physical stock")
    public ResponseEntity<OrderResponseDTO> markAsPaid(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.markAsPaid(id));
    }
}
