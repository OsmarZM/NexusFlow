package com.nexusflow.payment.api;

import com.nexusflow.payment.application.PaymentService;
import com.nexusflow.payment.application.dto.PaymentRequestDTO;
import com.nexusflow.payment.application.dto.PaymentResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing and transactional outbox events")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping({"", "/process"})
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @securityService.isOrderOwner(#request.orderId(), authentication)")
    @Operation(summary = "Process or simulate payment for an order (Finance, Admin or Order Owner)")
    public ResponseEntity<PaymentResponseDTO> processPayment(
            @Valid @RequestBody PaymentRequestDTO request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        PaymentResponseDTO response = paymentService.processPayment(request, corrId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @securityService.isOrderOwner(#orderId, authentication)")
    @Operation(summary = "Get all payment records for an order")
    public ResponseEntity<List<PaymentResponseDTO>> getPaymentsForOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsForOrder(orderId));
    }
}
