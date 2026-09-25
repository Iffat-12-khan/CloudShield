/**
 * File Location: web/js/app.js
 * Short Purpose: Master frontend controller for CloudShield.
 *                Manages authentication, API data synchronization, RBAC UI controls,
 *                graph updates, SVG charts, and interactive simulators.
 * Connections:
 *   - Communicates with Java backend via /api/* endpoints.
 *   - Drives WFGVisualizer in web/js/wfg-canvas.js.
 *   - Manipulates DOM elements in web/index.html.
 */

const API_BASE = '/api';

// Application State
const state = {
    currentUser: {
        userId: 1,
        username: 'admin',
        fullName: 'System Administrator',
        role: 'ADMIN',
        accountStatus: 'ACTIVE'
    },
    token: localStorage.getItem('cloudshield_token') || 'demo_token',
    vms: [],
    resources: [],
    requests: [],
    allocations: [],
    deadlockData: null,
    threatData: null,
    activeLogTab: 'security',
    searchQuery: '',
    wfgVisualizer: null
};

// ============================================================================
// INITIALIZATION
// ============================================================================
document.addEventListener('DOMContentLoaded', () => {
    // Initialize WFG Canvas
    state.wfgVisualizer = new WFGVisualizer('wfgCanvas');

    // Attach Event Listeners
    initNavigation();
    initModals();
    initForms();
    initSimulations();

    // Initial Data Fetch
    refreshAllData();

    // Periodic Polling every 6 seconds for live telemetry
    setInterval(() => {
        refreshAllData();
    }, 6000);
});

// ============================================================================
// DATA FETCHING & SYNCHRONIZATION
// ============================================================================
async function refreshAllData() {
    await Promise.allSettled([
        fetchDashboardStats(),
        fetchVirtualMachines(),
        fetchResources(),
        fetchRequests(),
        fetchAllocations(),
        fetchDeadlockGraph(),
        fetchThreatScan(),
        fetchAuditLogs()
    ]);
}

