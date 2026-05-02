// Copyright (c) 2024 Karandeep Malik. All rights reserved.
// PharmaTrack E2E Authentication Tests
// Run from Cloud Shell: cd e2e && npm install && node auth.test.js

const FRONTEND = 'https://pharmatrack-frontend-558147403401.asia-south1.run.app';
const BACKEND  = 'https://pharmatrack-backend-558147403401.asia-south1.run.app';
const API      = `${BACKEND}/api`;

let passed = 0, failed = 0;

async function test(name, fn) {
  try { await fn(); console.log(`  PASS: ${name}`); passed++; }
  catch(e) { console.log(`  FAIL: ${name} -- ${e.message}`); failed++; }
}

function assert(cond, msg) { if (!cond) throw new Error(msg); }

async function apiPost(url, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

async function apiGet(url, token) {
  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(url, { headers });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

async function run() {
  console.log('\nPharmaTrack E2E Auth Tests\n');

  // Infrastructure
  console.log('-- Infrastructure');
  await test('Frontend returns 200', async () => {
    const r = await apiGet(FRONTEND);
    assert(r.status === 200, `Got ${r.status}`);
  });
  await test('Backend health returns UP', async () => {
    const r = await apiGet(`${BACKEND}/actuator/health`);
    assert(r.status === 200 && r.data.status === 'UP', `Got ${r.status} ${JSON.stringify(r.data)}`);
  });

  // Admin Login
  console.log('\n-- Admin Login');
  let adminToken;
  await test('Admin login returns 200', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'admin', password: 'Admin@123' });
    assert(r.status === 200, `Got ${r.status}: ${JSON.stringify(r.data)}`);
    adminToken = r.data.token;
  });
  await test('Admin token is present', async () => {
    assert(adminToken && adminToken.length > 20, `Token: ${adminToken}`);
  });
  await test('Admin role is ADMIN', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'admin', password: 'Admin@123' });
    assert(r.data.role === 'ADMIN', `Got role: ${r.data.role}`);
  });
  await test('Admin username is correct', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'admin', password: 'Admin@123' });
    assert(r.data.username === 'admin', `Got: ${r.data.username}`);
  });
  await test('Admin fullName is present', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'admin', password: 'Admin@123' });
    assert(r.data.fullName && r.data.fullName.length > 0, `Got: ${r.data.fullName}`);
  });

  // User Login
  console.log('\n-- User Login');
  let userToken;
  await test('User login returns 200', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'john.doe', password: 'User@123' });
    assert(r.status === 200, `Got ${r.status}: ${JSON.stringify(r.data)}`);
    userToken = r.data.token;
  });
  await test('User role is USER', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'john.doe', password: 'User@123' });
    assert(r.data.role === 'USER', `Got role: ${r.data.role}`);
  });

  // Bad credentials
  console.log('\n-- Invalid Credentials');
  await test('Wrong password returns 401', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'admin', password: 'wrongpass' });
    assert(r.status === 401, `Expected 401, got ${r.status}`);
  });
  await test('Unknown user returns 401', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'nobody', password: 'pass' });
    assert(r.status === 401, `Expected 401, got ${r.status}`);
  });

  // Protected routes
  console.log('\n-- Protected Routes');
  await test('Admin-only endpoint rejects no token (401/403)', async () => {
    const r = await apiGet(`${API}/inventory`);
    assert(r.status === 401 || r.status === 403, `Expected 401/403, got ${r.status}`);
  });
  await test('Admin can access /api/inventory with token', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
  });
  await test('User can access /api/inventory/available with token', async () => {
    const r = await apiGet(`${API}/inventory/available`, userToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
  });

  // Summary
  console.log(`\n${'='.repeat(40)}`);
  console.log(`Results: ${passed} passed, ${failed} failed`);
  console.log('='.repeat(40));
  if (failed > 0) process.exit(1);
}

run().catch(e => { console.error(e); process.exit(1); });
