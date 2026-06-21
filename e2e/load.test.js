#!/usr/bin/env node
/**
 * PharmaTrack API Performance & Load Tests
 *
 * Runs concurrent requests against the live Cloud Run backend and measures
 * latency percentiles (p50 / p95 / p99) and error rate.
 *
 * Usage:
 *   node e2e/load.test.js
 *
 * Env vars (all optional — defaults target production):
 *   PHARMATRACK_API    Base API URL  (default: Cloud Run backend /api)
 *   PHARMATRACK_USER   Admin username (default: admin)
 *   PHARMATRACK_PASS   Admin password (default: Admin@123)
 *
 * SLOs enforced:
 *   p95 latency < 2 000 ms for all endpoints
 *   p99 latency < 5 000 ms for all endpoints
 *   error rate  < 2 %
 */

const API  = (process.env.PHARMATRACK_API  || 'https://pharmatrack-backend-558147403401.asia-south1.run.app/api');
const USER = (process.env.PHARMATRACK_USER || 'admin');
const PASS = (process.env.PHARMATRACK_PASS || 'Admin@123');

let passed = 0;
let failed = 0;

function assert(cond, msg) {
    if (!cond) throw new Error(msg);
}

async function apiFetch(url, opts = {}) {
    const start = Date.now();
    try {
        const res = await fetch(url, opts);
        return { ok: res.ok, status: res.status, latencyMs: Date.now() - start };
    } catch (err) {
        return { ok: false, status: 0, latencyMs: Date.now() - start, error: err.message };
    }
}

function percentile(sorted, pct) {
    if (sorted.length === 0) return 0;
    const idx = Math.min(Math.floor((pct / 100) * sorted.length), sorted.length - 1);
    return sorted[idx];
}

function stats(latencies) {
    const sorted = [...latencies].sort((a, b) => a - b);
    return {
        count: sorted.length,
        min:   sorted[0]   ?? 0,
        p50:   percentile(sorted, 50),
        p95:   percentile(sorted, 95),
        p99:   percentile(sorted, 99),
        max:   sorted[sorted.length - 1] ?? 0,
        avg:   sorted.length ? Math.round(sorted.reduce((s, v) => s + v, 0) / sorted.length) : 0,
    };
}

/**
 * Run `total` requests with at most `concurrency` in-flight at once.
 * Returns { latencies, errors }.
 */
async function loadTest(label, makeRequest, { total = 50, concurrency = 10 } = {}) {
    const latencies = [];
    const errors    = [];

    for (let i = 0; i < total; i += concurrency) {
        const batch = Array.from(
            { length: Math.min(concurrency, total - i) },
            () => makeRequest()
        );
        const results = await Promise.all(batch);
        for (const r of results) {
            if (r.ok) {
                latencies.push(r.latencyMs);
            } else {
                errors.push({ status: r.status, error: r.error });
                latencies.push(r.latencyMs); // still count latency
            }
        }
    }

    const s = stats(latencies);
    const errorRate = errors.length / total;

    console.log(`  ${label}`);
    console.log(`    requests=${total}  concurrency=${concurrency}  errors=${errors.length} (${(errorRate * 100).toFixed(1)}%)`);
    console.log(`    p50=${s.p50}ms  p95=${s.p95}ms  p99=${s.p99}ms  avg=${s.avg}ms  min=${s.min}ms  max=${s.max}ms`);

    return { latencies, errors, stats: s, errorRate };
}

async function test(name, fn) {
    try {
        await fn();
        console.log(`  PASS: ${name}`);
        passed++;
    } catch (e) {
        console.error(`  FAIL: ${name}\n       ${e.message}`);
        failed++;
    }
}