async function apiFetch(endpoint, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${state.token}`,
        ...(options.headers || {})
    };

    try {
        const response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });
        if (response.status === 401 && endpoint !== '/auth/login') {
            // Unauthorized or session expired
            console.warn('[API] Session requires authentication.');
        }
        return await response.json();
    } catch (err) {
        console.error(`[API] Error calling ${endpoint}:`, err);
        return null;
    }
}

// ============================================================================
// 1. DASHBOARD STATS & KPI RENDERING
// ============================================================================
async function fetchDashboardStats() {
    const data = await apiFetch('/dashboard/stats');
    if (!data) return;

    // Update Stat Cards Numbers
    const kpiVms = document.getElementById('kpiActiveVms');
    const kpiResources = document.getElementById('kpiTotalResources');
    const kpiDeadlock = document.getElementById('kpiDeadlockRisk');
    const kpiHealth = document.getElementById('kpiHealthScore');

    if (kpiVms) kpiVms.innerText = `${data.runningVms} / ${data.totalVms}`;
    if (kpiResources) kpiResources.innerText = `${data.totalCores} Cores`;
    if (kpiDeadlock) {
        kpiDeadlock.innerText = data.deadlockRisk ? `ALERT (${data.cycleCount})` : 'SECURE';
        kpiDeadlock.style.color = data.deadlockRisk ? '#ef4444' : '#34d399';
    }
    if (kpiHealth) kpiHealth.innerText = `${data.threatScore.toFixed(1)}%`;

    // Render Speedometer Circular Gauge (Image 2 reference)
    renderSpeedometerGauge(data.threatScore);

    // Draw Performance Chart
    renderPerformanceChart();
}

function renderSpeedometerGauge(score) {
    const scoreVal = document.getElementById('gaugeScore');
    const circle = document.getElementById('gaugeProgressCircle');
    if (!circle || !scoreVal) return;

    scoreVal.innerText = `${Math.round(score)}%`;
    // SVG circle circumference = 2 * PI * r = 2 * 3.14159 * 70 ≈ 440
    const totalCircumference = 440;
    const offset = totalCircumference - (totalCircumference * (score / 100));
    circle.style.strokeDashoffset = offset;

    if (score >= 85) {
        circle.style.stroke = '#22d3ee'; // Neon cyan
    } else if (score >= 65) {
        circle.style.stroke = '#f59e0b'; // Amber
    } else {
        circle.style.stroke = '#ef4444'; // Red alert
    }
}

function renderPerformanceChart() {
    const canvas = document.getElementById('performanceChartCanvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const rect = canvas.parentElement.getBoundingClientRect();
    canvas.width = rect.width;
    canvas.height = 220;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Data points representing monthly cloud load & precision
    const points = [
        { label: 'Jan', val: 50 },
        { label: 'Mar', val: 75 },
        { label: 'May', val: 60 },
        { label: 'Jul', val: 92 },
        { label: 'Sep', val: 68 },
        { label: 'Nov', val: 85 },
        { label: 'Dec', val: 95 }
    ];

    const padding = 35;
    const chartW = canvas.width - padding * 2;
    const chartH = canvas.height - padding * 2;

    // Draw grid horizontal lines
    ctx.strokeStyle = 'rgba(139, 92, 246, 0.1)';
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
        const y = padding + (chartH / 4) * i;
        ctx.beginPath();
        ctx.moveTo(padding, y);
        ctx.lineTo(canvas.width - padding, y);
        ctx.stroke();
    }

    // Coordinates calculation
    const coords = points.map((p, idx) => {
        const x = padding + (chartW / (points.length - 1)) * idx;
        const y = padding + chartH - (p.val / 100) * chartH;
        return { x, y, label: p.label, val: p.val };
    });

    // Highlight pillar bar for peak month (Image 2 design element)
    const peak = coords[3]; // July
    const barGrad = ctx.createLinearGradient(peak.x - 18, peak.y, peak.x + 18, padding + chartH);
    barGrad.addColorStop(0, 'rgba(168, 85, 247, 0.45)');
    barGrad.addColorStop(1, 'rgba(168, 85, 247, 0.05)');
    ctx.fillStyle = barGrad;
    ctx.beginPath();
    ctx.roundRect(peak.x - 16, peak.y, 32, (padding + chartH) - peak.y, 8);
    ctx.fill();

    // Fill gradient under spline
    const fillGrad = ctx.createLinearGradient(0, padding, 0, padding + chartH);
    fillGrad.addColorStop(0, 'rgba(139, 92, 246, 0.35)');
    fillGrad.addColorStop(1, 'rgba(139, 92, 246, 0.0)');

    ctx.beginPath();
    ctx.moveTo(coords[0].x, coords[0].y);
    for (let i = 1; i < coords.length; i++) {
        const xc = (coords[i].x + coords[i - 1].x) / 2;
        const yc = (coords[i].y + coords[i - 1].y) / 2;
        ctx.quadraticCurveTo(coords[i - 1].x, coords[i - 1].y, xc, yc);
    }
    ctx.lineTo(coords[coords.length - 1].x, coords[coords.length - 1].y);
    ctx.lineTo(coords[coords.length - 1].x, padding + chartH);
    ctx.lineTo(coords[0].x, padding + chartH);
    ctx.closePath();
    ctx.fillStyle = fillGrad;
    ctx.fill();

    // Draw glowing waveform line
    ctx.strokeStyle = '#a855f7';
    ctx.lineWidth = 3;
    ctx.shadowColor = 'rgba(168, 85, 247, 0.8)';
    ctx.shadowBlur = 10;

    ctx.beginPath();
    ctx.moveTo(coords[0].x, coords[0].y);
    for (let i = 1; i < coords.length; i++) {
        const xc = (coords[i].x + coords[i - 1].x) / 2;
        const yc = (coords[i].y + coords[i - 1].y) / 2;
        ctx.quadraticCurveTo(coords[i - 1].x, coords[i - 1].y, xc, yc);
    }
    ctx.lineTo(coords[coords.length - 1].x, coords[coords.length - 1].y);
    ctx.stroke();

    // Draw vertex dots
    ctx.shadowBlur = 0;
    coords.forEach(pt => {
        ctx.fillStyle = '#ffffff';
        ctx.strokeStyle = '#7c3aed';
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.arc(pt.x, pt.y, 5, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();

        // X-axis label
        ctx.fillStyle = '#94a3b8';
        ctx.font = '10px Inter, sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(pt.label, pt.x, canvas.height - 10);
    });
}

// ============================================================================
// 2. VIRTUAL MACHINES MODULE
// ============================================================================
async function fetchVirtualMachines() {
    const vms = await apiFetch('/vms');
    if (!vms) return;
    state.vms = vms;

    const container = document.getElementById('vmsGrid');
    if (!container) return;

    // Filter by search query if any
    const filtered = vms.filter(v => 
        !state.searchQuery || 
        v.vmName.toLowerCase().includes(state.searchQuery.toLowerCase()) ||
        v.operatingSystem.toLowerCase().includes(state.searchQuery.toLowerCase())
    );

    container.innerHTML = filtered.map(vm => {
        const statusClass = vm.status ? vm.status.toLowerCase() : 'running';
        const isOperatorOrAdmin = state.currentUser.role === 'ADMIN' || state.currentUser.role === 'OPERATOR';

        return `
            <div class="vm-card">
                <div class="vm-card-top">
                    <div>
                        <div class="vm-title">VM-${vm.vmId}: ${escapeHtml(vm.vmName)}</div>
                        <div class="vm-os-tag">${escapeHtml(vm.operatingSystem)} • Owner: ${escapeHtml(vm.ownerUsername || 'User-' + vm.ownerId)}</div>
                    </div>
                    <span class="status-badge ${statusClass}">
                        <span class="status-dot"></span>
                        ${escapeHtml(vm.status)}
                    </span>
                </div>
                <div class="vm-specs-grid">
                    <div class="spec-item">
                        <div class="spec-label">CPU</div>
                        <div class="spec-val">${vm.cpuRequired} Cores</div>
                    </div>
                    <div class="spec-item">
                        <div class="spec-label">RAM</div>
                        <div class="spec-val">${vm.ramRequired} GB</div>
                    </div>
                    <div class="spec-item">
                        <div class="spec-label">Storage</div>
                        <div class="spec-val">${vm.storageRequired} GB</div>
                    </div>
                </div>
                <div class="vm-actions">
                    ${isOperatorOrAdmin ? `
                        ${vm.status === 'RUNNING' ? `
                            <button class="btn btn-outline btn-sm" onclick="toggleVmStatus(${vm.vmId}, 'STOPPED')">Stop</button>
                        ` : `
                            <button class="btn btn-primary btn-sm" onclick="toggleVmStatus(${vm.vmId}, 'RUNNING')">Start</button>
                        `}
                        <button class="btn btn-outline btn-sm" onclick="openRequestModal(${vm.vmId})">+ Request</button>
                        ${state.currentUser.role === 'ADMIN' ? `
                            <button class="btn btn-danger btn-sm" onclick="deleteVm(${vm.vmId})">Delete</button>
                        ` : ''}
                    ` : `
                        <span style="font-size:0.75rem; color:#64748b;">Read-Only Mode</span>
                    `}
                </div>
            </div>
        `;
    }).join('');

    // Also populate select dropdowns in modal
    const vmSelect = document.getElementById('reqVmSelect');
    if (vmSelect) {
        vmSelect.innerHTML = vms.map(v => `<option value="${v.vmId}">${escapeHtml(v.vmName)} (VM-${v.vmId})</option>`).join('');
    }
}

async function toggleVmStatus(vmId, newStatus) {
    const res = await apiFetch('/vms', {
        method: 'PUT',
        body: JSON.stringify({ vmId, status: newStatus })
    });
    if (res && res.success) {
        showToast(`VM status updated to ${newStatus}`);
        refreshAllData();
    }
}

async function deleteVm(vmId) {
    if (!confirm(`Are you sure you want to terminate and delete VM-${vmId}?`)) return;
    const res = await apiFetch(`/vms?vmId=${vmId}`, { method: 'DELETE' });
    if (res && res.success) {
        showToast(`VM-${vmId} terminated and resources released.`);
        refreshAllData();
    }
}

// ============================================================================
// 3. CLOUD RESOURCE POOLS MODULE
// ============================================================================
async function fetchResources() {
    const resources = await apiFetch('/resources');
    if (!resources) return;
    state.resources = resources;

    const container = document.getElementById('resourcePoolsGrid');
    if (!container) return;

    const colors = ['purple', 'cyan', 'orange', 'pink'];

    container.innerHTML = resources.map((r, idx) => {
        const color = colors[idx % colors.length];
        const pct = r.utilizationPct ? r.utilizationPct.toFixed(1) : 0;
        return `
            <div class="pool-card">
                <div class="pool-header">
                    <span class="pool-name">${escapeHtml(r.resourceName)}</span>
                    <span class="tag-badge tag-lavender">${pct}% Used</span>
                </div>
                <div class="pool-metric-numbers">
                    <span class="pool-used">${r.allocatedUnits} <span style="font-size:0.9rem; color:#94a3b8;">${r.unit}</span></span>
                    <span class="pool-total">of ${r.totalUnits} ${r.unit}</span>
                </div>
                <div class="progress-bar-bg">
                    <div class="progress-bar-fill ${color}" style="width: ${pct}%;"></div>
                </div>
                <div style="display:flex; justify-content:space-between; margin-top:0.6rem; font-size:0.75rem; color:#94a3b8;">
                    <span>Available: ${r.availableUnits} ${r.unit}</span>
                    <span>Pool #${r.resourceId}</span>
                </div>
            </div>
        `;
    }).join('');

    // Populate resource select in modal
    const resSelect = document.getElementById('reqResourceSelect');
    if (resSelect) {
        resSelect.innerHTML = resources.map(r => `<option value="${r.resourceId}">${escapeHtml(r.resourceName)} (${r.availableUnits} ${r.unit} available)</option>`).join('');
    }
}

// ============================================================================
// 4. RESOURCE REQUESTS & ALLOCATIONS MODULE
// ============================================================================
async function fetchRequests() {
    const requests = await apiFetch('/requests');
    if (!requests) return;
    state.requests = requests;

    const tbody = document.getElementById('requestsTableBody');
    if (!tbody) return;

    const isOperatorOrAdmin = state.currentUser.role === 'ADMIN' || state.currentUser.role === 'OPERATOR';

    tbody.innerHTML = requests.map(req => {
        const isPending = req.status === 'PENDING';
        return `
            <tr>
                <td>#${req.requestId}</td>
                <td><strong>${escapeHtml(req.vmName)}</strong></td>
                <td>${escapeHtml(req.resourceName)}</td>
                <td><strong>${req.requestedUnits}</strong></td>
                <td><span class="status-badge ${req.status.toLowerCase()}">${req.status}</span></td>
                <td>
                    ${isPending && isOperatorOrAdmin ? `
                        <button class="btn btn-success btn-sm" onclick="approveRequest(${req.requestId})" title="Allocate via ACID Transaction">Approve</button>
                        <button class="btn btn-outline btn-sm" onclick="rejectRequest(${req.requestId})">Reject</button>
                    ` : `
                        <span style="font-size:0.75rem; color:#64748b;">${req.status}</span>
                    `}
                </td>
            </tr>
        `;
    }).join('');
}

async function fetchAllocations() {
    const allocations = await apiFetch('/allocations');
    if (!allocations) return;
    state.allocations = allocations;

    const tbody = document.getElementById('allocationsTableBody');
    if (!tbody) return;

    const isOperatorOrAdmin = state.currentUser.role === 'ADMIN' || state.currentUser.role === 'OPERATOR';

    tbody.innerHTML = allocations.map(a => {
        return `
            <tr>
                <td>#${a.allocationId}</td>
                <td><strong>${escapeHtml(a.vmName)}</strong></td>
                <td>${escapeHtml(a.resourceName)}</td>
                <td>${a.allocatedUnits}</td>
                <td><span class="status-badge running">ACTIVE</span></td>
                <td>
                    ${isOperatorOrAdmin ? `
                        <button class="btn btn-outline btn-sm" onclick="releaseAllocation(${a.allocationId})">Release</button>
                    ` : `
                        <span style="font-size:0.75rem; color:#64748b;">Allocated</span>
                    `}
                </td>
            </tr>
        `;
    }).join('');
}

async function approveRequest(requestId) {
    const res = await apiFetch('/requests/approve', {
        method: 'POST',
        body: JSON.stringify({ requestId })
    });
    if (res && res.success) {
        showToast(res.message);
        refreshAllData();
    } else {
        alert(res?.error || 'Failed to allocate resource.');
    }
}

async function rejectRequest(requestId) {
    const res = await apiFetch('/requests/reject', {
        method: 'POST',
        body: JSON.stringify({ requestId })
    });
    if (res && res.success) {
        showToast('Request rejected.');
        refreshAllData();
    }
}

async function releaseAllocation(allocationId) {
    const res = await apiFetch('/allocations/release', {
        method: 'POST',
        body: JSON.stringify({ allocationId })
    });
    if (res && res.success) {
        showToast(res.message);
        refreshAllData();
    }
}

// ============================================================================
// 5. DEADLOCK DETECTION & WAIT-FOR GRAPH (OS MODULE)
// ============================================================================
async function fetchDeadlockGraph() {
    const data = await apiFetch('/deadlock/graph');
    if (!data) return;
    state.deadlockData = data;

    // Update HTML5 Canvas
    if (state.wfgVisualizer) {
        state.wfgVisualizer.updateData(data.graph, data);
    }

    // Update Alert Banner
    const banner = document.getElementById('deadlockAlertBanner');
    const pathElem = document.getElementById('deadlockCyclePath');
    const resolveBtn = document.getElementById('btnResolveDeadlock');

    if (data.deadlockDetected && data.cyclePaths && data.cyclePaths.length > 0) {
        banner.style.display = 'flex';
        pathElem.innerText = data.cyclePaths[0];
        if (resolveBtn) resolveBtn.style.display = 'inline-flex';
    } else {
        banner.style.display = 'none';
        if (resolveBtn) resolveBtn.style.display = 'none';
    }
}

async function runDeadlockDetection() {
    showToast('Running DFS Deadlock Cycle Detection...');
    const data = await apiFetch('/deadlock/detect');
    if (!data) return;

    state.deadlockData = data;
    if (state.wfgVisualizer) {
        state.wfgVisualizer.updateData(data.graph, data);
    }

    if (data.deadlockDetected) {
        showToast(`CRITICAL: ${data.cycleCount} Deadlock Cycle(s) Identified via DFS!`);
    } else {
        showToast('No circular wait detected. System is operating in a SAFE STATE.');
    }
    refreshAllData();
}

async function resolveDeadlock() {
    const res = await apiFetch('/deadlock/resolve', { method: 'POST' });
    if (res && res.resolved) {
        showToast(res.message);
        refreshAllData();
    } else {
        alert(res?.message || 'Failed to resolve deadlock.');
    }
}

async function simulateDeadlock() {
    showToast('Simulating circular wait deadlock (VM 3 -> VM 2 -> VM 1 -> VM 3)...');
    const data = await apiFetch('/deadlock/simulate', { method: 'POST' });
    if (data) {
        refreshAllData();
        showToast('Deadlock cycle established in graph!');
    }
}

// ============================================================================
// 6. CYBERSECURITY THREAT DETECTION
// ============================================================================
async function fetchThreatScan() {
    const data = await apiFetch('/threats/scan');
    if (!data) return;
    state.threatData = data;

    const listContainer = document.getElementById('threatAlertsList');
    if (!listContainer) return;

    if (!data.alerts || data.alerts.length === 0) {
        listContainer.innerHTML = '<div style="padding:1rem; text-align:center; color:#64748b;">No active cybersecurity threats detected. All systems nominal.</div>';
        return;
    }

    listContainer.innerHTML = data.alerts.map(a => {
        const sevClass = a.severity.toLowerCase();
        return `
            <div class="threat-item ${sevClass}">
                <div class="threat-item-content">
                    <h5>${escapeHtml(a.eventType)} <span class="severity-pill ${sevClass}">${a.severity}</span></h5>
                    <p>${escapeHtml(a.description)}</p>
                    <div class="threat-meta">Origin: ${escapeHtml(a.ipAddress)} • Target: ${escapeHtml(a.subject || 'System')}</div>
                </div>
            </div>
        `;
    }).join('');
}

async function simulateAttack(type) {
    showToast(`Simulating attack pattern: ${type}...`);
    const res = await apiFetch('/threats/simulate', {
        method: 'POST',
        body: JSON.stringify({ type })
    });
    if (res && res.success) {
        showToast(res.message);
        refreshAllData();
    }
}

async function unblockAccount(userId) {
    const res = await apiFetch('/threats/unblock', {
        method: 'POST',
        body: JSON.stringify({ userId })
    });
    if (res && res.success) {
        showToast('Account unblocked successfully.');
        refreshAllData();
    }
}

// ============================================================================
// 7. AUDIT LOGS MODULE
// ============================================================================
async function fetchAuditLogs() {
    let endpoint = '/logs/security';
    if (state.activeLogTab === 'login') endpoint = '/logs/login';
    else if (state.activeLogTab === 'deadlock') endpoint = '/logs/deadlock';

    const logs = await apiFetch(endpoint);
    if (!logs) return;

    const tbody = document.getElementById('logsTableBody');
    const thead = document.getElementById('logsTableHead');
    if (!tbody || !thead) return;

    if (state.activeLogTab === 'security') {
        thead.innerHTML = `
            <tr>
                <th>ID</th>
                <th>Event Type</th>
                <th>Severity</th>
                <th>Description</th>
                <th>IP Address</th>
                <th>User</th>
                <th>Timestamp</th>
            </tr>
        `;
        tbody.innerHTML = logs.map(l => `
            <tr>
                <td>#${l.logId}</td>
                <td><strong>${escapeHtml(l.eventType)}</strong></td>
                <td><span class="severity-pill ${l.severity.toLowerCase()}">${l.severity}</span></td>
                <td>${escapeHtml(l.description)}</td>
                <td><code>${escapeHtml(l.ipAddress)}</code></td>
                <td>${escapeHtml(l.username || 'SYSTEM')}</td>
                <td>${escapeHtml(l.createdAt)}</td>
            </tr>
        `).join('');
    } else if (state.activeLogTab === 'login') {
        thead.innerHTML = `
            <tr>
                <th>ID</th>
                <th>Username</th>
                <th>IP Address</th>
                <th>Status</th>
                <th>Reason</th>
                <th>Attempt Time</th>
            </tr>
        `;
        tbody.innerHTML = logs.map(l => `
            <tr>
                <td>#${l.logId}</td>
                <td><strong>${escapeHtml(l.username)}</strong></td>
                <td><code>${escapeHtml(l.ipAddress)}</code></td>
                <td><span class="status-badge ${l.status.toLowerCase()}">${l.status}</span></td>
                <td>${escapeHtml(l.failureReason || 'N/A')}</td>
                <td>${escapeHtml(l.attemptTime)}</td>
            </tr>
        `).join('');
    } else if (state.activeLogTab === 'deadlock') {
        thead.innerHTML = `
            <tr>
                <th>Log ID</th>
                <th>Cycle Sequence</th>
                <th>Status</th>
                <th>Detected At</th>
                <th>Resolved At</th>
            </tr>
        `;
        tbody.innerHTML = logs.map(l => `
            <tr>
                <td>#${l.logId}</td>
                <td><code style="color:#fca5a5;">${escapeHtml(l.cyclePath)}</code></td>
                <td><span class="status-badge ${l.resolutionStatus === 'RESOLVED' ? 'running' : 'stopped'}">${l.resolutionStatus}</span></td>
                <td>${escapeHtml(l.detectedAt)}</td>
                <td>${l.resolvedAt ? escapeHtml(l.resolvedAt) : 'Pending'}</td>
            </tr>
        `).join('');
    }
}

function switchLogTab(tab) {
    state.activeLogTab = tab;
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === tab);
    });
    fetchAuditLogs();
}

// ============================================================================
// 8. NAVIGATION & UI CONTROLS
// ============================================================================
function initNavigation() {
    // Search input
    const searchInput = document.getElementById('globalSearchInput');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            state.searchQuery = e.target.value.trim();
            fetchVirtualMachines();
        });
    }

    // Smooth Scroll Spy for Navigation Pills
    const sections = document.querySelectorAll('section[id]');
    const navPills = document.querySelectorAll('.nav-pill');

    window.addEventListener('scroll', () => {
        let current = '';
        sections.forEach(section => {
            const sectionTop = section.offsetTop - 140;
            if (window.scrollY >= sectionTop) {
                current = section.getAttribute('id');
            }
        });

        navPills.forEach(pill => {
            pill.classList.remove('active');
            if (pill.getAttribute('href') === `#${current}`) {
                pill.classList.add('active');
            }
        });
    });
}

