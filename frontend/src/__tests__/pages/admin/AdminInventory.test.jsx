import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import AdminInventory from '../../../pages/admin/AdminInventory';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const makeItem = (overrides = {}) => ({
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
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <AdminInventory />
    </MemoryRouter>
  );

/** Returns rows from the data table (excluding header row). */
const dataRows = () => screen.getAllByRole('row').slice(1);

beforeEach(() => jest.clearAllMocks());

describe('AdminInventory — render', () => {
  test('shows page heading as View Available Medicine Stock', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [makeItem()] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /view available medicine stock/i })).toBeInTheDocument()
    );
  });

  test('shows inventory items', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [makeItem()] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));
    const table = screen.getByRole('table');
    expect(within(table).getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
    expect(within(table).getByText('john.doe')).toBeInTheDocument();
  });

  test('shows empty message when no inventory', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/no medicine stock records found/i)).toBeInTheDocument()
    );
  });

  test('shows error alert when fetch fails', async () => {
    api.getAdminInventory.mockRejectedValue(new Error('Network error'));
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to load stock/i)
    );
  });
});

describe('AdminInventory — zero quantity filtering', () => {
  test('hides items with quantity 0', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [
        makeItem({ id: 1, medicineName: 'Shield FX Vial 10 ml', quantity: 10 }),
        makeItem({ id: 2, medicineName: 'Shield FX Tablet 25 mg', quantity: 0 }),
      ],
    });
    renderPage();
    await waitFor(() => screen.getByRole('table'));
    const table = screen.getByRole('table');
    expect(within(table).getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
    expect(within(table).queryByText('Shield FX Tablet 25 mg')).not.toBeInTheDocument();
  });

  test('shows empty message when all items have quantity 0', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [makeItem({ quantity: 0 })],
    });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/no medicine stock records found/i)).toBeInTheDocument()
    );
  });

  test('shows items with positive quantity', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [makeItem({ quantity: 5 })],
    });
    renderPage();
    await waitFor(() => screen.getByRole('table'));
    expect(within(screen.getByRole('table')).getByText(/shield fx vial 10 ml/i)).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('5')).toBeInTheDocument();
  });
});

describe('AdminInventory — sort toggle', () => {
  const alpha = makeItem({ id: 1, medicineName: 'Alpha Med', username: 'zara.jones', quantity: 3 });
  const beta  = makeItem({ id: 2, medicineName: 'Beta Med',  username: 'alice.wang', quantity: 7 });

  test('"By Spec" tab is active by default', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [alpha, beta] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));
    expect(screen.getByRole('button', { name: /by spec/i })).toHaveClass('active');
    expect(screen.getByRole('button', { name: /by user/i })).not.toHaveClass('active');
  });

  test('default sort orders rows by medicine name', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [beta, alpha] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));
    const rows = dataRows();
    expect(rows[0]).toHaveTextContent('Alpha Med');
    expect(rows[1]).toHaveTextContent('Beta Med');
  });

  test('"By User" tab reorders rows by username', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [alpha, beta] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /by user/i }));

    const rows = dataRows();
    expect(rows[0]).toHaveTextContent('alice.wang');
    expect(rows[1]).toHaveTextContent('zara.jones');
  });

  test('clicking "By Spec" after "By User" restores medicine-name order', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [beta, alpha] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /by user/i }));
    await userEvent.click(screen.getByRole('button', { name: /by spec/i }));

    const rows = dataRows();
    expect(rows[0]).toHaveTextContent('Alpha Med');
    expect(rows[1]).toHaveTextContent('Beta Med');
  });
});

describe('AdminInventory — spec and username filters', () => {
  const vial   = makeItem({ id: 1, medicineName: 'Shield FX Vial 10 ml',   username: 'john.doe',   quantity: 5 });
  const tablet = makeItem({ id: 2, medicineName: 'Shield FX Tablet 25 mg', username: 'jane.smith', quantity: 3 });

  test('renders Medicine Specification filter dropdown', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByLabelText(/medicine specification/i)).toBeInTheDocument()
    );
  });

  test('renders Username filter dropdown', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
    );
  });

  test('spec filter shows only matching rows', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial, tablet] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    await userEvent.selectOptions(
      screen.getByLabelText(/medicine specification/i),
      'Shield FX Vial 10 ml'
    );

    const table = screen.getByRole('table');
    expect(within(table).getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
    expect(within(table).queryByText('Shield FX Tablet 25 mg')).not.toBeInTheDocument();
  });

  test('username filter shows only matching rows', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial, tablet] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    await userEvent.selectOptions(
      screen.getByLabelText(/username/i),
      'jane.smith'
    );

    const table = screen.getByRole('table');
    expect(within(table).queryByText('john.doe')).not.toBeInTheDocument();
    expect(within(table).getByText('jane.smith')).toBeInTheDocument();
  });

  test('All Specifications option shows all rows', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [vial, tablet] });
    renderPage();
    await waitFor(() => screen.getByRole('table'));

    await userEvent.selectOptions(
      screen.getByLabelText(/medicine specification/i),
      'Shield FX Vial 10 ml'
    );
    await userEvent.selectOptions(
      screen.getByLabelText(/medicine specification/i),
      ''
    );

    const table = screen.getByRole('table');
    expect(within(table).getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
    expect(within(table).getByText('Shield FX Tablet 25 mg')).toBeInTheDocument();
  });
});
