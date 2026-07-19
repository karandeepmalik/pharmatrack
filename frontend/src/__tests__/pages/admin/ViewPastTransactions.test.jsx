import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ViewPastTransactions from '../../../pages/admin/ViewPastTransactions';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const renderPage = () =>
  render(
    <MemoryRouter>
      <ViewPastTransactions />
    </MemoryRouter>
  );

const makeTx = (overrides = {}) => ({
  id: 1,
  submittedByUsername: 'john.doe',
  submittedByFullName: 'John Doe',
  medicineId: 10,
  medicineName: 'Shield FX Vial 10 ml',
  medicineType: 'VIAL',
  specification: 10,
  quantity: 3,
  pricePerUnit: 4000,
  price: 4000,
  status: 'APPROVED',
  notes: 'Clinic B dispatch',
  submittedAt: '2026-05-03T10:00:00',
  approvedByUsername: 'admin',
  ...overrides,
});

const USERS = [
  { id: 1, username: 'john.doe',   fullName: 'John Doe',  role: 'USER', active: true },
  { id: 2, username: 'jane.smith', fullName: 'Jane Smith', role: 'USER', active: true },
  { id: 3, username: 'admin',      fullName: 'Admin',      role: 'ADMIN', active: true },
];

const MEDICINES = [
  { id: 10, name: 'Shield FX Vial 10 ml', type: 'VIAL',   specification: 10, price: 4000 },
  { id: 20, name: 'Shield FX Vial 5 ml',  type: 'VIAL',   specification: 5,  price: 2000 },
  { id: 30, name: 'Shield FX Tablet 25 mg (10 Tablets)', type: 'TABLET', specification: 25, price: 4000 },
];

beforeEach(() => {
  jest.clearAllMocks();
  api.getUsers.mockResolvedValue({ data: USERS });
  api.getMedicines.mockResolvedValue({ data: MEDICINES });
});

// ── Initial render ───────────────────────────────────────────────────────

describe('ViewPastTransactions — render', () => {
  test('renders page heading', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /view past medicine dispatches/i })).toBeInTheDocument();
  });

  test('renders Back link to admin dashboard', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /← back/i })).toHaveAttribute('href', '/admin/dashboard');
  });

  test('renders date range inputs', () => {
    renderPage();
    expect(screen.getByLabelText(/from date/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/to date/i)).toBeInTheDocument();
  });

  test('renders status filter with ALL, APPROVED, REJECTED options', () => {
    renderPage();
    expect(screen.getByLabelText(/status/i)).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /^all$/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /^approved$/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /^rejected$/i })).toBeInTheDocument();
  });

  test('defaults to APPROVED status', () => {
    renderPage();
    expect(screen.getByLabelText(/status/i)).toHaveValue('APPROVED');
  });

  test('renders User filter dropdown', () => {
    renderPage();
    expect(screen.getByLabelText(/^user$/i)).toBeInTheDocument();
  });

  test('renders Medicine Spec filter dropdown', () => {
    renderPage();
    expect(screen.getByLabelText(/medicine spec/i)).toBeInTheDocument();
  });

  test('renders Search Notes text input', () => {
    renderPage();
    expect(screen.getByLabelText(/search notes/i)).toBeInTheDocument();
  });

  test('Search Notes defaults to empty', () => {
    renderPage();
    expect(screen.getByLabelText(/search notes/i)).toHaveValue('');
  });

  test('User filter defaults to All Users', () => {
    renderPage();
    expect(screen.getByLabelText(/^user$/i)).toHaveValue('ALL');
  });

  test('Medicine Spec filter defaults to All Medicines', () => {
    renderPage();
    expect(screen.getByLabelText(/medicine spec/i)).toHaveValue('ALL');
  });

  test('renders Search button', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /search/i })).toBeInTheDocument();
  });

  test('does not show results table before search', () => {
    renderPage();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

// ── Dropdown population ──────────────────────────────────────────────────

describe('ViewPastTransactions — dropdown population', () => {
  test('loads non-admin users into User dropdown', async () => {
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('option', { name: /john doe \(john\.doe\)/i })).toBeInTheDocument()
    );
    expect(screen.getByRole('option', { name: /jane smith \(jane\.smith\)/i })).toBeInTheDocument();
    // Admin should be excluded
    expect(screen.queryByRole('option', { name: /admin.*admin/i })).not.toBeInTheDocument();
  });

  test('loads medicines into Medicine Spec dropdown', async () => {
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('option', { name: /vial 10 ml/i })).toBeInTheDocument()
    );
    expect(screen.getByRole('option', { name: /vial 5 ml/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /tablet 25 mg/i })).toBeInTheDocument();
  });

  test('calls getUsers and getMedicines on mount', () => {
    renderPage();
    expect(api.getUsers).toHaveBeenCalledTimes(1);
    expect(api.getMedicines).toHaveBeenCalledTimes(1);
  });
});

// ── Search results ───────────────────────────────────────────────────────