function initModals() {
    // Deploy VM Modal
    const btnDeploy = document.getElementById('btnOpenDeployModal');
    const deployModal = document.getElementById('deployVmModal');
    const closeDeploy = document.getElementById('btnCloseDeployModal');

    if (btnDeploy && deployModal) {
        btnDeploy.addEventListener('click', () => deployModal.classList.add('open'));
    }
    if (closeDeploy && deployModal) {
        closeDeploy.addEventListener('click', () => deployModal.classList.remove('open'));
    }

    // Request Modal
    const reqModal = document.getElementById('requestResourceModal');
    const closeReq = document.getElementById('btnCloseReqModal');
    if (closeReq && reqModal) {
        closeReq.addEventListener('click', () => reqModal.classList.remove('open'));
    }

    // Login Modal
    const loginModal = document.getElementById('loginModal');
    const openLogin = document.getElementById('userProfileBadge');
    const closeLogin = document.getElementById('btnCloseLoginModal');

    if (openLogin && loginModal) {
        openLogin.addEventListener('click', () => loginModal.classList.add('open'));
    }
    if (closeLogin && loginModal) {
        closeLogin.addEventListener('click', () => loginModal.classList.remove('open'));
    }
}

function openRequestModal(vmId) {
    const modal = document.getElementById('requestResourceModal');
    if (modal) {
        const select = document.getElementById('reqVmSelect');
        if (select && vmId) select.value = vmId;
        modal.classList.add('open');
    }
}

