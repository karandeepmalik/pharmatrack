import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import AdminInventory from '../../../pages/admin/AdminInventory';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const makeRegular = (overrides = {}) => ({
  id: 1,
  username: 'john.doe',
  medicineName: 'Shield FX Vial 10 ml',
  medicineType: 'VIAL',
  specification: 10.0,
  concentrationMgPerMl: 20.0,
  specUnit: 'ml',
  price: 4000,
  pharmaName: 'Shield FX',
  quantity: 50,
  inventoryType: 'REGULAR_MEDICINE_STOCK',
  ...overrides,
});

const makeAdmin = (overrides = {}) => ({
  id: 100,
  username: 'admin',
  medicineName: 'Shield FX Vial 10 ml',
  medicineType: 'VIAL',
  specification: 10.0,
  concentrationMgPerMl: 20.0,
  specUnit: 'ml',
  price: 4000,
  pharmaName: 'Shield FX',
  quantity: 100,
  inventoryType: 'ADMIN_MEDICINE_STOCK',
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <AdminInventory />
    </MemoryRouter>
  );

beforeEach(() => jest.clearAllMocks());

// ── Render ─────────────────────────────────────────────────────────────────

describe('AdminInventory — render', () => {
  test('shows page heading', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [makeRegular()] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /view available medicine stock/i })).toBeInTheDocument()
    );
  });

  test('shows error alert when fetch fails', async () => {
    api.getAdminInventory.mockRejectedValue(new Error('Network error'));
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to load stock/i)
    );
  });

  test('does NOT render By Spec / By User toggle buttons', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [makeRegular()] });
    renderPage();
    await waitFor(() => screen.getByRole('heading', { name: /view available medicine stock/i }));
    expect(screen.queryByRole('button', { name: /by spec/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /by user/i })).not.toBeInTheDocument();
  });
});

// ── Two-section layout ────────────────────────────────────────────────────

describe('AdminInventory — two-section layout', () => {
  test('renders "Regular Stock (User Allocations)" section heading', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [makeRegular()] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /regular stock \(user allocations\)/i })).toBeInTheDocument()
    );
  });

  test('renders "Admin Stock (System Stock)" section heading', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [makeAdmin()] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /admin stock \(system stock\)/i })).toBeInTheDocument()
    );
  });

  test('REGULAR_MEDICINE_STOCK item appears in the Regular section, not Admin section', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [makeRegular({ id: 1, username: 'john.doe', inventoryType: 'REGULAR_MEDICINE_STOCK' })],
    });
    renderPage();
    await waitFor(() => screen.getByRole('heading', { name: /regular stock/i }));

    const regularSection = screen.getByRole('heading', { name: /regular stock \(user allocations\)/i }).closest('section');
    const adminSection   = screen.getByRole('heading', { name: /admin stock \(system stock\)/i }).closest('section');

    expect(within(regularSection).getByText('john.doe')).toBeInTheDocument();
    expect(within(adminSection).queryByText('john.doe')).not.toBeInTheDocument();
  });

  test('ADMIN_MEDICINE_STOCK item appears in the Admin section, not Regular section', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [makeAdmin({ id: 100, username: 'admin', inventoryType: 'ADMIN_MEDICINE_STOCK' })],
    });
    renderPage();
    await waitFor(() => screen.getByRole('heading', { name: /admin stock/i }));

    const regularSection = screen.getByRole('heading', { name: /regular stock \(user allocations\)/i }).closest('section');
    const adminSection   = screen.getByRole('heading', { name: /admin stock \(system stock\)/i }).closest('section');

    expect(within(adminSection).getByText('admin')).toBeInTheDocument();
    expect(within(regularSection).queryByText('admin')).not.toBeInTheDocument();
  });

  test('both sections render when both inventory types are present', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [
        makeRegular({ id: 1, username: 'john.doe' }),
        makeAdmin({ id: 2, username: 'admin' }),
      ],
    });
    renderPage();
    await waitFor(() => screen.getAllByRole('table'));

    const tables = screen.getAllByRole('table');
    expect(tables).toHaveLength(2);
  });

  test('Regular section shows "No stock records found" when no regular items', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [makeAdmin()] });
    renderPage();
    await waitFor(() => screen.getByRole('heading', { name: /regular stock/i }));

    const regularSection = screen.getByRole('heading', { name: /regular stock \(user allocations\)/i }).closest('section');
    expect(within(regularSection).getByText(/no stock records found/i)).toBeInTheDocument();
  });

  test('Admin section shows "No stock records found" when no admin items', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [makeRegular()] });
    renderPage();
    await waitFor(() => screen.getByRole('heading', { name: /admin stock/i }));

    const adminSection = screen.getByRole('heading', { name: /admin stock \(system stock\)/i }).closest('section');
    expect(within(adminSection).getByText(/no stock records found/i)).toBeInTheDocument();
  });

  test('both sections show "No stock records found" when data is empty', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() => screen.getByRole('heading', { name: /admin stock/i }));
    const empties = screen.getAllByText(/no stock records found/i);
    expect(empties).toHaveLength(2);
  });
});

