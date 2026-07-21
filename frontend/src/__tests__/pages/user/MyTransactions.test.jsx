import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import MyTransactions from '../../../pages/user/MyTransactions';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

// IntersectionObserver not available in JSDOM; stub so the sentinel hook doesn't throw
beforeAll(() => {
  global.IntersectionObserver = class {
    constructor() {}
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});

const makeTx = (overrides = {}) => ({
  id: 1,
  status: 'APPROVED',
  medicineName: 'Shield FX Vial',
  medicineType: 'VIAL',
  specification: 10,
  concentrationMgPerMl: 20,
  quantity: 3,
  notes: 'Clinic B dispatch note',
  submittedAt: '2026-05-01T10:00:00',
  screenshots: [],
  inventoryType: 'REGULAR_MEDICINE_STOCK',
  ...overrides,
});

// Wrap a list into the paginated response shape the component expects
const mkPage = (items, { last = true } = {}) => ({
  data: { content: items, last, totalElements: items.length },
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <MyTransactions />
    </MemoryRouter>
  );

// The Medicine Spec filter dropdown's option text can duplicate text shown in a
// transaction card (e.g. "Shield FX Vial — 20 mg/ml"), so queries that need to find
// card content unambiguously must be scoped to the transactions list, excluding the
// filter row.
const withinList = (container) => within(container.querySelector('.transactions-list'));

beforeEach(() => jest.clearAllMocks());

// ── Loading & render ──────────────────────────────────────────────────────

describe('MyTransactions — loading', () => {
  test('shows loading indicator while fetching', () => {
    api.getMyTransactions.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });
});

// ── Page structure ────────────────────────────────────────────────────────

describe('MyTransactions — page structure', () => {
  test('renders page heading after load', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /medicine dispatch history/i })).toBeInTheDocument()
    );
  });

  test('renders Back link to user dashboard', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('link', { name: /← back/i })).toHaveAttribute('href', '/user/dashboard')
    );
  });

  test('renders ALL, PENDING, APPROVED, REJECTED filter tabs', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() => screen.getByRole('group'));
    expect(screen.getByRole('button', { name: /^all$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^pending$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^approved$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^rejected$/i })).toBeInTheDocument();
  });

  test('renders Medicine Spec filter dropdown', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByLabelText(/medicine spec/i)).toBeInTheDocument()
    );
  });

  test('renders Search Notes text input', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByLabelText(/search notes/i)).toBeInTheDocument()
    );
  });
});

// ── Error state ───────────────────────────────────────────────────────────

describe('MyTransactions — error state', () => {
  test('shows error alert when API call fails', async () => {
    api.getMyTransactions.mockRejectedValue(new Error('Network error'));
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to load transactions/i)
    );
  });

  test('shows empty list (not crash) when API returns null data', async () => {
    api.getMyTransactions.mockResolvedValue({ data: null });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/no transactions found/i)).toBeInTheDocument()
    );
  });
});

// ── Empty state ───────────────────────────────────────────────────────────

describe('MyTransactions — empty state', () => {
  test('shows empty message when no transactions exist', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/no transactions found/i)).toBeInTheDocument()
    );
  });
});

// ── Crash-guard: malformed data ───────────────────────────────────────────

describe('MyTransactions — crash guard for malformed data', () => {
  test('renders without crashing when tx.status is null', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: null })]));
    renderPage();
    // Should render the card using fallback status 'UNKNOWN', not throw
    await waitFor(() =>
      expect(screen.getByText('UNKNOWN')).toBeInTheDocument()
    );
  });

  test('renders without crashing when tx.medicineName is null', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ medicineName: null })]));
    const { container } = renderPage();
    await waitFor(() =>
      expect(withinList(container).getByText(/unknown/i)).toBeInTheDocument()
    );
  });

  test('renders without crashing when tx.submittedAt is null', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ submittedAt: null })]));
    renderPage();
    await waitFor(() =>
      // Should show em-dash placeholder instead of throwing on new Date(null)
      expect(screen.getByText('—')).toBeInTheDocument()
    );
  });

  test('renders without crashing when tx.specification and concentrationMgPerMl are null', async () => {
    api.getMyTransactions.mockResolvedValue(
      mkPage([makeTx({ specification: null, concentrationMgPerMl: null })])
    );
    const { container } = renderPage();
    await waitFor(() =>
      expect(withinList(container).getByText(/shield fx vial/i)).toBeInTheDocument()
    );
  });

  test('renders without crashing when screenshots field is absent', async () => {
    const tx = makeTx();
    delete tx.screenshots;
    api.getMyTransactions.mockResolvedValue(mkPage([tx]));
    const { container } = renderPage();
    await waitFor(() =>
      expect(withinList(container).getByText(/shield fx vial/i)).toBeInTheDocument()
    );
  });
});

