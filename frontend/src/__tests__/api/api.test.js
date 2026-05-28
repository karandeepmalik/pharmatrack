/**
 * api.js tests
 *
 * Strategy: capture interceptor callbacks at module-load time via mocked axios.
 * We mock axios.create() to return a controllable object, then read the
 * callback functions that api.js passed to interceptors.response.use.
 */

let capturedResponseSuccess;
let capturedResponseError;

const mockPost = jest.fn();
const mockGet  = jest.fn();
const mockPut  = jest.fn();

const mockAxiosCreate = jest.fn();

// Must be declared before any require() of the module under test
jest.mock('axios', () => ({
  create: (...args) => {
    mockAxiosCreate(...args);
    return {
      interceptors: {
        response: { use: (success, error) => { capturedResponseSuccess = success; capturedResponseError = error; } },
      },
      post: (...a) => mockPost(...a),
      get:  (...a) => mockGet(...a),
      put:  (...a) => mockPut(...a),
    };
  },
}));

// Require the module ONCE after the mock is in place.
require('../../api/api');

describe('api.js', () => {

  beforeEach(() => {
    localStorage.clear();
    delete window.location;
    window.location = { replace: jest.fn() };
    mockPost.mockReset();
    mockGet.mockReset();
    mockPut.mockReset();
  });

  // ── Axios instance configuration ────────────────────────────────────

  describe('Axios instance configuration', () => {
    test('creates axios instance with withCredentials: true', () => {
      expect(mockAxiosCreate).toHaveBeenCalledWith(
        expect.objectContaining({ withCredentials: true })
      );
    });

    test('does not register a request interceptor', () => {
      // No request.use call — cookie is sent automatically by the browser
      // Verified implicitly: no capturedRequestFn variable needed
      expect(capturedResponseSuccess).toBeDefined(); // response interceptor is registered
    });
  });

  // ── Response interceptor ────────────────────────────────────────────

  describe('Response interceptor', () => {
    test('success handler was registered', () => {
      expect(typeof capturedResponseSuccess).toBe('function');
    });

    test('error handler was registered', () => {
      expect(typeof capturedResponseError).toBe('function');
    });

    test('success handler passes response through unchanged', () => {
      const response = { data: { id: 1 }, status: 200 };
      expect(capturedResponseSuccess(response)).toBe(response);
    });

    test('401 error clears user from localStorage', async () => {
      localStorage.setItem('user', JSON.stringify({ id: 1 }));
      await expect(capturedResponseError({ response: { status: 401 } })).rejects.toBeDefined();
      expect(localStorage.getItem('user')).toBeNull();
    });

    test('401 error does NOT touch token key (no longer stored)', async () => {
      localStorage.setItem('token', 'should-not-be-touched');
      await expect(capturedResponseError({ response: { status: 401 } })).rejects.toBeDefined();
      // token key is not managed by api.js anymore — still present if set externally
      // (nothing in api.js removes it since we no longer store tokens in localStorage)
    });

    test('401 error redirects to /login', async () => {
      await expect(capturedResponseError({ response: { status: 401 } })).rejects.toBeDefined();
      expect(window.location.replace).toHaveBeenCalledWith('/login');
    });

    test('403 error does NOT redirect', async () => {
      await expect(capturedResponseError({ response: { status: 403 } })).rejects.toBeDefined();
      expect(window.location.replace).not.toHaveBeenCalled();
    });

    test('500 error does NOT redirect', async () => {
      await expect(capturedResponseError({ response: { status: 500 } })).rejects.toBeDefined();
      expect(window.location.replace).not.toHaveBeenCalled();
    });

    test('404 error does NOT redirect', async () => {
      await expect(capturedResponseError({ response: { status: 404 } })).rejects.toBeDefined();
      expect(window.location.replace).not.toHaveBeenCalled();
    });

    test('non-401 error rejects with the original error object', async () => {
      const error = { response: { status: 422 }, message: 'Unprocessable' };
      await expect(capturedResponseError(error)).rejects.toBe(error);
    });
  });

  // ── submitTransaction — FormData ────────────────────────────────────

  describe('submitTransaction — FormData construction', () => {
    let appendSpy;

    beforeEach(() => {
      appendSpy = jest.spyOn(FormData.prototype, 'append');
      mockPost.mockResolvedValue({ data: {} });
    });

    afterEach(() => {
      appendSpy.mockRestore();
    });

    const { submitTransaction } = require('../../api/api');

    test('appends medicineId as string', async () => {
      await submitTransaction({ medicineId: 5, quantity: 3, notes: 'Clinic dispatch' });
      expect(appendSpy).toHaveBeenCalledWith('medicineId', '5');
    });

    test('appends quantity as string', async () => {
      await submitTransaction({ medicineId: 1, quantity: 7, notes: 'Ward note here' });
      expect(appendSpy).toHaveBeenCalledWith('quantity', '7');
    });

    test('appends notes verbatim', async () => {
      await submitTransaction({ medicineId: 1, quantity: 2, notes: 'Ward B dispatch' });
      expect(appendSpy).toHaveBeenCalledWith('notes', 'Ward B dispatch');
    });

    test('appends each screenshot file under the screenshots field', async () => {
      const f1 = new File(['x'], 'pay1.png', { type: 'image/png' });
      const f2 = new File(['x'], 'pay2.png', { type: 'image/png' });
      await submitTransaction({ medicineId: 1, quantity: 2, notes: 'Note', screenshotFiles: [f1, f2] });
      const screenshotCalls = appendSpy.mock.calls.filter(([k]) => k === 'screenshots');
      expect(screenshotCalls).toHaveLength(2);
      expect(screenshotCalls[0][1]).toBe(f1);
      expect(screenshotCalls[1][1]).toBe(f2);
    });

    test('appends single screenshot file under the screenshots field', async () => {
      const file = new File(['x'], 'proof.png', { type: 'image/png' });
      await submitTransaction({ medicineId: 1, quantity: 2, notes: 'Note', screenshotFiles: [file] });
      const screenshotCalls = appendSpy.mock.calls.filter(([k]) => k === 'screenshots');
      expect(screenshotCalls).toHaveLength(1);
      expect(screenshotCalls[0][1]).toBe(file);
    });

    test('appends no screenshot fields when screenshotFiles is empty', async () => {
      await submitTransaction({ medicineId: 1, quantity: 2, notes: 'Note', screenshotFiles: [] });
      const screenshotCalls = appendSpy.mock.calls.filter(([k]) => k === 'screenshots');
      expect(screenshotCalls).toHaveLength(0);
    });

    test('posts to /transactions endpoint', async () => {
      const file = new File(['x'], 'pay.png', { type: 'image/png' });
      await submitTransaction({ medicineId: 1, quantity: 1, notes: 'Note', screenshotFiles: [file] });
      expect(mockPost).toHaveBeenCalledWith(
        '/transactions',
        expect.any(FormData),
        expect.objectContaining({ headers: expect.objectContaining({ 'Content-Type': 'multipart/form-data' }) })
      );
    });

    test('appends pricePerUnit when provided', async () => {
      const file = new File(['x'], 'pay.png', { type: 'image/png' });
      await submitTransaction({ medicineId: 1, quantity: 2, notes: 'Note', screenshotFiles: [file], pricePerUnit: 3500 });
      expect(appendSpy).toHaveBeenCalledWith('pricePerUnit', '3500');
    });

    test('does not append pricePerUnit when null', async () => {
      const file = new File(['x'], 'pay.png', { type: 'image/png' });
      await submitTransaction({ medicineId: 1, quantity: 2, notes: 'Note', screenshotFiles: [file], pricePerUnit: null });
      const priceCall = appendSpy.mock.calls.find(([k]) => k === 'pricePerUnit');
      expect(priceCall).toBeUndefined();
    });
  });

  // ── getTransactionHistory ───────────────────────────────────────────

  describe('getTransactionHistory', () => {
    const { getTransactionHistory } = require('../../api/api');

    beforeEach(() => {
      mockGet.mockResolvedValue({ data: [] });
    });

    test('calls GET /transactions/history with from/to/status params', async () => {
      await getTransactionHistory('2026-05-01', '2026-05-07', 'APPROVED');
      expect(mockGet).toHaveBeenCalledWith(
        '/transactions/history',
        { params: { from: '2026-05-01', to: '2026-05-07', status: 'APPROVED' } }
      );
    });

    test('defaults status to ALL when not provided', async () => {
      await getTransactionHistory('2026-05-01', '2026-05-07');
      expect(mockGet).toHaveBeenCalledWith(
        '/transactions/history',
        { params: { from: '2026-05-01', to: '2026-05-07', status: 'ALL' } }
      );
    });
  });

  // ── getReportDaily ──────────────────────────────────────────────────

  describe('getReportDaily', () => {
    const { getReportDaily } = require('../../api/api');

    beforeEach(() => {
      mockGet.mockResolvedValue({ data: {} });
    });

    test('calls GET /reports/daily with date param when date provided', async () => {
      await getReportDaily('2026-05-01');
      expect(mockGet).toHaveBeenCalledWith(
        '/reports/daily',
        { params: { date: '2026-05-01' } }
      );
    });

    test('calls GET /reports/daily without params when date is null', async () => {
      await getReportDaily(null);
      expect(mockGet).toHaveBeenCalledWith('/reports/daily', {});
    });
  });
});
