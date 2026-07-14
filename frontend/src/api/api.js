import axios from 'axios';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

// Send token via Authorization header — works in all environments including cross-domain.
// The HttpOnly cookie set on login provides defence-in-depth for same-domain deployments.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    if (status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.replace('/login');
    }
    return Promise.reject(error);
  }
);

// ── Auth ───────────────────────────────────────────────────────────────
export const login    = (credentials) => api.post('/auth/login', credentials);
export const logout   = ()            => api.post('/auth/logout');

// ── Inventory — user ───────────────────────────────────────────────────
export const getAvailableInventory = () => api.get('/inventory/available');

// ── Inventory — admin ─────────────────────────────────────────────────
export const getAdminInventory = ()     => api.get('/inventory');
export const adjustInventory   = (data) => api.post('/inventory/adjust', data);

// ── Medicines ──────────────────────────────────────────────────────────
export const getMedicines    = ()     => api.get('/medicines');
export const createMedicine  = (data) => api.post('/medicines', data);

// ── Pharma companies ───────────────────────────────────────────────────
export const getPharmaCompanies    = ()     => api.get('/medicines/companies');
export const createPharmaCompany   = (data) => api.post('/medicines/companies', data);

// ── Transactions ───────────────────────────────────────────────────────
export const submitTransaction = ({ medicineId, quantity, notes, screenshotFiles, pricePerUnit, inventoryType, submittedDate }) => {
  const form = new FormData();
  form.append('medicineId', String(medicineId));
  form.append('quantity',   String(quantity));
  form.append('notes',      notes);
  (screenshotFiles || []).forEach((file) => form.append('screenshots', file));
  if (pricePerUnit != null) form.append('pricePerUnit', String(pricePerUnit));
  if (inventoryType) form.append('inventoryType', inventoryType);
  if (submittedDate) form.append('submittedDate', submittedDate);
  return api.post('/transactions', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const getMyTransactions  = (page = 0, size = 20)              => api.get('/transactions/my',  { params: { page, size } });
export const getAllTransactions  = (page = 0, size = 20, status = 'ALL') => api.get('/transactions',     { params: { page, size, status } });
export const approveTransaction  = (id, data)  => api.post(`/transactions/${id}/approve`, data);

/**
 * Fetch transaction history for a date range.
 * @param {string} from   ISO date string YYYY-MM-DD (inclusive)
 * @param {string} to     ISO date string YYYY-MM-DD (inclusive)
 * @param {string} status ALL | APPROVED | REJECTED  (default ALL)
 */
export const getTransactionHistory = (from, to, status = 'ALL') =>
  api.get('/transactions/history', { params: { from, to, status } });

export const deleteTransaction  = (id)      => api.delete(`/transactions/${id}`);
export const deleteMyTransaction = (id)     => api.delete(`/transactions/my/${id}`);
export const updateTransaction  = (id, data) => api.patch(`/transactions/${id}`, data);

// ── Users ──────────────────────────────────────────────────────────────
export const getUsers            = ()           => api.get('/users');
export const createUser          = (data)       => api.post('/users', data);
export const toggleUser          = (id)         => api.post(`/users/${id}/toggle`);
export const deleteUser          = (id)         => api.delete(`/users/${id}`);
export const adminChangePassword = (id, data)   => api.put(`/users/${id}/password`, data);

// ── Inventory adjustments (admin) ─────────────────────────────────────
export const getInventoryAdjustments    = (from, to) =>
    api.get('/inventory/adjustments', { params: { from, to } });
export const deleteInventoryAdjustment  = (id) =>
    api.delete(`/inventory/adjustments/${id}`);

// ── Reports ────────────────────────────────────────────────────────────
export const getReportInventoryByUser    = ()      => api.get('/reports/inventory-by-user');
export const getReportInventoryValuation = (date = null) =>
    api.get('/reports/inventory-valuation', date ? { params: { date } } : {});
export const getReportTodaySales         = (from, to) => api.get('/reports/today-sales', { params: { from, to } });

/**
 * Fetch the daily report.
 * @param {string|null} date  ISO date string YYYY-MM-DD, or null for today
 */
export const getReportDaily = (date) =>
  api.get('/reports/daily', date ? { params: { date } } : {});

export const getReportSalesGraph = (period, from, to) =>
  api.get('/reports/sales-graph', { params: { period, from, to } });

// ── Telemetry ──────────────────────────────────────────────────────────
export const postTelemetryEvent = (eventName, page, properties = {}) =>
  api.post('/telemetry', { eventName, page, properties });

export default api;