// ── Transaction display ───────────────────────────────────────────────────

describe('MyTransactions — transaction display', () => {
  test('shows medicine name in transaction card', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx()]));
    const { container } = renderPage();
    await waitFor(() =>
      expect(withinList(container).getByText(/shield fx vial/i)).toBeInTheDocument()
    );
  });

  test('shows transaction quantity', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ quantity: 5 })]));
    const { container } = renderPage();
    await waitFor(() => withinList(container).getByText(/shield fx vial/i));
    expect(screen.getByText(/quantity/i)).toBeInTheDocument();
  });

  test('shows APPROVED badge for approved transaction', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: 'APPROVED' })]));
    renderPage();
    await waitFor(() =>
      expect(screen.getAllByText('APPROVED').length).toBeGreaterThanOrEqual(2)
    );
  });

  test('shows PENDING badge for pending transaction', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: 'PENDING' })]));
    renderPage();
    await waitFor(() =>
      expect(screen.getAllByText('PENDING').length).toBeGreaterThanOrEqual(2)
    );
  });

  test('shows REJECTED badge for rejected transaction', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: 'REJECTED' })]));
    renderPage();
    await waitFor(() =>
      expect(screen.getAllByText('REJECTED').length).toBeGreaterThanOrEqual(2)
    );
  });

  test('shows mg/ml spec for VIAL medicine', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ medicineType: 'VIAL', concentrationMgPerMl: 20 })]));
    const { container } = renderPage();
    await waitFor(() =>
      expect(withinList(container).getByText(/20 mg\/ml/i)).toBeInTheDocument()
    );
  });

  test('shows mg spec for TABLET medicine', async () => {
    api.getMyTransactions.mockResolvedValue(
      mkPage([makeTx({ medicineType: 'TABLET', specification: 25, concentrationMgPerMl: null })])
    );
    const { container } = renderPage();
    await waitFor(() =>
      expect(withinList(container).getByText(/25 mg/i)).toBeInTheDocument()
    );
  });

  test('shows screenshot viewer when screenshots list is non-empty', async () => {
    api.getMyTransactions.mockResolvedValue(
      mkPage([makeTx({ screenshots: [{ data: 'base64', mimeType: 'image/png' }] })])
    );
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/payment screenshot:/i)).toBeInTheDocument()
    );
  });

  test('does not show screenshot indicator when screenshots list is empty', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ screenshots: [] })]));
    const { container } = renderPage();
    await waitFor(() => withinList(container).getByText(/shield fx vial/i));
    expect(screen.queryByText(/payment screenshot/i)).not.toBeInTheDocument();
  });
});

// ── Stock type display ───────────────────────────────────────────────────

describe('MyTransactions — stock type display', () => {
  test('shows Regular Stock for a REGULAR_MEDICINE_STOCK dispatch', async () => {
    api.getMyTransactions.mockResolvedValue(
      mkPage([makeTx({ inventoryType: 'REGULAR_MEDICINE_STOCK' })])
    );
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Regular Stock')).toBeInTheDocument()
    );
  });

  test('shows Admin Stock for an ADMIN_MEDICINE_STOCK dispatch', async () => {
    api.getMyTransactions.mockResolvedValue(
      mkPage([makeTx({ inventoryType: 'ADMIN_MEDICINE_STOCK' })])
    );
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Admin Stock')).toBeInTheDocument()
    );
  });

  test('renders "Regular Stock" for a dispatch with a null inventoryType (legacy fallback)', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ inventoryType: null })]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Regular Stock')).toBeInTheDocument()
    );
  });
});

// ── Filter tabs ───────────────────────────────────────────────────────────

