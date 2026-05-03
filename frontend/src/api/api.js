import axios from 'axios';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
});

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
export const register = (data)        => api.post('/auth/register', data);
export const getMe    = ()            => api.get('/users/me');

// ── Inventory — user ───────────────────────────────────────────────────
export const getAvailableInventory = () => api.get('/inventory/available');

// ── Inventory — admin ─────────────────────────────────────────────────
export const getAdminInventory = ()     => api.get('/inventory');
export const adjustInventory   = (data) => api.post('/inventory/adjust', data);

// ── Medicines ──────────────────────────────────────────────────────────
export const getMedicines    = ()     => api.get('/medicines');
export const createMedicine  = (data) => api.post('/medicines', data);

// ── Pharma companies ───────────────────────────────────────────────────
export const getPharmaCompanies = () => api.get('/pharma');

// ── Transactions ───────────────────────────────────────────────────────
export const submitTransaction = ({ medicineId, quantity, notes, screenshotFile }) => {
  const form = new FormData();
  form.append('medicineId', String(medicineId));
  form.append('quantity',   String(quantity));
  form.append('notes',      notes);
  form.append('screenshot', screenshotFile);
  return api.post('/transactions', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const getMyTransactions  = ()           => api.get('/transactions/my');
export const getAllTransactions  = ()           => api.get('/transactions');
export const approveTransaction  = (id, data)  => api.post(`/transactions/${id}/approve`, data);

// ── Users ──────────────────────────────────────────────────────────────
export const getUsers            = ()           => api.get('/users');
export const createUser          = (data)       => api.post('/users', data);
export const toggleUser          = (id)         => api.post(`/users/${id}/toggle`);
export const updateUser          = (id, data)   => api.put(`/users/${id}`, data);
export const changePassword      = (data)       => api.put('/users/me/password', data);
export const adminChangePassword = (id, data)   => api.put(`/users/${id}/password`, data);

export default api;