function initForms() {
    // Deploy VM Form
    const deployForm = document.getElementById('deployVmForm');
    if (deployForm) {
        deployForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const vmName = document.getElementById('deployVmName').value;
            const operatingSystem = document.getElementById('deployVmOs').value;
            const cpuRequired = parseInt(document.getElementById('deployVmCpu').value);
            const ramRequired = parseInt(document.getElementById('deployVmRam').value);
            const storageRequired = parseInt(document.getElementById('deployVmStorage').value);

            const res = await apiFetch('/vms', {
                method: 'POST',
                body: JSON.stringify({ vmName, operatingSystem, cpuRequired, ramRequired, storageRequired })
            });

            if (res && res.success) {
                showToast(res.message);
                document.getElementById('deployVmModal').classList.remove('open');
                deployForm.reset();
                refreshAllData();
            } else {
                alert(res?.error || 'Failed to deploy VM.');
            }
        });
    }

    // Request Form
    const reqForm = document.getElementById('resourceRequestForm');
    if (reqForm) {
        reqForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const vmId = parseInt(document.getElementById('reqVmSelect').value);
            const resourceId = parseInt(document.getElementById('reqResourceSelect').value);
            const requestedUnits = parseInt(document.getElementById('reqUnitsInput').value);

            const res = await apiFetch('/requests', {
                method: 'POST',
                body: JSON.stringify({ vmId, resourceId, requestedUnits })
            });

            if (res && res.success) {
                showToast(res.message);
                document.getElementById('requestResourceModal').classList.remove('open');
                reqForm.reset();
                refreshAllData();
            } else {
                alert(res?.error || 'Failed to submit request.');
            }
        });
    }

    // Login Form
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const username = document.getElementById('loginUsername').value;
            const password = document.getElementById('loginPassword').value;

            const res = await apiFetch('/auth/login', {
                method: 'POST',
                body: JSON.stringify({ username, password })
            });

            if (res && res.success) {
                state.token = res.token;
                state.currentUser = res.user;
                localStorage.setItem('cloudshield_token', res.token);
                updateUserProfileUI();
                showToast(`Welcome back, ${res.user.fullName}! Logged in as ${res.user.role}.`);
                document.getElementById('loginModal').classList.remove('open');
                refreshAllData();
            } else {
                alert(res?.error || 'Login failed. Please check credentials.');
                refreshAllData();
            }
        });
    }
}