describe('MyTransactions — filter tabs', () => {
  const pending  = makeTx({ id: 1, status: 'PENDING',   notes: 'Pending note text here' });
  const approved = makeTx({ id: 2, status: 'APPROVED',  notes: 'Approved note text here' });
  const rejected = makeTx({ id: 3, status: 'REJECTED',  notes: 'Rejected note text here' });

  beforeEach(() => {
    // Client-side filter: load all transactions at once, filter in UI
    api.getMyTransactions.mockResolvedValue(mkPage([pending, approved, rejected]));
  });

  test('ALL tab shows all three transactions by default', async () => {
    renderPage();
    await waitFor(() => screen.getByText('Pending note text here'));
    expect(screen.getByText('Approved note text here')).toBeInTheDocument();
    expect(screen.getByText('Rejected note text here')).toBeInTheDocument();
  });

  test('PENDING filter shows only the pending transaction', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^pending$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^pending$/i }));

    expect(screen.getByText('Pending note text here')).toBeInTheDocument();
    expect(screen.queryByText('Approved note text here')).not.toBeInTheDocument();
    expect(screen.queryByText('Rejected note text here')).not.toBeInTheDocument();
  });

  test('APPROVED filter shows only the approved transaction', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^approved$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^approved$/i }));

    expect(screen.getByText('Approved note text here')).toBeInTheDocument();
    expect(screen.queryByText('Pending note text here')).not.toBeInTheDocument();
    expect(screen.queryByText('Rejected note text here')).not.toBeInTheDocument();
  });

  test('REJECTED filter shows only the rejected transaction', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^rejected$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^rejected$/i }));

    expect(screen.getByText('Rejected note text here')).toBeInTheDocument();
    expect(screen.queryByText('Pending note text here')).not.toBeInTheDocument();
    expect(screen.queryByText('Approved note text here')).not.toBeInTheDocument();
  });

  test('clicking ALL after filtering restores all three transactions', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^approved$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^approved$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^all$/i }));

    expect(screen.getByText('Pending note text here')).toBeInTheDocument();
    expect(screen.getByText('Approved note text here')).toBeInTheDocument();
    expect(screen.getByText('Rejected note text here')).toBeInTheDocument();
  });

  test('shows empty message when filtered result has no matches', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([approved]));
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^pending$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^pending$/i }));

    expect(screen.getByText(/no transactions found/i)).toBeInTheDocument();
  });
});

// ── Medicine spec filter ──────────────────────────────────────────────────

describe('MyTransactions — medicine spec filter', () => {
  const vial10 = makeTx({
    id: 1, medicineId: 10, medicineName: 'Shield FX Vial 10 ml', medicineType: 'VIAL',
    concentrationMgPerMl: 20, notes: 'Vial 10 dispatch note',
  });
  const vial5 = makeTx({
    id: 2, medicineId: 20, medicineName: 'Shield FX Vial 5 ml', medicineType: 'VIAL',
    concentrationMgPerMl: 20, notes: 'Vial 5 dispatch note',
  });
  const tablet = makeTx({
    id: 3, medicineId: 30, medicineName: 'Shield FX Tablet 25 mg', medicineType: 'TABLET',
    specification: 25, concentrationMgPerMl: null, notes: 'Tablet dispatch note',
  });

  beforeEach(() => {
    api.getMyTransactions.mockResolvedValue(mkPage([vial10, vial5, tablet]));
  });

  test('All Medicines shows every dispatch by default', async () => {
    renderPage();
    await waitFor(() => screen.getByText(vial10.notes));
    expect(screen.getByText(vial5.notes)).toBeInTheDocument();
    expect(screen.getByText(tablet.notes)).toBeInTheDocument();
  });

  test('lists each distinct medicine once as a filter option', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine spec/i));
    expect(screen.getByRole('option', { name: /shield fx vial 10 ml/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /shield fx vial 5 ml/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /shield fx tablet 25 mg/i })).toBeInTheDocument();
  });

  test('filtering by a specific medicine hides the others', async () => {
    renderPage();
    await waitFor(() => screen.getByText(vial10.notes));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');

    expect(screen.getByText(vial10.notes)).toBeInTheDocument();
    expect(screen.queryByText(vial5.notes)).not.toBeInTheDocument();
    expect(screen.queryByText(tablet.notes)).not.toBeInTheDocument();
  });

  test('switching back to All Medicines restores all dispatches', async () => {
    renderPage();
    await waitFor(() => screen.getByText(vial10.notes));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');
    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), 'ALL');

    expect(screen.getByText(vial10.notes)).toBeInTheDocument();
    expect(screen.getByText(vial5.notes)).toBeInTheDocument();
    expect(screen.getByText(tablet.notes)).toBeInTheDocument();
  });

  test('spec filter combines with status filter tabs', async () => {
    const pendingVial10 = makeTx({
      id: 4, status: 'PENDING', medicineId: 10, medicineName: 'Shield FX Vial 10 ml',
      medicineType: 'VIAL', concentrationMgPerMl: 20, notes: 'Pending vial 10 note',
    });
    api.getMyTransactions.mockResolvedValue(mkPage([vial10, pendingVial10, vial5]));
    renderPage();
    await waitFor(() => screen.getByText(vial10.notes));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');
    await userEvent.click(screen.getByRole('button', { name: /^pending$/i }));

    expect(screen.getByText('Pending vial 10 note')).toBeInTheDocument();
    expect(screen.queryByText(vial10.notes)).not.toBeInTheDocument();
    expect(screen.queryByText(vial5.notes)).not.toBeInTheDocument();
  });
});

