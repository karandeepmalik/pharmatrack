/**
 * api.js tests
 *
 * Strategy: capture interceptor callbacks at module-load time via mocked axios.
 * We mock axios.create() to return a controllable object, then read the
 * callback functions that api.js passed to interceptors.request.use and
 * interceptors.response.use.
 */

let capturedRequestFn;
let capturedResponseSuccess;
let capturedResponseError;

const mockPost = jest.fn();
const mockGet  = jest.fn();
const mockPut  = jest.fn();

// Must be declared before any require() of the module under test
jest.mock('axios', () => ({
  create: jest.fn(() => ({
    interceptors: {
      request:  { use: (fn)               => { capturedRequestFn = fn; } },
      response: { use: (success, error)   => { capturedResponseSuccess = success; capturedResponseError = error; } },
    },
    post: (...args) => mockPost(...args),
    get:  (...args) => mockGet(...args),
    put:  (...args) => mockPut(...args),
  })),
}));

// Require the module ONCE after the mock is in place.
// The module runs synchronously, so capturedRequestFn etc are set by this line.
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

  // ── Request interceptor ─────────────────────────────────────────────

  describe('Request interceptor', () => {
    test('interceptor function was registered', () => {
      expect(typeof capturedRequestFn).toBe('function');
    });

    test('attaches Bearer token when token exists in localStorage', () => {
      localStorage.setItem('token', 'my-jwt-token');
      const config = { headers: {} };
      const result = capturedRequestFn(config);
      expect(result.headers.Authorization).toBe('Bearer my-jwt-token');
    });

    test('does not set Authorization when no token in localStorage', () => {
      const config = { headers: {} };
      const result = capturedRequestFn(config);
      expect(result.headers.Authorization).toBeUndefined();
    });

    test('returns config object with all original fields intact', () => {
      localStorage.setItem('token', 'tok');
      const config = { headers: {}, url: '/test', method: 'GET', timeout: 5000 };
      const result = capturedRequestFn(config);
      expect(result.url).toBe('/test');
      expect(result.method).toBe('GET');
      expect(result.timeout).toBe(5000);
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

    test('401 error clears token from localStorage', async () => {
      localStorage.setItem('token', 'expired');
      await expect(capturedResponseError({ response: { status: 401 } })).rejects.toBeDefined();
      expect(localStorage.getItem('token')).toBeNull();
    });

    test('401 error clears user from localStorage', async () => {
      localStorage.setItem('user', JSON.stringify({ id: 1 }));
      await expect(capturedResponseError({ response: { status: 401 } })).rejects.toBeDefined();
      expect(localStorage.getItem('user')).toBeNull();
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

    test('appends screenshot file when provided', async () => {
      const file = new File(['x'], 'pay.png', { type: 'image/png' });
      await submitTransaction({ medicineId: 1, quantity: 2, notes: 'Note', screenshotFile: file });
      expect(appendSpy).toHaveBeenCalledWith('screenshot', file);
    });

    test('screenshot is always appended (mandatory field)', async () => {
      const file = new File(['x'], 'proof.png', { type: 'image/png' });
      await submitTransaction({ medicineId: 1, quantity: 2, notes: 'Note', screenshotFile: file });
      const screenshotCalls = appendSpy.mock.calls.filter(([k]) => k === 'screenshot');
      expect(screenshotCalls).toHaveLength(1);
      expect(screenshotCalls[0][1]).toBe(file);
    });

    test('posts to /transactions endpoint', async () => {
      const file = new File(['x'], 'pay.png', { type: 'image/png' });
      await submitTransaction({ medicineId: 1, quantity: 1, notes: 'Note', screenshotFile: file });
      expect(mockPost).toHaveBeenCalledWith(
        '/transactions',
        expect.any(FormData),
        expect.objectContaining({ headers: expect.objectContaining({ 'Content-Type': 'multipart/form-data' }) })
      );
    });
  });
});
