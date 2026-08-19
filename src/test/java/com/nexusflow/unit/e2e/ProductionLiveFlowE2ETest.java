package com.nexusflow.unit.e2e;

import com.nexusflow.customer.application.CustomerService;
import com.nexusflow.customer.application.dto.CustomerRequestDTO;
import com.nexusflow.customer.application.dto.CustomerResponseDTO;
import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerRepository;
import com.nexusflow.customer.domain.CustomerStatus;
import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.InventoryResponseDTO;
import com.nexusflow.inventory.domain.Inventory;
import com.nexusflow.inventory.domain.InventoryRepository;
import com.nexusflow.inventory.domain.InventoryReservationRepository;
import com.nexusflow.messaging.producer.OrderEventProducer;
import com.nexusflow.order.application.OrderService;
import com.nexusflow.order.application.dto.CreateOrderRequestDTO;
import com.nexusflow.order.application.dto.OrderItemRequestDTO;
import com.nexusflow.order.application.dto.OrderResponseDTO;
import com.nexusflow.order.domain.Order;
import com.nexusflow.order.domain.OrderRepository;
import com.nexusflow.order.domain.OrderStatus;
import com.nexusflow.payment.application.PaymentService;
import com.nexusflow.payment.application.dto.PaymentRequestDTO;
import com.nexusflow.payment.application.dto.PaymentResponseDTO;
import com.nexusflow.payment.domain.Payment;
import com.nexusflow.payment.domain.PaymentRepository;
import com.nexusflow.payment.domain.PaymentStatus;
import com.nexusflow.product.application.ProductService;
import com.nexusflow.product.application.dto.ProductRequestDTO;
import com.nexusflow.product.application.dto.ProductResponseDTO;
import com.nexusflow.product.domain.Product;
import com.nexusflow.product.domain.ProductRepository;
import com.nexusflow.product.domain.ProductStatus;
import com.nexusflow.ratelimit.RateLimiterService;
import com.nexusflow.security.application.AuthService;
import com.nexusflow.security.application.JwtService;
import com.nexusflow.security.application.dto.AuthRequestDTO;
import com.nexusflow.security.application.dto.AuthResponseDTO;
import com.nexusflow.security.domain.Role;
import com.nexusflow.security.domain.User;
import com.nexusflow.security.domain.UserRepository;
import com.nexusflow.shared.exception.InsufficientStockException;
import com.nexusflow.shared.metrics.BusinessMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductionLiveFlowE2ETest {

    // Repositories & External Services Mocks
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private OrderEventProducer orderEventProducer;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Mock private com.nexusflow.outbox.application.OutboxService outboxService;

    // Real Application Services
    private JwtService jwtService;
    private AuthService authService;
    private InventoryService inventoryService;
    private ProductService productService;
    private OrderService orderService;
    private PaymentService paymentService;
    private RateLimiterService rateLimiterService;
    private BusinessMetricsService businessMetricsService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        businessMetricsService = new BusinessMetricsService(meterRegistry);

        jwtService = new JwtService(
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                120L,
                "nexusflow-auth-service"
        );

        authService = new AuthService(userRepository, passwordEncoder, jwtService, authenticationManager);
        inventoryService = new InventoryService(inventoryRepository, reservationRepository);
        productService = new ProductService(productRepository, inventoryService);
        orderService = new OrderService(orderRepository, customerRepository, productRepository, inventoryService, outboxService, businessMetricsService);
        ReflectionTestUtils.setField(orderService, "orderCreatedTopic", "orders.created");
        ReflectionTestUtils.setField(orderService, "orderCancelledTopic", "orders.cancelled");
        paymentService = new PaymentService(paymentRepository, orderRepository, outboxService);
        ReflectionTestUtils.setField(paymentService, "paymentProcessedTopic", "payments.processed");

        rateLimiterService = new RateLimiterService(redisTemplate);
        ReflectionTestUtils.setField(rateLimiterService, "enabled", true);
        ReflectionTestUtils.setField(rateLimiterService, "defaultCapacity", 100);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("SEÇÃO 1 & 2: Segurança & RBAC - Login de Admin e Emissão de Token JWT válido")
    void testSection2_AuthenticationAndRbac() {
        User adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .email("admin@nexusflow.io")
                .password("$2a$10$hashedAdminPassword")
                .fullName("System Administrator")
                .roles(Set.of(Role.ADMIN, Role.WAREHOUSE_OPERATOR))
                .enabled(true)
                .build();

        when(userRepository.findByUsernameOrEmail("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("Admin@123456", adminUser.getPassword())).thenReturn(true);

        AuthResponseDTO authResponse = authService.login(new AuthRequestDTO("admin", "Admin@123456"));

        assertThat(authResponse.accessToken()).isNotNull();
        assertThat(authResponse.username()).isEqualTo("admin");
        assertThat(authResponse.roles()).contains("ADMIN", "WAREHOUSE_OPERATOR");

        // Validar token com JwtService
        String usernameFromToken = jwtService.extractUsername(authResponse.accessToken());
        assertThat(usernameFromToken).isEqualTo("admin");
        assertThat(jwtService.isTokenValid(authResponse.accessToken(), adminUser)).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("SEÇÃO 3: Catálogo & Estoque - Criação de Produto e Inicialização Automática de Estoque")
    void testSection3_ProductAndInventoryCreation() {
        String sku = "GPU-RTX-5090";
        ProductRequestDTO request = new ProductRequestDTO(
                sku, "NVIDIA GeForce RTX 5090", "24GB GDDR7 512-bit", BigDecimal.valueOf(18999.00), ProductStatus.ACTIVE, 10
        );

        when(productRepository.existsBySku(sku)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory inv = invocation.getArgument(0);
            inv.setId(UUID.randomUUID());
            return inv;
        });

        ProductResponseDTO response = productService.createProduct(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.sku()).isEqualTo(sku);
        assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(18999.00));

        // Verificar se inicialização de estoque foi invocada
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("SEÇÃO 4: Concorrência & Estoque - Bloqueio de Overselling ao ultrapassar quantidade disponível")
    void testSection4_OversellingPrevention() {
        UUID customerId = UUID.randomUUID();
        String sku = "MONITOR-OLED-49";

        Customer customer = Customer.builder().id(customerId).name("Tony Stark").status(CustomerStatus.ACTIVE).build();
        Product product = Product.builder().id(UUID.randomUUID()).sku(sku).name("Samsung Odyssey OLED").price(BigDecimal.valueOf(8000)).status(ProductStatus.ACTIVE).build();
        Inventory inventory = Inventory.builder().id(UUID.randomUUID()).sku(sku).physicalQuantity(2).reservedQuantity(0).warehouse("DEFAULT_WH").build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findBySku(sku)).thenReturn(Optional.of(product));
        when(inventoryRepository.findBySkuWithPessimisticLock(sku)).thenReturn(Optional.of(inventory));

        // Tentar pedir 5 unidades quando só existem 2 disponíveis
        CreateOrderRequestDTO orderRequest = new CreateOrderRequestDTO(
                customerId,
                List.of(new OrderItemRequestDTO(sku, 5))
        );

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("SEÇÃO 5: Orquestração de Saga (Happy Path) - Pedido -> Reserva -> Pagamento Aprovado -> PAID")
    void testSection5_SagaHappyPath() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String sku = "IPHONE-16-PRO";

        Customer customer = Customer.builder().id(customerId).name("Bruce Wayne").status(CustomerStatus.ACTIVE).build();
        Product product = Product.builder().id(UUID.randomUUID()).sku(sku).name("iPhone 16 Pro Max").price(BigDecimal.valueOf(9000.00)).status(ProductStatus.ACTIVE).build();
        Inventory inventory = Inventory.builder().id(UUID.randomUUID()).sku(sku).physicalQuantity(10).reservedQuantity(0).warehouse("DEFAULT_WH").build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findBySku(sku)).thenReturn(Optional.of(product));
        when(inventoryRepository.findBySkuWithPessimisticLock(sku)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(com.nexusflow.inventory.domain.InventoryReservation.class))).thenAnswer(inv -> {
            com.nexusflow.inventory.domain.InventoryReservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(orderId);
            return o;
        });

        // 1. Criar Pedido
        OrderResponseDTO createdOrder = orderService.createOrder(
                new CreateOrderRequestDTO(customerId, List.of(new OrderItemRequestDTO(sku, 2)))
        );

        assertThat(createdOrder.status()).isEqualTo(OrderStatus.WAITING_PAYMENT);
        assertThat(createdOrder.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(18000.00));

        // 2. Processar Pagamento Aprovado
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(Order.builder()
                .id(orderId)
                .customer(customer)
                .totalAmount(BigDecimal.valueOf(18000.00))
                .status(OrderStatus.WAITING_PAYMENT)
                .items(Collections.emptyList())
                .build()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentResponseDTO payment = paymentService.processPayment(
                new PaymentRequestDTO(orderId, customerId, BigDecimal.valueOf(18000.00), false),
                "corr-e2e-happy"
        );

        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("SEÇÃO 5: Orquestração de Saga (Compensação) - Pagamento Rejeitado -> Compensação da Saga")
    void testSection5_SagaCompensatingTransaction() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Customer customer = Customer.builder().id(customerId).name("Tony Stark").email("tony@stark.com").build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(Order.builder()
                .id(orderId)
                .customer(customer)
                .totalAmount(BigDecimal.valueOf(5000.00))
                .status(OrderStatus.WAITING_PAYMENT)
                .items(Collections.emptyList())
                .build()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        // Simular falha no gateway de pagamento
        PaymentResponseDTO payment = paymentService.processPayment(
                new PaymentRequestDTO(orderId, customerId, BigDecimal.valueOf(5000.00), true),
                "corr-e2e-fail"
        );

        assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.failureReason()).isNotNull();

        // Validar publicação de evento de cancelamento para compensação via Outbox
        verify(outboxService).saveEvent(anyString(), any(), anyString(), any());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("SEÇÃO 6: Rate Limiting Distribuído - Sliding Window Token Bucket com bloqueio após limite")
    void testSection6_DistributedRateLimiting() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Caso 1: Dentro do limite
        when(valueOperations.increment(anyString())).thenReturn(50L);
        RateLimiterService.RateLimitResult okResult = rateLimiterService.tryConsume("user:tony", 100);
        assertThat(okResult.allowed()).isTrue();
        assertThat(okResult.remaining()).isEqualTo(50);

        // Caso 2: Excedeu o limite (HTTP 429)
        when(valueOperations.increment(anyString())).thenReturn(101L);
        RateLimiterService.RateLimitResult blockedResult = rateLimiterService.tryConsume("user:tony", 100);
        assertThat(blockedResult.allowed()).isFalse();
        assertThat(blockedResult.remaining()).isEqualTo(0);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("SEÇÃO 7: Observabilidade & Métricas de Negócio - Contadores registrados no Micrometer")
    void testSection7_ObservabilityMetrics() {
        businessMetricsService.incrementOrdersCreated();
        businessMetricsService.incrementPaymentsApproved();
        businessMetricsService.incrementPaymentsFailed();
        businessMetricsService.incrementRateLimitExceeded();
        businessMetricsService.recordOrderProcessingDuration(150);

        double ordersCount = meterRegistry.get("nexusflow.orders.created.total").counter().count();
        double paymentsApproved = meterRegistry.get("nexusflow.payments.approved.total").counter().count();
        double paymentsFailed = meterRegistry.get("nexusflow.payments.failed.total").counter().count();
        double rateLimitsExceeded = meterRegistry.get("nexusflow.ratelimit.exceeded.total").counter().count();

        assertThat(ordersCount).isEqualTo(1.0);
        assertThat(paymentsApproved).isEqualTo(1.0);
        assertThat(paymentsFailed).isEqualTo(1.0);
        assertThat(rateLimitsExceeded).isEqualTo(1.0);
    }
}