// ── Notes search ─────────────────────────────────────────────────────────

describe('MyTransactions — notes search', () => {
  const clinicTx = makeTx({ id: 1, notes: 'Dispatched to Clinic B for FIP treatment' });
  const wardTx   = makeTx({ id: 2, notes: 'Restocking Ward 3 monthly supply' });

  beforeEach(() => {
    api.getMyTransactions.mockResolvedValue(mkPage([clinicTx, wardTx]));
  });

  test('empty search shows all dispatches', async () => {
    renderPage();
    await waitFor(() => screen.getByText(clinicTx.notes));
    expect(screen.getByText(wardTx.notes)).toBeInTheDocument();
  });

  test('searching by note text filters to matching rows only', async () => {
    renderPage();
    await waitFor(() => screen.getByText(clinicTx.notes));

    await userEvent.type(screen.getByLabelText(/search notes/i), 'clinic');

    expect(screen.getByText(clinicTx.notes)).toBeInTheDocument();
    expect(screen.queryByText(wardTx.notes)).not.toBeInTheDocument();
  });

  test('note search is case-insensitive', async () => {
    renderPage();
    await waitFor(() => screen.getByText(clinicTx.notes));

    await userEvent.type(screen.getByLabelText(/search notes/i), 'CLINIC');

    expect(screen.getByText(clinicTx.notes)).toBeInTheDocument();
    expect(screen.queryByText(wardTx.notes)).not.toBeInTheDocument();
  });

  test('note search matching no rows shows empty state', async () => {
    renderPage();
    await waitFor(() => screen.getByText(clinicTx.notes));

    await userEvent.type(screen.getByLabelText(/search notes/i), 'nonexistent note text');

    expect(screen.getByText(/no transactions found/i)).toBeInTheDocument();
  });
});

// ── Delete own pending dispatch ─────────────────────────────────────────────

describe('MyTransactions — delete transaction', () => {
  let confirmSpy;

  beforeEach(() => {
    confirmSpy = jest.spyOn(window, 'confirm');
  });

  afterEach(() => {
    confirmSpy.mockRestore();
  });

  test('shows Delete button for a PENDING transaction', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: 'PENDING' })]));
    const { container } = renderPage();
    await waitFor(() => withinList(container).getByText(/shield fx vial/i));
    expect(screen.getByRole('button', { name: /delete/i })).toBeInTheDocument();
  });

  test('does not show Delete button for an APPROVED transaction', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: 'APPROVED' })]));
    const { container } = renderPage();
    await waitFor(() => withinList(container).getByText(/shield fx vial/i));
    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument();
  });

  test('does not show Delete button for a REJECTED transaction', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: 'REJECTED' })]));
    const { container } = renderPage();
    await waitFor(() => withinList(container).getByText(/shield fx vial/i));
    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument();
  });

  test('clicking Delete asks for confirmation and does nothing when declined', async () => {
    confirmSpy.mockReturnValue(false);
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: 'PENDING' })]));
    const { container } = renderPage();
    await waitFor(() => withinList(container).getByText(/shield fx vial/i));

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));

    expect(confirmSpy).toHaveBeenCalled();
    expect(api.deleteMyTransaction).not.toHaveBeenCalled();
    expect(withinList(container).getByText(/shield fx vial/i)).toBeInTheDocument();
  });

  test('confirming Delete calls the API and removes the transaction from the list', async () => {
    confirmSpy.mockReturnValue(true);
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: 'PENDING', id: 5 })]));
    api.deleteMyTransaction.mockResolvedValue({});
    const { container } = renderPage();
    await waitFor(() => withinList(container).getByText(/shield fx vial/i));

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));

    await waitFor(() => expect(api.deleteMyTransaction).toHaveBeenCalledWith(5));
    await waitFor(() =>
      expect(screen.getByText(/no transactions found/i)).toBeInTheDocument()
    );
  });

  test('shows an error alert when deletion fails', async () => {
    confirmSpy.mockReturnValue(true);
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ status: 'PENDING' })]));
    api.deleteMyTransaction.mockRejectedValue({
      response: { data: { message: 'Cannot delete — already approved' } },
    });
    const { container } = renderPage();
    await waitFor(() => withinList(container).getByText(/shield fx vial/i));

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/cannot delete — already approved/i)
    );
    // Record stays in the list since deletion failed
    expect(withinList(container).getByText(/shield fx vial/i)).toBeInTheDocument();
  });
});
