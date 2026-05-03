import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ManageUsers from '../../../pages/admin/ManageUsers';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const makeUser = (overrides = {}) => ({
  id: 2,
  username: 'john.doe',
  fullName: 'John Doe',
  email: 'john@pharma.com',
  role: 'USER',
  active: true,
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <ManageUsers />
    </MemoryRouter>
  );

beforeEach(() => {
  jest.clearAllMocks();
  window.confirm = jest.fn(() => true);
});

// ── Loading & render ─────────────────────────────────────────────────────

describe('ManageUsers — render', () => {
  test('renders page heading', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    renderPage();
    expect(screen.getByRole('heading', { name: /manage users/i })).toBeInTheDocument();
  });

  test('shows users in table after load', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();
    await waitFor(() => expect(screen.getByText('john.doe')).toBeInTheDocument());
    expect(screen.getByText('John Doe')).toBeInTheDocument();
  });

  test('shows error alert when user fetch fails', async () => {
    api.getUsers.mockRejectedValue(new Error('Network error'));
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to load users/i)
    );
  });
});

// ── Delete user ──────────────────────────────────────────────────────────

describe('ManageUsers — delete user', () => {
  test('shows Delete button for each user', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();
    await waitFor(() => expect(screen.getByRole('button', { name: /^delete$/i })).toBeInTheDocument());
  });

  test('calls deleteUser and refreshes on confirmation', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    api.deleteUser.mockResolvedValue({});
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /^delete$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^delete$/i }));

    expect(window.confirm).toHaveBeenCalledWith(
      expect.stringContaining('john.doe')
    );
    await waitFor(() => expect(api.deleteUser).toHaveBeenCalledWith(2));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/deleted successfully/i)
    );
  });

  test('does not call deleteUser when confirm is cancelled', async () => {
    window.confirm = jest.fn(() => false);
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /^delete$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^delete$/i }));

    expect(api.deleteUser).not.toHaveBeenCalled();
  });

  test('shows error alert when deleteUser API fails', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    api.deleteUser.mockRejectedValue({ response: { data: { message: 'Cannot delete admin' } } });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /^delete$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^delete$/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/cannot delete admin/i)
    );
  });
});

// ── Deactivate/activate ──────────────────────────────────────────────────

describe('ManageUsers — toggle active status', () => {
  test('shows Deactivate button for active users', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser({ active: true })] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /deactivate/i })).toBeInTheDocument()
    );
  });

  test('shows Activate button for inactive users', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser({ active: false })] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /activate/i })).toBeInTheDocument()
    );
  });

  test('calls toggleUser on deactivate click', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    api.toggleUser.mockResolvedValue({ data: makeUser({ active: false }) });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /deactivate/i }));
    await userEvent.click(screen.getByRole('button', { name: /deactivate/i }));

    await waitFor(() => expect(api.toggleUser).toHaveBeenCalledWith(2));
  });
});
