// Copyright (c) 2024 Karandeep Malik. All rights reserved.
// PharmaTrack E2E Tests — run against production Cloud Run services

const FRONTEND = 'https://pharmatrack-frontend-xhlza2c2ua-el.a.run.app';
const BACKEND  = 'https://pharmatrack-backend-xhlza2c2ua-el.a.run.app';
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

async function apiPut(url, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(url, { method: 'PUT', headers, body: JSON.stringify(body) });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

async function apiDelete(url, token) {
  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(url, { method: 'DELETE', headers });
  return { status: res.status };
}

async function apiPostForm(url, fields, token) {
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) {
    if (Array.isArray(v)) {
      for (const item of v) form.append(k, item);
    } else {
      form.append(k, v);
    }
  }
  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(url, { method: 'POST', headers, body: form });
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

function makeFakePng(label = 'A') {
  return new File([`fake-png-${label}`], `screenshot-${label}.png`, { type: 'image/png' });
}

async function run() {
  console.log('\nPharmaTrack E2E Tests\n');

  // ── Infrastructure ───────────────────────────────────────────��────────
  console.log('-- Infrastructure');
  await test('Frontend returns 200', async () => {
    const r = await apiGet(FRONTEND);
    assert(r.status === 200, `Got ${r.status}`);
  });
  await test('Backend health returns UP', async () => {
    const r = await apiGet(`${BACKEND}/actuator/health`);
    assert(r.status === 200 && r.data.status === 'UP', `Got ${r.status} ${JSON.stringify(r.data)}`);
  });

  // ── Admin Login ───────────────────────────────────────────────────────
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

  // ── User Login ────────────────────────────────────────────────────────
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

  // ── Invalid credentials ───────────────────────────────────────────────
  console.log('\n-- Invalid Credentials');
  await test('Wrong password returns 401', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'admin', password: 'wrongpass' });
    assert(r.status === 401, `Expected 401, got ${r.status}`);
  });
  await test('Unknown user returns 401', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'nobody', password: 'pass' });
    assert(r.status === 401, `Expected 401, got ${r.status}`);
  });
  await test('lostinventory user no longer exists in seed', async () => {
    const r = await apiGet(`${API}/users`, adminToken);
    const sysUser = r.data.find(u => u.username === 'lostinventory');
    assert(!sysUser, 'lostinventory system user should not exist after reseed');
  });

  // ── CORS ──────────────────────────────────────────────────────────────
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

  // ── Protected Routes ───────────────────────────────────────────��──────
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
  await test('Non-admin cannot access /api/inventory (403)', async () => {
    const r = await apiGet(`${API}/inventory`, userToken);
    assert(r.status === 401 || r.status === 403, `Expected 401 or 403, got ${r.status}`);
  });
  await test('/api/inventory/system endpoint no longer exists (404)', async () => {
    const r = await apiGet(`${API}/inventory/system`, adminToken);
    assert(r.status === 404, `Expected 404 (removed), got ${r.status}`);
  });

  // ── Medicine Specifications ───────────────────────────────────��───────
  console.log('\n-- Medicine Specifications (Shield FX)');
  let allMedicines;
  await test('Admin can list medicines', async () => {
    const r = await apiGet(`${API}/medicines`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    assert(Array.isArray(r.data), 'Expected array');
    allMedicines = r.data;
  });
  await test('Exactly 5 Shield FX medicines exist', async () => {
    assert(allMedicines.length === 5, `Expected 5, got ${allMedicines.length}`);
  });
  await test('All medicines belong to Shield FX', async () => {
    allMedicines.forEach(m => {
      assert(m.name.startsWith('Shield FX'), `Expected Shield FX name, got: ${m.name}`);
    });
  });
  await test('Tablet medicines have (10 Tablets) in name', async () => {
    const tablets = allMedicines.filter(m => m.type === 'TABLET');
    assert(tablets.length === 3, `Expected 3 tablets, got ${tablets.length}`);
    tablets.forEach(t => {
      assert(t.name.includes('(10 Tablets)'), `Expected (10 Tablets) in name, got: ${t.name}`);
    });
  });
  await test('All medicines have a price field', async () => {
    allMedicines.forEach(m => {
      assert(typeof m.price === 'number' && m.price > 0, `Expected positive price for ${m.name}, got: ${m.price}`);
    });
  });
  await test('Vial 5 ml price is Rs 2000', async () => {
    const m = allMedicines.find(m => m.name === 'Shield FX Vial 5 ml');
    assert(m, 'Shield FX Vial 5 ml not found');
    assert(m.price === 2000, `Expected 2000, got ${m.price}`);
  });
  await test('Vial 10 ml price is Rs 4000', async () => {
    const m = allMedicines.find(m => m.name === 'Shield FX Vial 10 ml');
    assert(m, 'Shield FX Vial 10 ml not found');
    assert(m.price === 4000, `Expected 4000, got ${m.price}`);
  });
  await test('Tablet 12 mg (10 Tablets) price is Rs 1750', async () => {
    const m = allMedicines.find(m => m.name === 'Shield FX Tablet 12 mg (10 Tablets)');
    assert(m, 'Shield FX Tablet 12 mg (10 Tablets) not found');
    assert(m.price === 1750, `Expected 1750, got ${m.price}`);
  });
  await test('Tablet 25 mg (10 Tablets) price is Rs 4000', async () => {
    const m = allMedicines.find(m => m.name === 'Shield FX Tablet 25 mg (10 Tablets)');
    assert(m, 'Shield FX Tablet 25 mg (10 Tablets) not found');
    assert(m.price === 4000, `Expected 4000, got ${m.price}`);
  });
  await test('Tablet 50 mg (10 Tablets) price is Rs 8000', async () => {
    const m = allMedicines.find(m => m.name === 'Shield FX Tablet 50 mg (10 Tablets)');
    assert(m, 'Shield FX Tablet 50 mg (10 Tablets) not found');
    assert(m.price === 8000, `Expected 8000, got ${m.price}`);
  });
  await test('No old tablet names without (10 Tablets) exist', async () => {
    const badNames = ['Shield FX Tablet 12 mg', 'Shield FX Tablet 25 mg', 'Shield FX Tablet 50 mg'];
    allMedicines.forEach(m => {
      assert(!badNames.includes(m.name), `Old medicine name still present: ${m.name}`);
    });
  });

  // ── Inventory — user inventory ──────────────────────────────��───────
  console.log('\n-- Inventory — user inventory');
  await test('Admin can list all user inventories', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    assert(Array.isArray(r.data), 'Expected array');
  });
  await test('Inventory records include price and specUnit fields', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    if (r.data.length === 0) return; // no allocations yet
    const item = r.data[0];
    assert(item.specUnit !== undefined, 'Missing specUnit');
    assert(item.price !== undefined, 'Missing price');
  });
  await test('TABLET inventory items have specUnit mg (10 Tablets)', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    const tablets = r.data.filter(i => i.medicineType === 'TABLET');
    tablets.forEach(t => {
      assert(t.specUnit === 'mg (10 Tablets)', `Expected 'mg (10 Tablets)', got: ${t.specUnit}`);
    });
  });

  // ── Admin: Adjust User Inventory ───────────────────────────��────────
  console.log('\n-- Admin: Adjust User Inventory');
  let johnId;
  let adjustMedicineId;
  let quantityBeforeAdjust;

  await test('Find john.doe user id', async () => {
    const r = await apiGet(`${API}/users`, adminToken);
    const john = r.data.find(u => u.username === 'john.doe');
    assert(john, 'john.doe not found');
    johnId = john.id;
  });

  await test('Get a medicine id for adjustment tests', async () => {
    const r = await apiGet(`${API}/medicines`, adminToken);
    assert(r.data.length > 0, 'No medicines found');
    adjustMedicineId = r.data[0].id;
  });

  await test('Record john.doe current inventory before adjustment', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    const inv = r.data.find(i => i.userId === johnId && i.medicineId === adjustMedicineId);
    quantityBeforeAdjust = inv ? inv.quantity : 0;
    assert(quantityBeforeAdjust >= 0, 'Expected non-negative quantity');
  });

  await test('Admin can ADD inventory to user with note', async () => {
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: johnId,
      medicineId: adjustMedicineId,
      adjustmentType: 'ADD',
      quantity: 10,
      note: 'Restocking for Ward 3 monthly supply',
    }, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.quantity === quantityBeforeAdjust + 10,
      `Expected ${quantityBeforeAdjust + 10}, got ${r.data.quantity}`);
  });

  await test('Admin can REDUCE inventory from user with note', async () => {
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: johnId,
      medicineId: adjustMedicineId,
      adjustmentType: 'REDUCE',
      quantity: 5,
      note: 'Correction for expired stock removal',
    }, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.quantity === quantityBeforeAdjust + 5,
      `Expected ${quantityBeforeAdjust + 5}, got ${r.data.quantity}`);
  });

  await test('Adjust inventory without note returns 400', async () => {
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: johnId,
      medicineId: adjustMedicineId,
      adjustmentType: 'ADD',
      quantity: 1,
    }, adminToken);
    assert(r.status === 400, `Expected 400, got ${r.status}`);
  });

  await test('Adjust inventory with note too short returns 400', async () => {
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: johnId,
      medicineId: adjustMedicineId,
      adjustmentType: 'ADD',
      quantity: 1,
      note: 'hi',
    }, adminToken);
    assert(r.status === 400, `Expected 400, got ${r.status}`);
  });

  await test('Adjust inventory with quantity 0 returns 400', async () => {
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: johnId,
      medicineId: adjustMedicineId,
      adjustmentType: 'ADD',
      quantity: 0,
      note: 'This should fail validation',
    }, adminToken);
    assert(r.status === 400, `Expected 400, got ${r.status}`);
  });

  await test('Reducing more than available returns 409', async () => {
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: johnId,
      medicineId: adjustMedicineId,
      adjustmentType: 'REDUCE',
      quantity: 999999,
      note: 'This should fail due to insufficient stock',
    }, adminToken);
    assert(r.status === 409, `Expected 409, got ${r.status}`);
  });

  await test('Non-admin cannot adjust inventory (401/403)', async () => {
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: johnId,
      medicineId: adjustMedicineId,
      adjustmentType: 'ADD',
      quantity: 1,
      note: 'Should be rejected for non-admin',
    }, userToken);
    assert(r.status === 401 || r.status === 403, `Expected 401 or 403, got ${r.status}`);
  });

  // ── TEARDOWN: Restore john.doe inventory to original ─────────────────
  // Net change so far: +10 ADD then -5 REDUCE = net +5 above original
  // Restore: REDUCE 5 to get back to quantityBeforeAdjust
  await test('[TEARDOWN] Restore john.doe inventory to pre-test level', async () => {
    const currentR = await apiGet(`${API}/inventory`, adminToken);
    const current = currentR.data.find(i => i.userId === johnId && i.medicineId === adjustMedicineId);
    const currentQty = current ? current.quantity : 0;
    if (currentQty === quantityBeforeAdjust) return; // already correct
    const diff = currentQty - quantityBeforeAdjust;
    if (diff > 0) {
      const r = await apiPost(`${API}/inventory/adjust`, {
        userId: johnId,
        medicineId: adjustMedicineId,
        adjustmentType: 'REDUCE',
        quantity: diff,
        note: 'E2E test teardown — restoring original inventory level',
      }, adminToken);
      assert(r.status === 200, `Teardown failed: ${r.status} ${JSON.stringify(r.data)}`);
    } else {
      const r = await apiPost(`${API}/inventory/adjust`, {
        userId: johnId,
        medicineId: adjustMedicineId,
        adjustmentType: 'ADD',
        quantity: Math.abs(diff),
        note: 'E2E test teardown — restoring original inventory level',
      }, adminToken);
      assert(r.status === 200, `Teardown failed: ${r.status} ${JSON.stringify(r.data)}`);
    }
  });

  await test('[VERIFY TEARDOWN] john.doe inventory restored to original', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    const inv = r.data.find(i => i.userId === johnId && i.medicineId === adjustMedicineId);
    const restored = inv ? inv.quantity : 0;
    assert(restored === quantityBeforeAdjust,
      `Expected ${quantityBeforeAdjust}, got ${restored}`);
  });

  // ── Admin: User Management ────────────────────────────────────────────
  console.log('\n-- Admin: User Management');
  await test('Admin can list all users', async () => {
    const r = await apiGet(`${API}/users`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    assert(Array.isArray(r.data), 'Expected array');
    assert(r.data.length >= 3, `Expected at least 3 seeded users, got ${r.data.length}`);
  });
  await test('User list includes expected fields', async () => {
    const r = await apiGet(`${API}/users`, adminToken);
    const u = r.data[0];
    assert(u.username !== undefined, 'Missing username');
    assert(u.fullName !== undefined, 'Missing fullName');
    assert(u.role !== undefined, 'Missing role');
    assert(u.active !== undefined, 'Missing active');
  });
  await test('Non-admin cannot list users (401/403)', async () => {
    const r = await apiGet(`${API}/users`, userToken);
    assert(r.status === 401 || r.status === 403, `Expected 401 or 403, got ${r.status}`);
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
  await test('Non-admin cannot create a user (401/403)', async () => {
    const r = await apiPost(`${API}/users`, {
      username: 'shouldfail', email: 'shouldfail@test.com',
      fullName: 'Should Fail', password: 'TestPass1!',
    }, userToken);
    assert(r.status === 401 || r.status === 403, `Expected 401 or 403, got ${r.status}`);
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

  // ── Admin: Change User Password ───────────────────────────────────────
  console.log('\n-- Admin: Change User Password');
  const tempPassword = 'TempPass@E2E9';
  let johnToken;

  await test('Admin can change john.doe password', async () => {
    const r = await apiPut(`${API}/users/${johnId}/password`, { newPassword: tempPassword }, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
  });
  await test('john.doe can log in with new password', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'john.doe', password: tempPassword });
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    johnToken = r.data.token;
    assert(johnToken && johnToken.length > 20, 'Expected valid token');
  });
  await test('john.doe cannot log in with old password after change', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'john.doe', password: 'User@123' });
    assert(r.status === 401, `Expected 401 for old password, got ${r.status}`);
  });
  await test('Admin change password with short password returns 400', async () => {
    const r = await apiPut(`${API}/users/${johnId}/password`, { newPassword: 'short' }, adminToken);
    assert(r.status === 400, `Expected 400, got ${r.status}`);
  });
  await test('Non-admin cannot change another user password (401/403)', async () => {
    const r = await apiPut(`${API}/users/${johnId}/password`, { newPassword: tempPassword }, userToken);
    assert(r.status === 401 || r.status === 403, `Expected 401 or 403, got ${r.status}`);
  });

  // ── TEARDOWN: Restore john.doe original password ─────────────────────
  await test('[TEARDOWN] Restore john.doe original password', async () => {
    const r = await apiPut(`${API}/users/${johnId}/password`, { newPassword: 'User@123' }, adminToken);
    assert(r.status === 200, `Teardown failed: ${r.status} ${JSON.stringify(r.data)}`);
  });
  await test('[VERIFY TEARDOWN] john.doe can log in with original password', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: 'john.doe', password: 'User@123' });
    assert(r.status === 200, `Expected 200, got ${r.status}`);
  });

  // ── TEARDOWN: Delete the e2e test user created in User Management ─────
  console.log('\n-- Cleanup: Delete e2e test user');
  await test('[TEARDOWN] Delete e2e test user created earlier', async () => {
    if (!createdUserId) return; // skip if create test failed
    const r = await apiDelete(`${API}/users/${createdUserId}`, adminToken);
    assert(r.status === 204, `Expected 204, got ${r.status}`);
  });
  await test('[VERIFY TEARDOWN] Deleted e2e user no longer exists', async () => {
    if (!createdUserId) return;
    const r = await apiGet(`${API}/users`, adminToken);
    const found = r.data.find(u => u.id === createdUserId);
    assert(!found, `Expected user ${createdUserId} to be deleted`);
  });

  // ── Admin: Delete User ────────────────────────────────────────────────
  console.log('\n-- Admin: Delete User');
  const deleteTestUsername = `e2e_del_${Date.now()}`;
  let deleteTestUserId;

  await test('Admin can create a user for deletion test', async () => {
    const r = await apiPost(`${API}/users`, {
      username: deleteTestUsername,
      email: `${deleteTestUsername}@e2e.test`,
      fullName: 'E2E Delete Test',
      password: 'TestPass1!',
      role: 'USER',
    }, adminToken);
    assert(r.status === 201, `Expected 201, got ${r.status}: ${JSON.stringify(r.data)}`);
    deleteTestUserId = r.data.id;
  });

  await test('Admin can add inventory to user before deletion (for cascade test)', async () => {
    if (!deleteTestUserId || !adjustMedicineId) return;
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: deleteTestUserId,
      medicineId: adjustMedicineId,
      adjustmentType: 'ADD',
      quantity: 5,
      note: 'E2E test inventory for delete cascade test',
    }, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
  });

  await test('Admin can delete a user', async () => {
    assert(deleteTestUserId, 'No deleteTestUserId — create test must have passed');
    const r = await apiDelete(`${API}/users/${deleteTestUserId}`, adminToken);
    assert(r.status === 204, `Expected 204, got ${r.status}`);
  });

  await test('Deleted user no longer appears in user list', async () => {
    const r = await apiGet(`${API}/users`, adminToken);
    const found = r.data.find(u => u.id === deleteTestUserId);
    assert(!found, `Expected user ${deleteTestUserId} to be deleted, but still found`);
  });

  await test('Deleted user cannot log in', async () => {
    const r = await apiPost(`${API}/auth/login`, { username: deleteTestUsername, password: 'TestPass1!' });
    assert(r.status === 401, `Expected 401 for deleted user, got ${r.status}`);
  });

  await test('Deleted user inventory is also removed (cascade)', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    const found = r.data.find(i => i.userId === deleteTestUserId);
    assert(!found, `Expected inventory for deleted user to be removed`);
  });

  await test('Delete non-existent user returns 404', async () => {
    const r = await apiDelete(`${API}/users/999999`, adminToken);
    assert(r.status === 404, `Expected 404, got ${r.status}`);
  });

  await test('Non-admin cannot delete a user (401/403)', async () => {
    const r = await apiDelete(`${API}/users/${johnId}`, userToken);
    assert(r.status === 401 || r.status === 403, `Expected 401 or 403, got ${r.status}`);
  });

  // ── Admin: Modify own (admin) inventory ──────────────────────────────
  console.log('\n-- Admin: Modify Own Inventory');

  // Resolve the admin user's id from the users list
  let adminUserId;
  await test('Admin user appears in users list', async () => {
    const r = await apiGet(`${API}/users`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    const adminUser = r.data.find(u => u.username === 'admin');
    assert(adminUser, 'Expected to find admin user in users list');
    adminUserId = adminUser.id;
  });

  let adminInvMedicineId;
  let adminQtyBefore = 0;
  await test('Record admin current inventory level before test', async () => {
    assert(adminUserId, 'No adminUserId — previous test must have passed');
    const r = await apiGet(`${API}/inventory`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    // Use the same medicine as the john.doe adjust tests
    const med = r.data.find(i => i.userId === adminUserId);
    // If admin already has inventory use that medicine, otherwise fall back to adjustMedicineId
    adminInvMedicineId = med ? med.medicineId : adjustMedicineId;
    const existing = r.data.find(i => i.userId === adminUserId && i.medicineId === adminInvMedicineId);
    adminQtyBefore = existing ? existing.quantity : 0;
  });

  await test('Admin can add inventory to own account', async () => {
    assert(adminUserId, 'No adminUserId');
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: adminUserId,
      medicineId: adminInvMedicineId,
      adjustmentType: 'ADD',
      quantity: 3,
      note: 'E2E test — admin adding inventory to own account',
    }, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
  });

  await test('Admin inventory quantity increased after ADD', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    const inv = r.data.find(i => i.userId === adminUserId && i.medicineId === adminInvMedicineId);
    const newQty = inv ? inv.quantity : 0;
    assert(newQty === adminQtyBefore + 3,
      `Expected ${adminQtyBefore + 3}, got ${newQty}`);
  });

  // ── TEARDOWN: Restore admin inventory ───────────────────────────────
  await test('[TEARDOWN] Restore admin inventory to pre-test level', async () => {
    const currentR = await apiGet(`${API}/inventory`, adminToken);
    const current = currentR.data.find(i => i.userId === adminUserId && i.medicineId === adminInvMedicineId);
    const currentQty = current ? current.quantity : 0;
    if (currentQty === adminQtyBefore) return;
    const diff = currentQty - adminQtyBefore;
    const r = await apiPost(`${API}/inventory/adjust`, {
      userId: adminUserId,
      medicineId: adminInvMedicineId,
      adjustmentType: diff > 0 ? 'REDUCE' : 'ADD',
      quantity: Math.abs(diff),
      note: 'E2E test teardown — restoring admin inventory level',
    }, adminToken);
    assert(r.status === 200, `Teardown failed: ${r.status} ${JSON.stringify(r.data)}`);
  });

  await test('[VERIFY TEARDOWN] Admin inventory restored to original', async () => {
    const r = await apiGet(`${API}/inventory`, adminToken);
    const inv = r.data.find(i => i.userId === adminUserId && i.medicineId === adminInvMedicineId);
    const restored = inv ? inv.quantity : 0;
    assert(restored === adminQtyBefore, `Expected ${adminQtyBefore}, got ${restored}`);
  });

  // ── Admin: Reports ────────────────────────────────────────────────────
  console.log('\n-- Admin: Reports');

  await test('Admin can access inventory-by-user report', async () => {
    const r = await apiGet(`${API}/reports/inventory-by-user`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.reportType === 'INVENTORY_BY_USER', `Expected INVENTORY_BY_USER, got ${r.data.reportType}`);
    assert(typeof r.data.content === 'string' && r.data.content.length > 0, 'Expected non-empty content');
    assert(r.data.content.includes('CURRENT MEDICINE STOCK PER USER'), 'Expected report header');
  });

  await test('Admin can access inventory-valuation report', async () => {
    const r = await apiGet(`${API}/reports/inventory-valuation`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.reportType === 'INVENTORY_VALUATION', `Expected INVENTORY_VALUATION, got ${r.data.reportType}`);
    assert(r.data.content.includes('CURRENT MEDICINE STOCK VALUATION'), 'Expected report header');
    assert(r.data.content.includes('TOTAL VALUATION'), 'Expected total valuation line');
  });

  await test("Admin can access today-sales report", async () => {
    const r = await apiGet(`${API}/reports/today-sales`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.reportType === 'TODAY_SALES', `Expected TODAY_SALES, got ${r.data.reportType}`);
    assert(typeof r.data.content === 'string' && r.data.content.length > 0, 'Expected non-empty content');
  });

  await test('Admin can access daily report', async () => {
    const r = await apiGet(`${API}/reports/daily`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.reportType === 'DAILY_REPORT', `Expected DAILY_REPORT, got ${r.data.reportType}`);
    assert(r.data.content.includes('DAILY REPORT'), 'Expected daily report header');
    assert(r.data.content.includes('Shield FX'), 'Expected pharma name as section heading');
    assert(!r.data.content.includes('INVENTORY COUNTS'), 'Should not contain INVENTORY COUNTS heading');
    assert(r.data.content.includes('REGULAR MEDICINE STOCK'), 'Expected regular medicine stock section');
    assert(r.data.content.includes('ADMIN MEDICINE STOCK'), 'Expected admin medicine stock section');
    assert(r.data.content.includes('DAILY TRANSACTION SUMMARY'), 'Expected transactions section');
    assert(r.data.content.includes('Vial 10 ml'), 'Expected 10ml vial in inventory section');
    assert(r.data.content.includes('Vial 5 ml'), 'Expected 5ml vial in inventory section');
    // Admin stock must appear before transactions
    assert(r.data.content.indexOf('ADMIN MEDICINE STOCK') < r.data.content.indexOf('DAILY TRANSACTION SUMMARY'),
      'Expected ADMIN MEDICINE STOCK before DAILY TRANSACTION SUMMARY');
    assert(r.data.content.includes('IST'), 'Expected IST in timestamp');
    // 10ml must appear before 5ml
    assert(r.data.content.indexOf('Vial 10 ml') < r.data.content.indexOf('Vial 5 ml'),
      'Expected 10ml vial before 5ml vial');
  });

  await test('Reports include generatedAt timestamp with IST', async () => {
    const r = await apiGet(`${API}/reports/daily`, adminToken);
    assert(r.data.generatedAt && r.data.generatedAt.endsWith('IST'), `Expected generatedAt ending with IST, got: ${r.data.generatedAt}`);
  });

  await test('Non-admin cannot access reports (401/403)', async () => {
    const r = await apiGet(`${API}/reports/inventory-by-user`, userToken);
    assert(r.status === 401 || r.status === 403, `Expected 401 or 403, got ${r.status}`);
  });

  await test('Unauthenticated cannot access reports (401)', async () => {
    const r = await apiGet(`${API}/reports/inventory-valuation`);
    assert(r.status === 401 || r.status === 403, `Expected 401, got ${r.status}`);
  });

  // ── Multi-Screenshot Transactions ────────────────────────────────────
  console.log('\n-- Multi-Screenshot Transactions');

  // Find a medicine the user (john.doe) has access to
  let txMedicineId;
  await test('User has available inventory to submit against', async () => {
    const r = await apiGet(`${API}/inventory/available`, userToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    const item = r.data.find(i => i.quantity >= 2 && i.inventoryType === 'REGULAR_MEDICINE_STOCK');
    assert(item, 'No REGULAR_MEDICINE_STOCK inventory item with qty >= 2 found for user');
    txMedicineId = item.medicineId;
  });

  let singleShotTxId;
  await test('User can submit transaction with 1 screenshot', async () => {
    if (!txMedicineId) return;
    const r = await apiPostForm(`${API}/transactions`, {
      medicineId: String(txMedicineId),
      quantity: '1',
      notes: 'E2E single-screenshot test',
      inventoryType: 'REGULAR_MEDICINE_STOCK',
      screenshots: [makeFakePng('1')],
    }, userToken);
    assert(r.status === 201, `Expected 201, got ${r.status}: ${JSON.stringify(r.data)}`);
    assert(r.data.id, 'Missing transaction id');
    singleShotTxId = r.data.id;
  });

  await test('Single-screenshot transaction response has screenshots array with 1 entry', async () => {
    if (!singleShotTxId) return;
    const r = await apiGet(`${API}/transactions/my`, userToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    const tx = r.data.find(t => t.id === singleShotTxId);
    assert(tx, `Transaction #${singleShotTxId} not found in user transactions`);
    assert(Array.isArray(tx.screenshots), 'screenshots field should be an array');
    assert(tx.screenshots.length === 1, `Expected 1 screenshot, got ${tx.screenshots.length}`);
    assert(tx.screenshots[0].data, 'Screenshot entry missing data field');
    assert(tx.screenshots[0].mimeType === 'image/png', `Expected image/png, got ${tx.screenshots[0].mimeType}`);
  });

  let multiShotTxId;
  await test('User can submit transaction with 2 screenshots', async () => {
    if (!txMedicineId) return;
    const r = await apiPostForm(`${API}/transactions`, {
      medicineId: String(txMedicineId),
      quantity: '1',
      notes: 'E2E multi-screenshot test two files',
      inventoryType: 'REGULAR_MEDICINE_STOCK',
      screenshots: [makeFakePng('A'), makeFakePng('B')],
    }, userToken);
    assert(r.status === 201, `Expected 201, got ${r.status}: ${JSON.stringify(r.data)}`);
    multiShotTxId = r.data.id;
  });

  await test('Multi-screenshot transaction response has screenshots array with 2 entries', async () => {
    if (!multiShotTxId) return;
    const r = await apiGet(`${API}/transactions/my`, userToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    const tx = r.data.find(t => t.id === multiShotTxId);
    assert(tx, `Transaction #${multiShotTxId} not found in user transactions`);
    assert(Array.isArray(tx.screenshots), 'screenshots field should be an array');
    assert(tx.screenshots.length === 2, `Expected 2 screenshots, got ${tx.screenshots.length}`);
    assert(tx.screenshots[0].data && tx.screenshots[1].data, 'Both screenshot entries must have data');
  });

  await test('Admin sees multi-screenshot transaction with both screenshots', async () => {
    if (!multiShotTxId) return;
    const r = await apiGet(`${API}/transactions`, adminToken);
    assert(r.status === 200, `Expected 200, got ${r.status}`);
    const tx = r.data.find(t => t.id === multiShotTxId);
    assert(tx, `Transaction #${multiShotTxId} not found in admin transaction list`);
    assert(Array.isArray(tx.screenshots) && tx.screenshots.length === 2,
      `Expected 2 screenshots in admin view, got ${tx.screenshots?.length}`);
  });

  await test('Submitting transaction with no screenshots returns 400', async () => {
    if (!txMedicineId) return;
    const r = await apiPostForm(`${API}/transactions`, {
      medicineId: String(txMedicineId),
      quantity: '1',
      notes: 'E2E no-screenshot test should fail',
      inventoryType: 'REGULAR',
    }, userToken);
    assert(r.status === 400, `Expected 400, got ${r.status}: ${JSON.stringify(r.data)}`);
  });

  // TEARDOWN: Reject created test transactions so inventory is freed
  await test('[TEARDOWN] Admin rejects screenshot test transactions', async () => {
    for (const txId of [singleShotTxId, multiShotTxId]) {
      if (!txId) continue;
      const r = await apiPost(`${API}/transactions/${txId}/approve`, { approved: false }, adminToken);
      assert(r.status === 200, `Teardown reject failed for tx #${txId}: ${r.status}`);
    }
  });

  // ── Summary ───────────────────────────────────────────────────────────
  console.log(`\n${'='.repeat(40)}`);
  console.log(`Results: ${passed} passed, ${failed} failed`);
  console.log('='.repeat(40));
  if (failed > 0) process.exit(1);
}

run().catch(e => { console.error(e); process.exit(1); });
