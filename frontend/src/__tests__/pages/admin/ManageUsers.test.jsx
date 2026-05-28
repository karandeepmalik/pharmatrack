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

// ── Add new user form ────────────────────────────────────────────────────

describe('ManageUsers — add new user form', () => {
  test('renders Add New User section heading', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    renderPage();
    expect(screen.getByRole('heading', { name: /add new user/i })).toBeInTheDocument();
  });

  test('renders fullName, username, email, password, role fields', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    renderPage();
    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^username$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/role/i)).toBeInTheDocument();
  });

  test('role dropdown defaults to USER', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    renderPage();
    expect(screen.getByLabelText(/role/i)).toHaveValue('USER');
  });

  test('shows success alert after creating a user', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    api.createUser.mockResolvedValue({});
    renderPage();

    await userEvent.type(screen.getByLabelText(/full name/i), 'New User');
    await userEvent.type(screen.getByLabelText(/^username$/i), 'new.user');
    await userEvent.type(screen.getByLabelText(/email/i), 'new@pharma.com');
    await userEvent.type(screen.getByLabelText(/^password$/i), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /create user/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/new\.user.*created/i)
    );
  });

  test('calls createUser API with form data', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    api.createUser.mockResolvedValue({});
    renderPage();

    await userEvent.type(screen.getByLabelText(/full name/i), 'New User');
    await userEvent.type(screen.getByLabelText(/^username$/i), 'new.user');
    await userEvent.type(screen.getByLabelText(/email/i), 'new@pharma.com');
    await userEvent.type(screen.getByLabelText(/^password$/i), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /create user/i }));

    await waitFor(() =>
      expect(api.createUser).toHaveBeenCalledWith(
        expect.objectContaining({ username: 'new.user', email: 'new@pharma.com' })
      )
    );
  });

  test('shows error alert when createUser API fails', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    api.createUser.mockRejectedValue({
      response: { data: { message: 'Username already exists' } },
    });
    renderPage();

    await userEvent.type(screen.getByLabelText(/full name/i), 'New User');
    await userEvent.type(screen.getByLabelText(/^username$/i), 'existing');
    await userEvent.type(screen.getByLabelText(/email/i), 'e@pharma.com');
    await userEvent.type(screen.getByLabelText(/^password$/i), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /create user/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/username already exists/i)
    );
  });
});

// ── Change password ──────────────────────────────────────────────────────

describe('ManageUsers — change password', () => {
  test('clicking Change Password reveals password input row', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /change password/i }));
    await userEvent.click(screen.getByRole('button', { name: /change password/i }));

    expect(
      screen.getByLabelText(/new password for john\.doe/i)
    ).toBeInTheDocument();
  });

  test('Set Password button is disabled when password is fewer than 8 chars', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /change password/i }));
    await userEvent.click(screen.getByRole('button', { name: /change password/i }));

    const input = screen.getByLabelText(/new password for john\.doe/i);
    await userEvent.type(input, 'short');

    expect(screen.getByRole('button', { name: /set password/i })).toBeDisabled();
  });

  test('Set Password button is enabled when password is 8 or more chars', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /change password/i }));
    await userEvent.click(screen.getByRole('button', { name: /change password/i }));

    await userEvent.type(screen.getByLabelText(/new password for john\.doe/i), 'longpass123');

    expect(screen.getByRole('button', { name: /set password/i })).not.toBeDisabled();
  });

  test('calls adminChangePassword with correct userId and password', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    api.adminChangePassword.mockResolvedValue({});
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /change password/i }));
    await userEvent.click(screen.getByRole('button', { name: /change password/i }));

    await userEvent.type(screen.getByLabelText(/new password for john\.doe/i), 'newpassword');
    await userEvent.click(screen.getByRole('button', { name: /set password/i }));

    await waitFor(() =>
      expect(api.adminChangePassword).toHaveBeenCalledWith(2, { newPassword: 'newpassword' })
    );
  });

  test('shows success alert after password change', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    api.adminChangePassword.mockResolvedValue({});
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /change password/i }));
    await userEvent.click(screen.getByRole('button', { name: /change password/i }));
    await userEvent.type(screen.getByLabelText(/new password for john\.doe/i), 'newpassword');
    await userEvent.click(screen.getByRole('button', { name: /set password/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/password.*updated/i)
    );
  });

  test('shows error alert when password change fails', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    api.adminChangePassword.mockRejectedValue({
      response: { data: { message: 'Password too weak' } },
    });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /change password/i }));
    await userEvent.click(screen.getByRole('button', { name: /change password/i }));
    await userEvent.type(screen.getByLabelText(/new password for john\.doe/i), 'weakpassword');
    await userEvent.click(screen.getByRole('button', { name: /set password/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/password too weak/i)
    );
  });

  test('Cancel button hides the password row', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /change password/i }));
    await userEvent.click(screen.getByRole('button', { name: /change password/i }));

    expect(screen.getByLabelText(/new password for john\.doe/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(screen.queryByLabelText(/new password for john\.doe/i)).not.toBeInTheDocument();
  });
});
