// Copyright (c) 2024 Karandeep Malik. All rights reserved.
// PharmaTrack E2E Authentication & Inventory Tests

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
  console.log('\nPharmaTrack E2E Tests\n');

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

  // Invalid credentials
  console.log('\n-- Invalid Credentials');
  await test('Wrong password returns 401', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'admin', password: 'wrongpass' });
    assert(r.status === 401, `Expected 401, got ${r.status}`);
  });
  await test('Unknown user returns 401', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'nobody', password: 'pass' });
    assert(r.status === 401, `Expected 401, got ${r.status}`);
  });
  await test('lostinventory user cannot log in (inactive → 401)', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'lostinventory', password: 'NoLogin@System999!' });
    assert(r.status === 401, `Expected 401 for system user, got ${r.status}`);
  });

  // CORS
  console.log('\n-- CORS (browser origin simulation)');
  await test('Preflight OPTIONS from frontend origin returns 200/204', async () => {
    const res = await fetch(`${API}/auth/login`, {
      method: 'OPTIONS',
      headers: {
        'Origin': FRONTEND,
        'Access-Control-Request-Method': 'POST',
        'Access-Control-Request-Headers': 'Content-Type',
      },
    });
    assert(res.status === 200 || res.status === 204, `Expected 200/204, got ${res.status}`);
    const acao = res.headers.get('access-control-allow-origin');
    assert(acao === FRONTEND || acao === '*', `ACAO header: ${acao}`);
  });
  await test('Admin login with frontend Origin header returns ACAO header', async () => {
    const res = await fetch(`${API}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Origin': FRONTEND },
      body: JSON.stringify({ username: 'admin', password: 'Admin@123' }),
    });
    const acao = res.headers.get('access-control-allow-origin');
    assert(res.status === 200, `Login status: ${res.status}`);
    assert(acao === FRONTEND || acao === '*', `ACAO header missing or wrong: "${acao}"`);
  });
  await test('Backend rejects cross-origin from unknown origin', async () => {
    const res = await fetch(`${API}/auth/login`, {
      method: 'OPTIONS',
      headers: {
        'Origin': 'https://evil.example.com',
        'Access-Control-Request-Method': 'POST',
        'Access-Control-Request-Headers': 'Content-Type',
      },
    });
    const acao = res.headers.get('access-control-allow-origin');
    assert(acao !== 'https://evil.example.com', `Should not allow evil origin, got ACAO: ${acao}`);
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

  // System Inventory
  console.log('\n-- System Inventory');
  let firstMedicineId;
  await test('Admin can get system inventory', async () => {
    const r = await apiGet(`${API}/inventory/system`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    assert(Array.isArray(r.data), 'Expected array');
    assert(r.data.length > 0, 'Expected at least one system inventory record');
    firstMedicineId = r.data[0].medicineId;
  });
  await test('System inventory records have specUnit field', async () => {
    const r = await apiGet(`${API}/inventory/system`, adminToken);
    const item = r.data[0];
    assert(item.specUnit === 'mg/ml' || item.specUnit === 'mg', `Expected mg/ml or mg, got: ${item.specUnit}`);
  });
  await test('VIAL medicines have specUnit mg/ml', async () => {
    const r = await apiGet(`${API}/inventory/system`, adminToken);
    const vials = r.data.filter(i => i.medicineType === 'VIAL');
    assert(vials.length > 0, 'Expected at least one VIAL in system inventory');
    vials.forEach(v => assert(v.specUnit === 'mg/ml', `VIAL specUnit should be mg/ml, got: ${v.specUnit}`));
  });
  await test('TABLET medicines have specUnit mg', async () => {
    const r = await apiGet(`${API}/inventory/system`, adminToken);
    const tablets = r.data.filter(i => i.medicineType === 'TABLET');
    assert(tablets.length > 0, 'Expected at least one TABLET in system inventory');
    tablets.forEach(t => assert(t.specUnit === 'mg', `TABLET specUnit should be mg, got: ${t.specUnit}`));
  });
  await test('Non-admin cannot access system inventory (403)', async () => {
    const r = await apiGet(`${API}/inventory/system`, userToken);
    assert(r.status === 403, `Expected 403, got ${r.status}`);
  });
  await test('Admin can add to system inventory', async () => {
    const r = await apiPost(`${API}/inventory/system`, { medicineId: firstMedicineId, quantity: 10 }, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.quantity > 0, `Expected positive quantity, got: ${r.data.quantity}`);
  });
  await test('Adding system inventory with quantity 0 returns 400', async () => {
    const r = await apiPost(`${API}/inventory/system`, { medicineId: firstMedicineId, quantity: 0 }, adminToken);
    assert(r.status === 400, `Expected 400, got ${r.status}`);
  });

  // Inventory Allocation
  console.log('\n-- Inventory Allocation (Add Inventory to User)');
  let availableBeforeAlloc;
  await test('Admin can view all user inventory', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    assert(Array.isArray(r.data), 'Expected array');
    assert(r.data.length > 0, 'Expected at least one inventory record');
    // lostinventory should NOT appear in user inventory list
    const hasSystem = r.data.some(i => i.username === 'lostinventory');
    assert(!hasSystem, 'lostinventory should be excluded from user inventory view');
  });
  await test('User inventory records have expected fields', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    const item = r.data[0];
    assert(item.username !== undefined, 'Missing username');
    assert(item.medicineName !== undefined, 'Missing medicineName');
    assert(item.quantity !== undefined, 'Missing quantity');
    assert(item.specUnit !== undefined, 'Missing specUnit');
  });
  await test('System inventory shows available for first medicine', async () => {
    const r = await apiGet(`${API}/inventory/system`, adminToken);
    const item = r.data.find(i => i.medicineId === firstMedicineId);
    assert(item, 'Expected system inventory for first medicine');
    availableBeforeAlloc = item.quantity;
    assert(availableBeforeAlloc >= 0, `Expected non-negative qty, got: ${availableBeforeAlloc}`);
  });
  await test('Admin can allocate inventory to user', async () => {
    if (availableBeforeAlloc < 1) { console.log('    (skipping: no system inventory available)'); return; }
    const usersR = await apiGet(`${API}/users`, adminToken);
    const john = usersR.data.find(u => u.username === 'john.doe');
    assert(john, 'john.doe not found');
    const r = await apiPost(`${API}/inventory/allocate`,
      { userId: john.id, medicineId: firstMedicineId, quantity: 1 }, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
  });
  await test('Allocating more than system inventory returns 409', async () => {
    const usersR = await apiGet(`${API}/users`, adminToken);
    const john = usersR.data.find(u => u.username === 'john.doe');
    const r = await apiPost(`${API}/inventory/allocate`,
      { userId: john.id, medicineId: firstMedicineId, quantity: 999999 }, adminToken);
    assert(r.status === 409, `Expected 409, got ${r.status}`);
  });
  await test('User cannot allocate inventory (403)', async () => {
    const usersR = await apiGet(`${API}/users`, adminToken);
    const john = usersR.data.find(u => u.username === 'john.doe');
    const r = await apiPost(`${API}/inventory/allocate`,
      { userId: john.id, medicineId: firstMedicineId, quantity: 1 }, userToken);
    assert(r.status === 403, `Expected 403, got ${r.status}`);
  });

  // Admin: User Management
  console.log('\n-- Admin: User Management');
  await test('Admin can list all users', async () => {
    const r = await apiGet(`${API}/users`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    assert(Array.isArray(r.data), 'Expected array');
    assert(r.data.length >= 4, `Expected at least 4 seeded users (incl. lostinventory), got ${r.data.length}`);
  });
  await test('lostinventory user exists and is inactive', async () => {
    const r = await apiGet(`${API}/users`, adminToken);
    const sysUser = r.data.find(u => u.username === 'lostinventory');
    assert(sysUser, 'lostinventory user not found');
    assert(sysUser.active === false, `Expected lostinventory to be inactive, got: ${sysUser.active}`);
  });
  await test('User list includes expected fields', async () => {
    const r = await apiGet(`${API}/users`, adminToken);
    const u = r.data[0];
    assert(u.username !== undefined, 'Missing username');
    assert(u.fullName !== undefined, 'Missing fullName');
    assert(u.role !== undefined, 'Missing role');
    assert(u.active !== undefined, 'Missing active');
  });
  await test('Non-admin cannot list users (403)', async () => {
    const r = await apiGet(`${API}/users`, userToken);
    assert(r.status === 403, `Expected 403, got ${r.status}`);
  });

  const testUsername = `e2e_user_${Date.now()}`;
  let createdUserId;
  await test('Admin can create a new user', async () => {
    const r = await apiPost(`${API}/users`, {
      username: testUsername, email: `${testUsername}@e2e.test`,
      fullName: 'E2E Test User', password: 'TestPass1!', role: 'USER',
    }, adminToken);
    assert(r.status === 201, `Expected 201, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.username === testUsername, `Got: ${r.data.username}`);
    createdUserId = r.data.id;
  });
  await test('Newly created user can log in', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: testUsername, password: 'TestPass1!' });
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.token && r.data.token.length > 20, 'Expected valid token');
  });
  await test('Admin cannot create user with duplicate username', async () => {
    const r = await apiPost(`${API}/users`, {
      username: testUsername, email: `other_${testUsername}@e2e.test`,
      fullName: 'Duplicate', password: 'TestPass1!', role: 'USER',
    }, adminToken);
    assert(r.status === 400 || r.status === 409, `Expected 4xx, got ${r.status}`);
  });
  await test('Non-admin cannot create a user (403)', async () => {
    const r = await apiPost(`${API}/users`, {
      username: 'shouldfail', email: 'shouldfail@test.com',
      fullName: 'Should Fail', password: 'TestPass1!',
    }, userToken);
    assert(r.status === 403, `Expected 403, got ${r.status}`);
  });
  await test('Admin can toggle user active status', async () => {
    assert(createdUserId, 'No createdUserId — create test must have passed');
    const r = await apiPost(`${API}/users/${createdUserId}/toggle`, {}, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    assert(r.data.active === false, `Expected active=false, got ${r.data.active}`);
  });
  await test('Deactivated user cannot log in', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: testUsername, password: 'TestPass1!' });
    assert(r.status === 401, `Expected 401 for deactivated user, got ${r.status}`);
  });

  // Medicine Specifications
  console.log('\n-- Medicine Specifications');
  await test('All VIAL medicines have specification 5 or 10 (mg/ml)', async () => {
    const r = await apiGet(`${API}/inventory/system`, adminToken);
    const vials = r.data.filter(i => i.medicineType === 'VIAL');
    assert(vials.length > 0, 'Expected at least one VIAL');
    vials.forEach(v => {
      assert(v.specification === 5 || v.specification === 10,
        `VIAL specification must be 5 or 10 mg/ml, got: ${v.specification}`);
    });
  });
  await test('All TABLET medicines have specification 12, 25, or 50 (mg)', async () => {
    const r = await apiGet(`${API}/inventory/system`, adminToken);
    const tablets = r.data.filter(i => i.medicineType === 'TABLET');
    assert(tablets.length > 0, 'Expected at least one TABLET');
    tablets.forEach(t => {
      assert([12, 25, 50].includes(t.specification),
        `TABLET specification must be 12, 25, or 50 mg, got: ${t.specification}`);
    });
  });

  // Summary
  console.log(`\n${'='.repeat(40)}`);
  console.log(`Results: ${passed} passed, ${failed} failed`);
  console.log('='.repeat(40));
  if (failed > 0) process.exit(1);
}

run().catch(e => { console.error(e); process.exit(1); });