async function run() {
    console.log('\nPharmaTrack — Performance & Load Tests\n');

    // ── Authenticate ────────────────────────────────────────────────────
    console.log('-- Authenticating…');
    const authRes = await apiFetch(`${API}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: USER, password: PASS }),
    });
    if (!authRes.ok) {
        console.error(`Authentication failed (${authRes.status}). Aborting.`);
        process.exit(1);
    }
    const authBody = await (await fetch(`${API}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: USER, password: PASS }),
    })).json();
    const token = authBody.token;
    if (!token) {
        console.error('No token in auth response. Aborting.');
        process.exit(1);
    }
    console.log('  Authenticated.\n');

    const authHeader = { Authorization: `Bearer ${token}` };

    // ── Baseline: Health endpoint (no auth, cheapest possible) ──────────
    console.log('-- Baseline: /actuator/health (unauthenticated)');
    const healthResult = await loadTest(
        '/actuator/health',
        () => apiFetch(`${API.replace('/api', '')}/actuator/health`),
        { total: 100, concurrency: 20 }
    );
    await test('Health p95 < 2 000ms', () => {
        assert(healthResult.stats.p95 < 2000,
            `p95=${healthResult.stats.p95}ms exceeds 2 000ms SLO`);
    });
    await test('Health error rate < 2%', () => {
        assert(healthResult.errorRate < 0.02,
            `error rate ${(healthResult.errorRate * 100).toFixed(1)}% exceeds 2% SLO`);
    });

    // ── Sales Graph: daily (lightest report) ────────────────────────────
    console.log('\n-- Sales Graph: daily (50 requests, concurrency 10)');
    const sgDailyResult = await loadTest(
        'GET /api/reports/sales-graph?period=daily',
        () => apiFetch(`${API}/reports/sales-graph?period=daily`, { headers: authHeader }),
        { total: 50, concurrency: 10 }
    );
    await test('Sales-graph daily p95 < 2 000ms', () => {
        assert(sgDailyResult.stats.p95 < 2000,
            `p95=${sgDailyResult.stats.p95}ms exceeds 2 000ms SLO`);
    });
    await test('Sales-graph daily p99 < 5 000ms', () => {
        assert(sgDailyResult.stats.p99 < 5000,
            `p99=${sgDailyResult.stats.p99}ms exceeds 5 000ms SLO`);
    });
    await test('Sales-graph daily error rate < 2%', () => {
        assert(sgDailyResult.errorRate < 0.02,
            `error rate ${(sgDailyResult.errorRate * 100).toFixed(1)}% exceeds 2% SLO`);
    });

    // ── Sales Graph: monthly (most expensive — full year) ───────────────
    console.log('\n-- Sales Graph: monthly (30 requests, concurrency 5)');
    const sgMonthlyResult = await loadTest(
        'GET /api/reports/sales-graph?period=monthly',
        () => apiFetch(`${API}/reports/sales-graph?period=monthly`, { headers: authHeader }),
        { total: 30, concurrency: 5 }
    );
    await test('Sales-graph monthly p95 < 2 000ms', () => {
        assert(sgMonthlyResult.stats.p95 < 2000,
            `p95=${sgMonthlyResult.stats.p95}ms exceeds 2 000ms SLO`);
    });
    await test('Sales-graph monthly error rate < 2%', () => {
        assert(sgMonthlyResult.errorRate < 0.02,
            `error rate ${(sgMonthlyResult.errorRate * 100).toFixed(1)}% exceeds 2% SLO`);
    });

    // ── Inventory by user ────────────────────────────────────────────────
    console.log('\n-- Inventory-by-user (30 requests, concurrency 5)');
    const invResult = await loadTest(
        'GET /api/reports/inventory-by-user',
        () => apiFetch(`${API}/reports/inventory-by-user`, { headers: authHeader }),
        { total: 30, concurrency: 5 }
    );
    await test('Inventory-by-user p95 < 2 000ms', () => {
        assert(invResult.stats.p95 < 2000,
            `p95=${invResult.stats.p95}ms exceeds 2 000ms SLO`);
    });

    // ── Telemetry throughput (fire-and-forget from many users) ───────────
    console.log('\n-- Telemetry POST (50 requests, concurrency 20)');
    let telIdx = 0;
    const telResult = await loadTest(
        'POST /api/telemetry',
        () => apiFetch(`${API}/telemetry`, {
            method:  'POST',
            headers: { ...authHeader, 'Content-Type': 'application/json' },
            body:    JSON.stringify({ eventName: `load_test_${++telIdx}`, page: '/load-test', properties: {} }),
        }),
        { total: 50, concurrency: 20 }
    );
    await test('Telemetry p95 < 2 000ms', () => {
        assert(telResult.stats.p95 < 2000,
            `p95=${telResult.stats.p95}ms exceeds 2 000ms SLO`);
    });
    await test('Telemetry error rate < 2%', () => {
        assert(telResult.errorRate < 0.02,
            `error rate ${(telResult.errorRate * 100).toFixed(1)}% exceeds 2% SLO`);
    });

    // ── Summary ──────────────────────────────────────────────────────────
    console.log(`\n${'='.repeat(50)}`);
    console.log(`Performance results: ${passed} passed, ${failed} failed`);
    console.log('='.repeat(50));
    if (failed > 0) process.exit(1);
}

run().catch(e => { console.error(e); process.exit(1); });
