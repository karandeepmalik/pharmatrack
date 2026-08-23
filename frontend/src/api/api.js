import axios from 'axios';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
});

// Auth is via the Authorization header, populated from the token in the login response body.
// Frontend and backend are separate origins (different Cloud Run services), so a cookie-based
// approach doesn't work here: a SameSite=Lax cookie is never sent on a cross-origin XHR/fetch,
// only a top-level GET navigation — there used to be a cookie set alongside this for
// "defence-in-depth," but it was dead code in this deployment topology, so it was removed.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    // A 401 from /auth/login itself means "wrong credentials", not "session expired" —
    // redirecting here would hard-reload the login page and wipe out the error message
    // the Login component is about to render before the user ever sees it.
    const isLoginAttempt = error.config?.url?.includes('/auth/login');
    if (status === 401 && !isLoginAttempt) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.replace('/login');
    }
    return Promise.reject(error);
  }
);

// ── Auth ───────────────────────────────────────────────────────────────
export const login    = (credentials) => api.post('/auth/login', credentials);
// Takes the token explicitly rather than relying on the request interceptor to pick it up from
// localStorage — AuthContext.logout() clears localStorage before firing this call (for instant
// UI feedback), so by the time the interceptor would read it, it's already gone.
export const logout   = (token) =>
  api.post('/auth/logout', {}, token ? { headers: { Authorization: `Bearer ${token}` } } : {});

// ── Medicine stock — user ───────────────────────────────────────────────
export const getAvailableMedicineStock = () => api.get('/medicine-stock/available');

// ── Medicine stock — admin ──────────────────────────────────────────────
export const getAdminMedicineStock = ()     => api.get('/medicine-stock');
export const adjustMedicineStock   = (data) => api.post('/medicine-stock/adjust', data);

// ── Medicines ──────────────────────────────────────────────────────────
export const getMedicines    = ()     => api.get('/medicines');
export const createMedicine  = (data) => api.post('/medicines', data);

// ── Pharma companies ───────────────────────────────────────────────────
export const getPharmaCompanies    = ()     => api.get('/medicines/companies');
export const createPharmaCompany   = (data) => api.post('/medicines/companies', data);

// ── Transactions ───────────────────────────────────────────────────────
export const submitTransaction = ({ medicineId, quantity, notes, screenshotFiles, pricePerUnit, medicineStockType, submittedDate }) => {
  const form = new FormData();
  form.append('medicineId', String(medicineId));
  form.append('quantity',   String(quantity));
  form.append('notes',      notes);
  (screenshotFiles || []).forEach((file) => form.append('screenshots', file));
  if (pricePerUnit != null) form.append('pricePerUnit', String(pricePerUnit));
  if (medicineStockType) form.append('medicineStockType', medicineStockType);
  if (submittedDate) form.append('submittedDate', submittedDate);
  return api.post('/transactions', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const getMyTransactions  = (page = 0, size = 20, status = 'ALL', medicineId, notes) =>
  api.get('/transactions/my', { params: { page, size, status, medicineId, notes } });
export const getAllTransactions  = (page = 0, size = 20, status = 'ALL') => api.get('/transactions',     { params: { page, size, status } });
export const approveTransaction  = (id, data)  => api.post(`/transactions/${id}/approve`, data);

/**
 * Fetch a page of transaction history for a date range. Filters are applied server-side
 * (against the full matching set, not just whatever page happens to be loaded), so passing
 * username/medicineId/notes here is safe to combine with pagination.
 * @param {string} from       ISO date string YYYY-MM-DD (inclusive)
 * @param {string} to         ISO date string YYYY-MM-DD (inclusive)
 * @param {string} status     ALL | APPROVED | REJECTED  (default ALL)
 * @param {number} page       zero-based page number (default 0)
 * @param {number} size       page size (default 10)
 * @param {string} [username]   optional exact submittedBy username filter
 * @param {number} [medicineId] optional exact medicine filter
 * @param {string} [notes]      optional case-insensitive notes substring filter
 */
export const getTransactionHistory = (from, to, status = 'ALL', page = 0, size = 10, username, medicineId, notes) =>
  api.get('/transactions/history', { params: { from, to, status, page, size, username, medicineId, notes } });

export const deleteTransaction  = (id)      => api.delete(`/transactions/${id}`);
export const deleteMyTransaction = (id)     => api.delete(`/transactions/my/${id}`);

/**
 * Admin edit of a past dispatch record. `notes` is always required — it's the audit trail
 * for a quantity/stock-type correction, since there's no separate adjustment-ledger entry
 * recorded for it. `quantity`/`medicineStockType`/`pricePerUnit` are optional (omit to leave
 * unchanged); `screenshotFiles`, if provided non-empty, replaces the existing screenshot set
 * entirely.
 */
export const updateTransaction  = (id, { notes, quantity, medicineStockType, pricePerUnit, screenshotFiles }) => {
  const form = new FormData();
  form.append('notes', notes);
  if (quantity != null) form.append('quantity', String(quantity));
  if (medicineStockType) form.append('medicineStockType', medicineStockType);
  if (pricePerUnit != null) form.append('pricePerUnit', String(pricePerUnit));
  (screenshotFiles || []).forEach((file) => form.append('screenshots', file));
  return api.patch(`/transactions/${id}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

// ── Users ──────────────────────────────────────────────────────────────
export const getUsers            = ()           => api.get('/users');
export const createUser          = (data)       => api.post('/users', data);
export const toggleUser          = (id)         => api.post(`/users/${id}/toggle`);
export const deleteUser          = (id)         => api.delete(`/users/${id}`);
export const adminChangePassword = (id, data)   => api.put(`/users/${id}/password`, data);

// ── Medicine stock adjustments (admin) ──────────────────────────────────
export const getMedicineStockAdjustments    = (from, to) =>
    api.get('/medicine-stock/adjustments', { params: { from, to } });
export const deleteMedicineStockAdjustment  = (id) =>
    api.delete(`/medicine-stock/adjustments/${id}`);

// ── Reports ────────────────────────────────────────────────────────────
export const getReportMedicineStockByUser    = ()      => api.get('/reports/medicine-stock-by-user');
export const getReportMedicineStockValuation = (date = null) =>
    api.get('/reports/medicine-stock-valuation', date ? { params: { date } } : {});
export const getReportTodaySales         = (from, to, username, medicineId) =>
    api.get('/reports/today-sales', { params: { from, to, username, medicineId } });

/**
 * Fetch the daily report.
 * @param {string|null} date  ISO date string YYYY-MM-DD, or null for today
 */
export const getReportDaily = (date) =>
  api.get('/reports/daily', date ? { params: { date } } : {});

export const getReportSalesGraph = (period, from, to, medicineStockType) =>
  api.get('/reports/sales-graph', { params: { period, from, to, medicineStockType } });

// ── Telemetry ──────────────────────────────────────────────────────────
export const postTelemetryEvent = (eventName, page, properties = {}) =>
  api.post('/telemetry', { eventName, page, properties });

export default api;