// ── Zero quantity filtering ────────────────────────────────────────────────

describe('AdminInventory — zero quantity filtering', () => {
  test('hides items with quantity 0 from the Regular section', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [
        makeRegular({ id: 1, medicineName: 'Shield FX Vial 10 ml', quantity: 10 }),
        makeRegular({ id: 2, medicineName: 'Shield FX Tablet 25 mg', quantity: 0 }),
      ],
    });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    const regularSection = screen.getByRole('heading', { name: /regular stock \(user allocations\)/i }).closest('section');
    expect(within(regularSection).getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
    expect(within(regularSection).queryByText('Shield FX Tablet 25 mg')).not.toBeInTheDocument();
  });

  test('hides items with quantity 0 from the Admin section', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [
        makeAdmin({ id: 1, medicineName: 'Shield FX Vial 10 ml', quantity: 100 }),
        makeAdmin({ id: 2, medicineName: 'Shield FX Tablet 25 mg', quantity: 0 }),
      ],
    });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    const adminSection = screen.getByRole('heading', { name: /admin stock \(system stock\)/i }).closest('section');
    expect(within(adminSection).getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
    expect(within(adminSection).queryByText('Shield FX Tablet 25 mg')).not.toBeInTheDocument();
  });
});

// ── Default sort ──────────────────────────────────────────────────────────

describe('AdminInventory — default sort order', () => {
  test('Regular section rows sorted by medicine name', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [
        makeRegular({ id: 2, medicineName: 'Shield FX Vial 5 ml'  }),
        makeRegular({ id: 1, medicineName: 'Shield FX Tablet 25 mg' }),
      ],
    });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    const regularSection = screen.getByRole('heading', { name: /regular stock/i }).closest('section');
    const rows = within(regularSection).getAllByRole('row').slice(1); // skip header
    expect(rows[0]).toHaveTextContent('Shield FX Tablet 25 mg');
    expect(rows[1]).toHaveTextContent('Shield FX Vial 5 ml');
  });
});

// ── Filters ──────────────────────────────────────────────────────────────

describe('AdminInventory — filters', () => {
  const vial   = makeRegular({ id: 1, medicineName: 'Shield FX Vial 10 ml',   username: 'john.doe',   quantity: 5 });
  const tablet = makeRegular({ id: 2, medicineName: 'Shield FX Tablet 25 mg', username: 'jane.smith', quantity: 3 });

  test('renders "Filter by Medicine" dropdown', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByLabelText(/filter by medicine/i)).toBeInTheDocument()
    );
  });

  test('renders "Filter by User" dropdown', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByLabelText(/filter by user/i)).toBeInTheDocument()
    );
  });

  test('medicine filter shows only matching rows in Regular section', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial, tablet] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    await userEvent.selectOptions(
      screen.getByLabelText(/filter by medicine/i),
      'Shield FX Vial 10 ml'
    );

    const regularSection = screen.getByRole('heading', { name: /regular stock/i }).closest('section');
    expect(within(regularSection).getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
    expect(within(regularSection).queryByText('Shield FX Tablet 25 mg')).not.toBeInTheDocument();
  });

  test('user filter shows only matching rows in Regular section', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial, tablet] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    await userEvent.selectOptions(screen.getByLabelText(/filter by user/i), 'jane.smith');

    const regularSection = screen.getByRole('heading', { name: /regular stock/i }).closest('section');
    expect(within(regularSection).queryByText('john.doe')).not.toBeInTheDocument();
    expect(within(regularSection).getByText('jane.smith')).toBeInTheDocument();
  });

  test('user filter dropdown is populated only from regular stock users, not admin', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [
        vial,
        makeAdmin({ id: 100, username: 'admin' }),
      ],
    });
    renderPage();
    await waitFor(() => screen.getAllByRole('table'));

    const userSelect = screen.getByLabelText(/filter by user/i);
    const options = Array.from(userSelect.options).map(o => o.value);
    expect(options).toContain('john.doe');
    expect(options).not.toContain('admin'); // admin only holds ADMIN_MEDICINE_STOCK
  });

  test('clearing medicine filter restores all rows', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial, tablet] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    await userEvent.selectOptions(screen.getByLabelText(/filter by medicine/i), 'Shield FX Vial 10 ml');
    await userEvent.selectOptions(screen.getByLabelText(/filter by medicine/i), '');

    const regularSection = screen.getByRole('heading', { name: /regular stock/i }).closest('section');
    expect(within(regularSection).getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
    expect(within(regularSection).getByText('Shield FX Tablet 25 mg')).toBeInTheDocument();
  });
});
