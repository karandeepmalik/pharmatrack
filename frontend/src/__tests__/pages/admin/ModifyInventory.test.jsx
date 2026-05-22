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

  test('excludes admin users from the dropdown (admin cannot hold inventory)', async () => {
    api.getUsers.mockResolvedValue({
      data: [
        makeUser({ id: 1, username: 'admin', fullName: 'System Admin', role: 'ADMIN' }),
        makeUser({ id: 2, username: 'john.doe', fullName: 'John Doe', role: 'USER' }),
      ],
    });
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /john doe/i })).toBeInTheDocument()
    );
    expect(screen.queryByRole('option', { name: /system admin/i })).not.toBeInTheDocument();
  });

  test('only non-admin users appear in the dropdown', async () => {
    api.getUsers.mockResolvedValue({
      data: [
        makeUser({ id: 1, username: 'admin', fullName: 'System Admin', role: 'ADMIN' }),
        makeUser({ id: 2, username: 'john.doe', fullName: 'John Doe', role: 'USER' }),
      ],
    });
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /john doe/i })).toBeInTheDocument()
    );
    expect(screen.queryByRole('option', { name: /system admin/i })).not.toBeInTheDocument();
  });

  test('regular users do not have — Admin label', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /john doe/i })).toBeInTheDocument()
    );
    expect(screen.queryByRole('option', { name: /— admin/i })).not.toBeInTheDocument();
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

// ── Inventory type selector ───────────────────────────────────────────────

describe('ModifyInventory — inventory type selector', () => {
  test('shows stock type selector with Regular and Admin Medicine Stock options', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByLabelText(/stock type/i)).toBeInTheDocument()
    );
    expect(screen.getByRole('option', { name: /regular medicine stock/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /admin medicine stock/i })).toBeInTheDocument();
  });

  test('defaults to Regular Medicine Stock', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByLabelText(/stock type/i)).toBeInTheDocument()
    );
    expect(screen.getByLabelText(/stock type/i)).toHaveValue('REGULAR_MEDICINE_STOCK');
  });
});

// ── Page structure ────────────────────────────────────────────────────────

describe('ModifyInventory — page structure', () => {
  test('renders the page heading', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    renderPage();
    expect(screen.getByRole('heading', { name: /modify medicine stock/i })).toBeInTheDocument();
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

// ── Internal movement checkbox ────────────────────────────────────────────

describe('ModifyInventory — internal movement', () => {
  test('renders the Internal Movement checkbox unchecked by default', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByLabelText(/internal movement/i)).toBeInTheDocument()
    );
    expect(screen.getByLabelText(/internal movement/i)).not.toBeChecked();
  });

  test('checkbox can be toggled on and off', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    const checkbox = await screen.findByLabelText(/internal movement/i);
    await userEvent.click(checkbox);
    expect(checkbox).toBeChecked();
    await userEvent.click(checkbox);
    expect(checkbox).not.toBeChecked();
  });

  test('note placeholder text is "e.g. Sent to Suma"', async () => {
    api.getUsers.mockResolvedValue({ data: [] });
    renderPage();
    expect(screen.getByPlaceholderText('e.g. Sent to Suma')).toBeInTheDocument();
  });
});

// ── Adjustment date input ─────────────────────────────────────────────────

describe('ModifyInventory — adjustment date', () => {
  test('renders an Adjustment Date input', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByLabelText(/adjustment date/i)).toBeInTheDocument()
    );
  });

  test('defaults adjustment date to today', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    const today = new Date().toISOString().slice(0, 10);
    const dateInput = await screen.findByLabelText(/adjustment date/i);
    expect(dateInput).toHaveValue(today);
  });

  test('adjustment date input has max set to today (no future dates)', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    const today = new Date().toISOString().slice(0, 10);
    const dateInput = await screen.findByLabelText(/adjustment date/i);
    expect(dateInput).toHaveAttribute('max', today);
  });

  test('adjustment date input is of type date', async () => {
    api.getUsers.mockResolvedValue({ data: [makeUser()] });
    renderPage();

    const dateInput = await screen.findByLabelText(/adjustment date/i);
    expect(dateInput).toHaveAttribute('type', 'date');
  });
});
