import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

// ── Request interceptor: attach JWT ───────────────────────────────────
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ── Response interceptor: global error handling ───────────────────────
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;

    // Token expired or invalid — redirect to login
    if (status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      // Use location.replace so back-button doesn't return to the protected page
      window.location.replace('/login');
    }

    // Propagate the error so individual call sites can still handle it
    return Promise.reject(error);
  }
);

// ── Auth ───────────────────────────────────────────────────────────────
export const login    = (credentials) => api.post('/auth/login', credentials);
export const register = (data)        => api.post('/auth/register', data);
export const getMe    = ()            => api.get('/users/me');

// ── Inventory ──────────────────────────────────────────────────────────
export const getAvailableInventory = () => api.get('/inventory/available');
export const addInventory          = (data) => api.post('/inventory', data);
export const getAdminInventory     = ()     => api.get('/inventory');

// ── Medicines ──────────────────────────────────────────────────────────
export const getMedicines    = ()     => api.get('/medicines');
export const createMedicine  = (data) => api.post('/medicines', data);

// ── Pharma companies ───────────────────────────────────────────────────
export const getPharmaCompanies = () => api.get('/pharma');

// ── Transactions ───────────────────────────────────────────────────────

/**
 * Submit an inventory adjustment (dispatch) transaction.
 * Sends multipart/form-data so an optional payment screenshot can be attached.
 *
 * @param {Object}    params
 * @param {number}    params.medicineId
 * @param {number}    params.quantity
 * @param {string}    params.notes
 * @param {File|null} params.screenshotFile
 */
export const submitTransaction = ({ medicineId, quantity, notes, screenshotFile = null }) => {
  const form = new FormData();
  form.append('medicineId', String(medicineId));
  form.append('quantity',   String(quantity));
  form.append('notes',      notes);
  if (screenshotFile) form.append('screenshot', screenshotFile);
  return api.post('/transactions', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const getMyTransactions  = ()           => api.get('/transactions/my');
export const getAllTransactions  = ()           => api.get('/transactions');
export const approveTransaction  = (id, data)  => api.post(`/transactions/${id}/approve`, data);

// ── Users ──────────────────────────────────────────────────────────────
export const getUsers      = ()           => api.get('/users');
export const updateUser    = (id, data)   => api.put(`/users/${id}`, data);
export const changePassword = (data)      => api.put('/users/me/password', data);

export default api;
