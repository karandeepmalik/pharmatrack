import React from 'react';
import { renderHook, act, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from '../../context/AuthContext';
import * as api from '../../api/api';

jest.mock('../../api/api');

const wrapper = ({ children }) => <AuthProvider>{children}</AuthProvider>;

beforeEach(() => {
  jest.clearAllMocks();
  localStorage.clear();
});

describe('AuthContext — initial load', () => {
  test('starts logged out when localStorage has no stored user', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.user).toBeNull();
  });

  test('restores the stored user on mount', () => {
    localStorage.setItem('user', JSON.stringify({ username: 'john.doe', fullName: 'John Doe', role: 'USER' }));
    localStorage.setItem('token', 'jwt-token');

    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.user).toEqual({ username: 'john.doe', fullName: 'John Doe', role: 'USER' });
  });

  // Regression: localStorage persists across reloads, so an unguarded JSON.parse on a corrupted
  // 'user' entry (partial write, manual tampering, a stale shape from an older app version)
  // would crash on every single mount forever — not a one-off error a reload could fix. Treat
  // it as logged-out and clear it, the same way an expired/malformed JWT is treated as
  // "unauthenticated" server-side rather than crashing the request.
  test('a corrupted stored user value does not crash the app and clears itself', () => {
    localStorage.setItem('user', '{not valid json');
    localStorage.setItem('token', 'some-token');

    let result;
    expect(() => {
      ({ result } = renderHook(() => useAuth(), { wrapper }));
    }).not.toThrow();

    expect(result.current.user).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(localStorage.getItem('token')).toBeNull();
  });
});

describe('AuthContext — login', () => {
  test('sets user state and persists user/token to localStorage', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });

    act(() => {
      result.current.login({ username: 'admin', fullName: 'Admin User', role: 'ADMIN' }, 'jwt-token');
    });

    expect(result.current.user).toEqual({ username: 'admin', fullName: 'Admin User', role: 'ADMIN' });
    expect(result.current.isAdmin).toBe(true);
    expect(localStorage.getItem('token')).toBe('jwt-token');
    expect(JSON.parse(localStorage.getItem('user'))).toEqual({ username: 'admin', fullName: 'Admin User', role: 'ADMIN' });
  });
});

describe('AuthContext — logout', () => {
  test('clears user state and localStorage synchronously, without waiting for the API call', () => {
    // Never resolves — if logout() awaited this before clearing state, the assertions below
    // (made immediately after calling logout(), before this promise could ever settle) would
    // fail. This is the exact bug: Sign Out visibly doing nothing until a slow/high-latency
    // /auth/logout request finished.
    api.logout.mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useAuth(), { wrapper });

    act(() => {
      result.current.login({ username: 'john.doe', fullName: 'John Doe', role: 'USER' }, 'jwt-token');
    });
    expect(result.current.user).not.toBeNull();

    act(() => {
      result.current.logout();
    });

    expect(result.current.user).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(localStorage.getItem('token')).toBeNull();
  });

  test('still calls the logout API in the background', () => {
    api.logout.mockResolvedValue({});
    const { result } = renderHook(() => useAuth(), { wrapper });

    act(() => {
      result.current.login({ username: 'john.doe', fullName: 'John Doe', role: 'USER' }, 'jwt-token');
    });
    act(() => {
      result.current.logout();
    });

    expect(api.logout).toHaveBeenCalledTimes(1);
  });

  test('a rejected logout API call does not throw or affect the already-cleared state', async () => {
    api.logout.mockRejectedValue(new Error('network error'));
    const { result } = renderHook(() => useAuth(), { wrapper });

    act(() => {
      result.current.login({ username: 'john.doe', fullName: 'John Doe', role: 'USER' }, 'jwt-token');
    });

    expect(() => {
      act(() => {
        result.current.logout();
      });
    }).not.toThrow();

    expect(result.current.user).toBeNull();
    // Let the rejected promise's .catch() settle so it doesn't surface as an unhandled rejection.
    await waitFor(() => expect(api.logout).toHaveBeenCalled());
  });
});
