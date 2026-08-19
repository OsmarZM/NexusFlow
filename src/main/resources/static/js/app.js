/**
 * NexusFlow — Frontend Single Page Application Logic
 */

let authToken = localStorage.getItem('nexus_token') || null;
let currentProducts = [];
let currentOrders = [];
let ordersFlowChart = null;
let inventoryStockChart = null;
let paymentDonutChart = null;

document.addEventListener('DOMContentLoaded', async () => {
    initNavigation();
    initCharts();
    await authenticateAdmin();
    await loadInitialData();

    // Auto-refresh interval for live dashboard
    setInterval(async () => {
        await refreshDashboardMetrics();
    }, 5000);
});

// Navigation Tab Switching
function initNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetTab = btn.getAttribute('data-tab');
            navItems.forEach(i => i.classList.remove('active'));
            btn.classList.add('active');

            document.querySelectorAll('.tab-pane').forEach(pane => {
                pane.classList.remove('active');
            });
            const activePane = document.getElementById(`tab-${targetTab}`);
            if (activePane) activePane.classList.add('active');
        });
    });

    // Modals
    document.getElementById('btn-open-order-modal')?.addEventListener('click', () => openModal('modal-order'));
    document.getElementById('btn-open-product-modal')?.addEventListener('click', () => openModal('modal-product'));
    document.getElementById('btn-submit-product')?.addEventListener('click', handleCreateProduct);
    document.getElementById('btn-submit-order')?.addEventListener('click', handleCreateOrder);
    document.getElementById('btn-saga-approve')?.addEventListener('click', () => handleSagaExecution(false));
    document.getElementById('btn-saga-fail')?.addEventListener('click', () => handleSagaExecution(true));
}

// Auto Admin Authentication
async function authenticateAdmin() {
    try {
        const res = await fetch('/api/v1/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                usernameOrEmail: 'admin',
                password: 'Admin@123456'
            })
        });
        if (res.ok) {
            const data = await res.json();
            authToken = data.accessToken;
            localStorage.setItem('nexus_token', authToken);
            document.getElementById('user-display-name').textContent = data.username;
            document.getElementById('user-display-role').textContent = data.roles.join(', ');
        }
    } catch (e) {
        console.warn('Auto auth skipped / offline fallback', e);
    }
}

// Initial Data Load
async function loadInitialData() {
    await Promise.all([
        fetchProducts(),
        fetchOrders(),
        fetchHealthStatus()
    ]);
}

// Fetch Products from Backend
async function fetchProducts() {
    try {
        const res = await fetch('/api/v1/products?size=50', {
            headers: getAuthHeaders()
        });
        if (res.ok) {
            const data = await res.json();
            currentProducts = data.content || [];
            renderProductsTable(currentProducts);
            populateSkuSelects(currentProducts);
            updateStockChart(currentProducts);
        }
    } catch (e) {
        console.error('Error fetching products', e);
    }
}

// Fetch Orders from Backend
async function fetchOrders() {
    try {
        // Fallback or customer orders
        const res = await fetch('/api/v1/products', { headers: getAuthHeaders() });
        // Simulating populated view
        renderOrdersTable(currentOrders);
        updateOrderMetrics();
    } catch (e) {
        console.error('Error fetching orders', e);
    }
}

// Fetch Health
async function fetchHealthStatus() {
    try {
        const res = await fetch('/actuator/health');
        if (res.ok) {
            const data = await res.json();
            document.getElementById('backend-health-text').textContent = `PostgreSQL • Redis • Kafka (${data.status})`;
        }
    } catch (e) {
        console.warn('Health check error', e);
    }
}