function selectDemoAccount(username, password) {
    document.getElementById('loginUsername').value = username;
    document.getElementById('loginPassword').value = password;
}

function updateUserProfileUI() {
    const nameElem = document.getElementById('navUserName');
    const roleElem = document.getElementById('navUserRole');
    const heroWelcome = document.getElementById('heroWelcomeText');

    if (nameElem) nameElem.innerText = state.currentUser.fullName || state.currentUser.username;
    if (roleElem) roleElem.innerText = state.currentUser.role;
    if (heroWelcome) heroWelcome.innerText = `Welcome back, ${state.currentUser.fullName || state.currentUser.username}`;
}

function initSimulations() {
    // Button hooks for Deadlock and Security simulations
    const btnDetectDl = document.getElementById('btnRunDeadlockDetection');
    const btnSimDl = document.getElementById('btnSimulateDeadlock');
    const btnResolveDl = document.getElementById('btnResolveDeadlock');

    if (btnDetectDl) btnDetectDl.addEventListener('click', runDeadlockDetection);
    if (btnSimDl) btnSimDl.addEventListener('click', simulateDeadlock);
    if (btnResolveDl) btnResolveDl.addEventListener('click', resolveDeadlock);

    // Threat Simulations
    const btnSimBrute = document.getElementById('btnSimulateBruteForce');
    const btnSimAbuse = document.getElementById('btnSimulateResourceAbuse');

    if (btnSimBrute) btnSimBrute.addEventListener('click', () => simulateAttack('BRUTE_FORCE'));
    if (btnSimAbuse) btnSimAbuse.addEventListener('click', () => simulateAttack('RESOURCE_ABUSE'));
}

// Toast notification helper
function showToast(message) {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.innerHTML = `
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#a855f7" stroke-width="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
        </svg>
        <span>${escapeHtml(message)}</span>
    `;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        toast.style.transition = 'all 0.3s';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