describe('ViewPastTransactions — search', () => {
  test('shows results table after successful search', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByRole('table')).toBeInTheDocument()
    );
    expect(api.getTransactionHistory).toHaveBeenCalledTimes(1);
  });

  test('shows transaction username in results', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('john.doe')).toBeInTheDocument()
    );
  });

  test('shows APPROVED status badge for approved transactions', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx({ status: 'APPROVED' })] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('APPROVED')).toBeInTheDocument()
    );
  });

  test('shows REJECTED status badge for rejected transactions', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx({ status: 'REJECTED' })] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('REJECTED')).toBeInTheDocument()
    );
  });

  test('shows empty state message when no transactions found', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/no transactions found/i)).toBeInTheDocument()
    );
  });

  test('calls API with APPROVED by default on first search', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String),
        expect.any(String),
        'APPROVED'
      )
    );
  });

  test('calls API with ALL when status changed to All', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [] });
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/status/i), 'ALL');
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String),
        expect.any(String),
        'ALL'
      )
    );
  });

  test('shows error alert when API fails', async () => {
    api.getTransactionHistory.mockRejectedValue(new Error('Network error'));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to load transactions/i)
    );
  });

  test('shows result count in heading', async () => {
    api.getTransactionHistory.mockResolvedValue({
      data: [makeTx(), makeTx({ id: 2 })],
    });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/results \(2\)/i)).toBeInTheDocument()
    );
  });
});

// ── User filter ──────────────────────────────────────────────────────────

describe('ViewPastTransactions — user filter', () => {
  const johnTx  = makeTx({ id: 1, submittedByUsername: 'john.doe',   notes: 'John note' });
  const janeTx  = makeTx({ id: 2, submittedByUsername: 'jane.smith', notes: 'Jane note' });

  beforeEach(() => {
    api.getTransactionHistory.mockResolvedValue({ data: [johnTx, janeTx] });
  });

  test('All Users shows all transactions', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => screen.getByText('John note'));
    expect(screen.getByText('Jane note')).toBeInTheDocument();
  });

  test('filtering by john.doe hides jane.smith transactions', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('John note'));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');

    expect(screen.getByText('John note')).toBeInTheDocument();
    expect(screen.queryByText('Jane note')).not.toBeInTheDocument();
  });

  test('filtering by jane.smith hides john.doe transactions', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('John note'));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'jane.smith');

    expect(screen.getByText('Jane note')).toBeInTheDocument();
    expect(screen.queryByText('John note')).not.toBeInTheDocument();
  });

  test('result count updates when user filter is applied', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText(/results \(2\)/i));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');

    expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument();
  });

  test('shows empty state when user filter matches no results', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [johnTx] });
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('John note'));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'jane.smith');

    expect(screen.getByText(/no transactions found/i)).toBeInTheDocument();
  });

  test('switching back to All Users restores all results', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('John note'));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');
    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'ALL');

    expect(screen.getByText('John note')).toBeInTheDocument();
    expect(screen.getByText('Jane note')).toBeInTheDocument();
  });

  test('user filter does not trigger a new API call', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('John note'));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');

    expect(api.getTransactionHistory).toHaveBeenCalledTimes(1);
  });
});

// ── Medicine filter ──────────────────────────────────────────────────────

describe('ViewPastTransactions — medicine filter', () => {
  const vial10Tx = makeTx({ id: 1, medicineId: 10, medicineName: 'Vial 10 ml', medicineType: 'VIAL',   specification: 10, notes: 'Vial 10 note' });
  const vial5Tx  = makeTx({ id: 2, medicineId: 20, medicineName: 'Vial 5 ml',  medicineType: 'VIAL',   specification: 5,  notes: 'Vial 5 note' });
  const tabTx    = makeTx({ id: 3, medicineId: 30, medicineName: 'Tab 25 mg',  medicineType: 'TABLET', specification: 25, notes: 'Tablet note' });

  beforeEach(() => {
    api.getTransactionHistory.mockResolvedValue({ data: [vial10Tx, vial5Tx, tabTx] });
  });

  test('All Medicines shows all transactions', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => screen.getByText('Vial 10 note'));
    expect(screen.getByText('Vial 5 note')).toBeInTheDocument();
    expect(screen.getByText('Tablet note')).toBeInTheDocument();
  });

  test('filtering by Vial 10 ml hides other medicines', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('Vial 10 note'));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');

    expect(screen.getByText('Vial 10 note')).toBeInTheDocument();
    expect(screen.queryByText('Vial 5 note')).not.toBeInTheDocument();
    expect(screen.queryByText('Tablet note')).not.toBeInTheDocument();
  });

  test('filtering by Vial 5 ml shows only 5 ml transactions', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('Vial 5 note'));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '20');

    expect(screen.getByText('Vial 5 note')).toBeInTheDocument();
    expect(screen.queryByText('Vial 10 note')).not.toBeInTheDocument();
    expect(screen.queryByText('Tablet note')).not.toBeInTheDocument();
  });

  test('result count updates when medicine filter is applied', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText(/results \(3\)/i));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');

    expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument();
  });

  test('shows empty state when medicine filter matches no results', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [vial10Tx] });
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('Vial 10 note'));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '20');

    expect(screen.getByText(/no transactions found/i)).toBeInTheDocument();
  });

  test('switching back to All Medicines restores all results', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('Vial 10 note'));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');
    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), 'ALL');

    expect(screen.getByText('Vial 10 note')).toBeInTheDocument();
    expect(screen.getByText('Vial 5 note')).toBeInTheDocument();
    expect(screen.getByText('Tablet note')).toBeInTheDocument();
  });

  test('medicine filter does not trigger a new API call', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('Vial 10 note'));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');

    expect(api.getTransactionHistory).toHaveBeenCalledTimes(1);
  });
});