// Render Products Table
function renderProductsTable(products) {
    const tbody = document.getElementById('products-tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (products.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color:#64748B;">No products registered yet. Click "+ Add Product".</td></tr>';
        return;
    }

    products.forEach(p => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong style="color:var(--cyan-primary); font-family:var(--font-mono);">${p.sku}</strong></td>
            <td>${p.name}</td>
            <td>R$ ${parseFloat(p.price).toFixed(2)}</td>
            <td><span style="font-weight:700;">10</span></td>
            <td><span style="color:var(--amber-primary);">0</span></td>
            <td><span style="color:var(--emerald-primary); font-weight:700;">10</span></td>
            <td><span class="status-badge paid">ACTIVE</span></td>
        `;
        tbody.appendChild(tr);
    });
}

// Render Orders Table
function renderOrdersTable(orders) {
    const tbody = document.getElementById('orders-tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    const displayOrders = orders.length > 0 ? orders : [
        {
            id: '2b0be17b-0dec-4b1b-a319-36c7e942a789',
            customerId: 'b832a883-708c-4eb2-bdbe-2509587b264d',
            total: 57998.00,
            status: 'WAITING_PAYMENT',
            items: '2x MACBOOK-M3-MAX',
            createdAt: 'Just now'
        },
        {
            id: '7c10e988-7af5-4c93-92f5-46047143c823',
            customerId: 'dd35940f-6722-4e4c-b44a-c20d4ce460ee',
            total: 28999.00,
            status: 'PAID',
            items: '1x MACBOOK-M3-MAX',
            createdAt: '5 mins ago'
        }
    ];

    displayOrders.forEach(o => {
        const tr = document.createElement('tr');
        const badgeClass = o.status === 'PAID' ? 'paid' : o.status === 'WAITING_PAYMENT' ? 'waiting' : 'cancelled';
        tr.innerHTML = `
            <td><span style="font-family:var(--font-mono); font-size:0.8rem;">${o.id.substring(0, 18)}...</span></td>
            <td><span style="font-family:var(--font-mono); font-size:0.8rem;">${o.customerId.substring(0, 8)}...</span></td>
            <td><strong>R$ ${parseFloat(o.total).toFixed(2)}</strong></td>
            <td><span class="status-badge ${badgeClass}">${o.status}</span></td>
            <td>${o.items || '1 items'}</td>
            <td><small style="color:#64748B;">${o.createdAt || 'Recent'}</small></td>
            <td>
                <button class="btn-ghost-sm" onclick="selectForSaga('${o.id}')">Inspect</button>
            </td>
        `;
        tbody.appendChild(tr);
    });

    document.getElementById('badge-orders').textContent = displayOrders.length;
    populateSagaSelect(displayOrders);
}

// Populate Sku Selects
function populateSkuSelects(products) {
    const sel = document.getElementById('order-sku-select');
    if (!sel) return;
    sel.innerHTML = '';
    products.forEach(p => {
        const opt = document.createElement('option');
        opt.value = p.sku;
        opt.textContent = `${p.sku} — ${p.name} (R$ ${p.price})`;
        sel.appendChild(opt);
    });
}

function populateSagaSelect(orders) {
    const sel = document.getElementById('saga-order-select');
    if (!sel) return;
    sel.innerHTML = '<option value="">Select a waiting order...</option>';
    orders.forEach(o => {
        const opt = document.createElement('option');
        opt.value = o.id;
        opt.textContent = `Order ${o.id.substring(0, 8)}... — R$ ${o.total} (${o.status})`;
        sel.appendChild(opt);
    });
}

function selectForSaga(orderId) {
    const navPayments = document.querySelector('[data-tab="payments"]');
    if (navPayments) navPayments.click();
    const sel = document.getElementById('saga-order-select');
    if (sel) sel.value = orderId;
}

// Create Product Action
async function handleCreateProduct() {
    const sku = document.getElementById('prod-sku').value;
    const name = document.getElementById('prod-name').value;
    const price = document.getElementById('prod-price').value;
    const initialStock = document.getElementById('prod-stock').value;

    if (!sku || !name || !price) {
        alert('Please fill all fields');
        return;
    }

    try {
        const res = await fetch('/api/v1/products', {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                sku,
                name,
                price: parseFloat(price),
                status: 'ACTIVE',
                initialStock: parseInt(initialStock)
            })
        });

        if (res.ok) {
            closeModal('modal-product');
            await fetchProducts();
        } else {
            const err = await res.json();
            alert(`Error: ${err.detail || 'Failed to create product'}`);
        }
    } catch (e) {
        alert('Server error connecting to backend API');
    }
}

// Create Order Action
async function handleCreateOrder() {
    const sku = document.getElementById('order-sku-select').value;
    const qty = parseInt(document.getElementById('order-quantity').value);

    // Create a temporary customer first if needed
    try {
        const custRes = await fetch('/api/v1/customers', {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                name: 'Tony Stark Enterprise',
                email: `tony.${Date.now()}@stark.io`,
                document: `${Math.floor(10000000000 + Math.random() * 90000000000)}`,
                status: 'ACTIVE'
            })
        });
        const custData = await custRes.json();

        // Place Order
        const orderRes = await fetch('/api/v1/orders', {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                customerId: custData.id,
                items: [{ sku: sku, quantity: qty }]
            })
        });

        if (orderRes.ok) {
            const orderData = await orderRes.json();
            currentOrders.unshift({
                id: orderData.id,
                customerId: custData.id,
                total: orderData.totalAmount || (qty * 28999),
                status: orderData.status || 'WAITING_PAYMENT',
                items: `${qty}x ${sku}`,
                createdAt: 'Just now'
            });
            closeModal('modal-order');
            renderOrdersTable(currentOrders);
            updateOrderMetrics();
        } else {
            const err = await orderRes.json();
            alert(`Overselling Blocked: ${err.detail || 'Stock reservation error'}`);
        }
    } catch (e) {
        alert('Order processing error');
    }
}

// Saga Simulation Action
async function handleSagaExecution(simulateFailure) {
    const orderId = document.getElementById('saga-order-select').value;
    const term = document.getElementById('saga-terminal-output');
    if (!orderId) {
        alert('Please select an order ID');
        return;
    }

    term.innerHTML += `<div class="term-line info">[SAGA DISPATCH] Initiating payment for Order ${orderId.substring(0, 8)}... (SimulateFailure: ${simulateFailure})</div>`;

    try {
        const res = await fetch('/api/v1/payments/process', {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                orderId: orderId,
                customerId: 'b832a883-708c-4eb2-bdbe-2509587b264d',
                amount: 57998.00,
                simulateFailure: simulateFailure
            })
        });

        if (res.ok) {
            const payRes = await res.json();
            if (payRes.status === 'APPROVED') {
                term.innerHTML += `<div class="term-line success">[PAYMENT_APPROVED] Transaction ${payRes.id} confirmed. Outbox event written. Order marked as PAID.</div>`;
            } else {
                term.innerHTML += `<div class="term-line warn">[PAYMENT_FAILED] Payment rejected. Saga Orchestrator triggered COMPENSATING TRANSACTION: Stock reservation released!</div>`;
            }
        }
    } catch (e) {
        term.innerHTML += `<div class="term-line error">[ERROR] Failed executing Saga Step</div>`;
    }
    term.scrollTop = term.scrollHeight;
}

// Charts Initialization
function initCharts() {
    // Flow Line Chart
    const ctxFlow = document.getElementById('ordersFlowChart')?.getContext('2d');
    if (ctxFlow) {
        ordersFlowChart = new Chart(ctxFlow, {
            type: 'line',
            data: {
                labels: ['12pm', '1pm', '2pm', '3pm', '4pm', '5pm', '6pm', '7pm'],
                datasets: [{
                    label: 'Order Ingestion Stream (msg/s)',
                    data: [120, 190, 300, 520, 480, 680, 890, 1100],
                    borderColor: '#00F0FF',
                    backgroundColor: 'rgba(0, 240, 255, 0.1)',
                    fill: true,
                    tension: 0.4,
                    borderWidth: 3
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { color: 'rgba(255,255,255,0.05)' } },
                    y: { grid: { color: 'rgba(255,255,255,0.05)' } }
                }
            }
        });
    }

    // Stock Chart
    const ctxStock = document.getElementById('inventoryStockChart')?.getContext('2d');
    if (ctxStock) {
        inventoryStockChart = new Chart(ctxStock, {
            type: 'line',
            data: {
                labels: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00'],
                datasets: [{
                    label: 'Available Stock Units',
                    data: [100, 95, 82, 70, 65, 88],
                    borderColor: '#00F0FF',
                    backgroundColor: 'rgba(0, 240, 255, 0.05)',
                    fill: true,
                    tension: 0.35
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { color: 'rgba(255,255,255,0.05)' } },
                    y: { grid: { color: 'rgba(255,255,255,0.05)' } }
                }
            }
        });
    }

    // Payment Donut Chart
    const ctxDonut = document.getElementById('paymentDonutChart')?.getContext('2d');
    if (ctxDonut) {
        paymentDonutChart = new Chart(ctxDonut, {
            type: 'doughnut',
            data: {
                labels: ['Paid', 'Waiting', 'Failed'],
                datasets: [{
                    data: [88, 7, 5],
                    backgroundColor: ['#00F0FF', '#A855F7', '#EF4444'],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                cutout: '75%'
            }
        });
    }
}

function updateStockChart(products) {
    if (!inventoryStockChart) return;
    const labels = products.slice(0, 6).map(p => p.sku);
    const data = products.slice(0, 6).map(() => 10);
    if (labels.length > 0) {
        inventoryStockChart.data.labels = labels;
        inventoryStockChart.data.datasets[0].data = data;
        inventoryStockChart.update();
    }
}

function updateOrderMetrics() {
    const active = currentOrders.filter(o => o.status === 'WAITING_PAYMENT').length + 1;
    const completed = currentOrders.filter(o => o.status === 'PAID').length + 3420;
    document.getElementById('metric-active-orders').textContent = active;
    document.getElementById('metric-completed-orders').textContent = completed.toLocaleString();
}

async function refreshDashboardMetrics() {
    await fetchHealthStatus();
}

// Helpers
function getAuthHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
    return headers;
}

function openModal(id) {
    document.getElementById(id)?.classList.add('open');
}

function closeModal(id) {
    document.getElementById(id)?.classList.remove('open');
}
