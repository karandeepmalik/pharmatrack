import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ModifyInventory from '../../../pages/admin/ModifyInventory';
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

const makeMedicine = (overrides = {}) => ({
  id: 1,
  name: 'FIP Shield Vial',
  type: 'VIAL',
  specification: 10,
  price: 4000,
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <ModifyInventory />
    </MemoryRouter>
  );

beforeEach(() => {
  jest.clearAllMocks();
  api.getMedicines.mockResolvedValue({ data: [makeMedicine()] });
  api.getAdminInventory.mockResolvedValue({ data: [] });
});

// ── User dropdown ─────────────────────────────────────────────────────────

describe('ModifyInventory — user dropdown', () => {
  test('shows active regular users in the dropdown', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /john doe/i })).toBeInTheDocument()
    );
  });

  test('shows active admin users in the dropdown', async () => {
    api.getUsers.mockResolvedValue({
      data: [
        makeUser({ id: 1, username: 'admin', fullName: 'System Admin', role: 'ADMIN' }),
      ],
    });
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /system admin.*admin/i })).toBeInTheDocument()
    );
  });

  test('admin users are labelled with — Admin suffix', async () => {
    api.getUsers.mockResolvedValue({
      data: [
        makeUser({ id: 1, username: 'admin', fullName: 'System Admin', role: 'ADMIN' }),
      ],
    });
    renderPage();

    await waitFor(() => {
      const option = screen.getByRole('option', { name: /system admin \(admin\) — admin/i });
      expect(option).toBeInTheDocument();
    });
  });

  test('regular users are not labelled with — Admin', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /john doe/i })).toBeInTheDocument()
    );
    expect(screen.queryByRole('option', { name: /— admin/i })).not.toBeInTheDocument();
  });

  test('both admin and regular users appear together in the dropdown', async () => {
    api.getUsers.mockResolvedValue({
      data: [
        makeUser({ id: 1, username: 'admin', fullName: 'System Admin', role: 'ADMIN' }),
        makeUser({ id: 2, username: 'john.doe', fullName: 'John Doe', role: 'USER' }),
      ],
    });
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /system admin/i })).toBeInTheDocument()
    );
    expect(screen.getByRole('option', { name: /john doe/i })).toBeInTheDocument();
  });

  test('inactive users are excluded from the dropdown', async () => {
    api.getUsers.mockResolvedValue({
      data: [
        makeUser({ id: 1, username: 'inactive.user', fullName: 'Inactive User', active: false }),
        makeUser({ id: 2, username: 'john.doe', fullName: 'John Doe', active: true }),
      ],
    });
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /john doe/i })).toBeInTheDocument()
    );
    expect(screen.queryByRole('option', { name: /inactive user/i })).not.toBeInTheDocument();
  });
});

// ── Page structure ────────────────────────────────────────────────────────

describe('ModifyInventory — page structure', () => {
  test('renders the page heading', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    renderPage();
    expect(screen.getByRole('heading', { name: /modify inventory/i })).toBeInTheDocument();
  });

  test('renders a Back link to /admin/dashboard', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    renderPage();
    const backLink = screen.getByRole('link', { name: /← back/i });
    expect(backLink).toHaveAttribute('href', '/admin/dashboard');
  });

  test('quantity input is disabled until a medicine is selected', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /john doe/i })).toBeInTheDocument()
    );
    expect(screen.getByLabelText(/quantity/i)).toBeDisabled();
  });
});