// ── Notes search ─────────────────────────────────────────────────────────

describe('ViewPastTransactions — notes search', () => {
  const clinicTx = makeTx({ id: 1, notes: 'Dispatched to Clinic B for FIP treatment' });
  const wardTx   = makeTx({ id: 2, notes: 'Restocking Ward 3 monthly supply' });

  beforeEach(() => {
    api.getTransactionHistory.mockResolvedValue({ data: [clinicTx, wardTx] });
  });

  test('empty search shows all transactions', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => screen.getByText(clinicTx.notes));
    expect(screen.getByText(wardTx.notes)).toBeInTheDocument();
  });

  test('searching by note text filters to matching rows only', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText(clinicTx.notes));

    await userEvent.type(screen.getByLabelText(/search notes/i), 'clinic');

    expect(screen.getByText(clinicTx.notes)).toBeInTheDocument();
    expect(screen.queryByText(wardTx.notes)).not.toBeInTheDocument();
  });

  test('note search is case-insensitive', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText(clinicTx.notes));

    await userEvent.type(screen.getByLabelText(/search notes/i), 'CLINIC');

    expect(screen.getByText(clinicTx.notes)).toBeInTheDocument();
    expect(screen.queryByText(wardTx.notes)).not.toBeInTheDocument();
  });

  test('note search matching no rows shows empty state', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText(clinicTx.notes));

    await userEvent.type(screen.getByLabelText(/search notes/i), 'nonexistent note text');

    expect(screen.getByText(/no transactions found/i)).toBeInTheDocument();
  });

  test('note search combines with user filter', async () => {
    const johnClinic = makeTx({ id: 3, submittedByUsername: 'john.doe', notes: 'Clinic B dispatch for john' });
    const janeClinic  = makeTx({ id: 4, submittedByUsername: 'jane.smith', notes: 'Clinic B dispatch for jane' });
    api.getTransactionHistory.mockResolvedValue({ data: [johnClinic, janeClinic] });

    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText(johnClinic.notes));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');
    await userEvent.type(screen.getByLabelText(/search notes/i), 'clinic b');

    expect(screen.getByText(johnClinic.notes)).toBeInTheDocument();
    expect(screen.queryByText(janeClinic.notes)).not.toBeInTheDocument();
  });

  test('note search does not trigger a new API call', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText(clinicTx.notes));

    await userEvent.type(screen.getByLabelText(/search notes/i), 'clinic');

    expect(api.getTransactionHistory).toHaveBeenCalledTimes(1);
  });
});

// ── Combined filters ─────────────────────────────────────────────────────

describe('ViewPastTransactions — combined user + medicine filter', () => {
  const tx1 = makeTx({ id: 1, submittedByUsername: 'john.doe',   medicineId: 10, notes: 'John Vial10' });
  const tx2 = makeTx({ id: 2, submittedByUsername: 'john.doe',   medicineId: 20, notes: 'John Vial5' });
  const tx3 = makeTx({ id: 3, submittedByUsername: 'jane.smith', medicineId: 10, notes: 'Jane Vial10' });
  const tx4 = makeTx({ id: 4, submittedByUsername: 'jane.smith', medicineId: 20, notes: 'Jane Vial5' });

  beforeEach(() => {
    api.getTransactionHistory.mockResolvedValue({ data: [tx1, tx2, tx3, tx4] });
  });

  test('user + medicine filter shows only matching row', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('John Vial10'));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');
    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');

    expect(screen.getByText('John Vial10')).toBeInTheDocument();
    expect(screen.queryByText('John Vial5')).not.toBeInTheDocument();
    expect(screen.queryByText('Jane Vial10')).not.toBeInTheDocument();
    expect(screen.queryByText('Jane Vial5')).not.toBeInTheDocument();
    expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument();
  });

  test('user filter shows 2 rows, medicine filter further narrows to 1', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText(/results \(4\)/i));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'jane.smith');
    expect(screen.getByText(/results \(2\)/i)).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '20');
    expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument();
    expect(screen.getByText('Jane Vial5')).toBeInTheDocument();
  });
});
