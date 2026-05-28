import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Login from '../../components/Login';
import * as api from '../../api/api';

jest.mock('../../api/api');

const mockLogin = jest.fn();
const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

jest.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ login: mockLogin }),
}));

const renderPage = () =>
  render(
    <MemoryRouter>
      <Login />
    </MemoryRouter>
  );

beforeEach(() => {
  jest.clearAllMocks();
});

// ── Render ────────────────────────────────────────────────────────────────

describe('Login — render', () => {
  test('renders PharmaTrack heading', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /pharmatrack/i })).toBeInTheDocument();
  });

  test('renders username and password inputs', () => {
    renderPage();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  test('renders Sign In button', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  test('does not show error message initially', () => {
    renderPage();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

// ── Successful login ──────────────────────────────────────────────────────

describe('Login — successful login', () => {
  test('navigates to admin dashboard when role is ADMIN', async () => {
    api.login.mockResolvedValue({
      data: { token: 'tok', username: 'admin', fullName: 'Admin', role: 'ADMIN' },
    });
    renderPage();

    await userEvent.type(screen.getByLabelText(/username/i), 'admin');
    await userEvent.type(screen.getByLabelText(/password/i), 'secret');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/admin/dashboard'));
  });

  test('navigates to user dashboard when role is USER', async () => {
    api.login.mockResolvedValue({
      data: { token: 'tok', username: 'john.doe', fullName: 'John Doe', role: 'USER' },
    });
    renderPage();

    await userEvent.type(screen.getByLabelText(/username/i), 'john.doe');
    await userEvent.type(screen.getByLabelText(/password/i), 'secret');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/user/dashboard'));
  });

  test('calls login context function with user data and token', async () => {
    api.login.mockResolvedValue({
      data: { token: 'jwt-token', username: 'john.doe', fullName: 'John Doe', role: 'USER' },
    });
    renderPage();

    await userEvent.type(screen.getByLabelText(/username/i), 'john.doe');
    await userEvent.type(screen.getByLabelText(/password/i), 'pass');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(mockLogin).toHaveBeenCalledWith(
        { username: 'john.doe', fullName: 'John Doe', role: 'USER' },
        'jwt-token'
      )
    );
  });
});

// ── Failed login ──────────────────────────────────────────────────────────

describe('Login — failed login', () => {
  test('shows error alert on API failure', async () => {
    api.login.mockRejectedValue(new Error('Unauthorized'));
    renderPage();

    await userEvent.type(screen.getByLabelText(/username/i), 'john.doe');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/invalid username or password/i)
    );
  });

  test('does not navigate on failure', async () => {
    api.login.mockRejectedValue(new Error('Unauthorized'));
    renderPage();

    await userEvent.type(screen.getByLabelText(/username/i), 'john.doe');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => screen.getByRole('alert'));
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});

// ── Loading state ─────────────────────────────────────────────────────────

describe('Login — loading state', () => {
  test('shows "Signing in…" while request is in flight', async () => {
    api.login.mockReturnValue(new Promise(() => {})); // never resolves
    renderPage();

    await userEvent.type(screen.getByLabelText(/username/i), 'john.doe');
    await userEvent.type(screen.getByLabelText(/password/i), 'pass');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(screen.getByRole('button', { name: /signing in/i })).toBeDisabled();
  });
});
