import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import MyTransactions from '../../../pages/user/MyTransactions';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

// IntersectionObserver not available in JSDOM; stub so the sentinel hook doesn't throw.
// Captures the callback passed by the component so tests can simulate the sentinel
// scrolling into view by invoking it directly.
let observerCallback;
beforeAll(() => {
  global.IntersectionObserver = class {
    constructor(callback) { observerCallback = callback; }
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});

const makeTx = (overrides = {}) => ({
  id: 1,
  status: 'APPROVED',
  medicineId: 10,
  medicineName: 'Shield FX Vial',
  medicineType: 'VIAL',
  specification: 10,
  concentrationMgPerMl: 20,
  quantity: 3,
  notes: 'Clinic B dispatch note',
  submittedAt: '2026-05-01T10:00:00',
  screenshots: [],
  medicineStockType: 'REGULAR_MEDICINE_STOCK',
  ...overrides,
});

// Wrap a list into the paginated response shape the component expects
const mkPage = (items, { last = true } = {}) => ({
  data: { content: items, last, totalElements: items.length },
});

const MEDICINES = [
  { id: 10, name: 'Shield FX Vial 10 ml', type: 'VIAL', concentrationMgPerMl: 20, specification: 10 },
  { id: 20, name: 'Shield FX Vial 5 ml', type: 'VIAL', concentrationMgPerMl: 20, specification: 5 },
  { id: 30, name: 'Shield FX Tablet 25 mg', type: 'TABLET', specification: 25, concentrationMgPerMl: null },
];

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

beforeEach(() => {
  jest.clearAllMocks();
  api.getMedicines.mockResolvedValue({ data: MEDICINES });
});

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

  test('renders Medicine Spec filter dropdown populated from the medicine catalog', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByLabelText(/medicine spec/i)).toBeInTheDocument()
    );
    expect(screen.getByRole('option', { name: /shield fx vial 10 ml/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /shield fx tablet 25 mg/i })).toBeInTheDocument();
  });

  test('renders Search Notes text input', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByLabelText(/search notes/i)).toBeInTheDocument()
    );
  });
});

// ── Pagination (scroll-loaded pages) ──────────────────────────────────────

describe('MyTransactions — pagination', () => {
  test('requests page 0 with a page size of 10 and default (ALL) filters', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'ALL', undefined, undefined)
    );
  });

  test('scrolling the sentinel into view loads the next page and appends its results', async () => {
    const page0 = [makeTx({ id: 1, notes: 'First page dispatch' })];
    const page1 = [makeTx({ id: 2, notes: 'Second page dispatch' })];
    api.getMyTransactions.mockResolvedValueOnce(mkPage(page0, { last: false }));
    const { container } = renderPage();
    await waitFor(() => withinList(container).getByText('First page dispatch'));

    api.getMyTransactions.mockResolvedValueOnce(mkPage(page1, { last: true }));
    observerCallback([{ isIntersecting: true }]);

    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(1, 10, 'ALL', undefined, undefined)
    );
    await waitFor(() => withinList(container).getByText('Second page dispatch'));
    expect(withinList(container).getByText('First page dispatch')).toBeInTheDocument();
  });

  test('shows "all loaded" message once the last page has been fetched', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx()], { last: true }));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/all 1 transactions loaded/i)).toBeInTheDocument()
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
      mkPage([makeTx({ medicineStockType: 'REGULAR_MEDICINE_STOCK' })])
    );
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Regular Stock')).toBeInTheDocument()
    );
  });

  test('shows Admin Stock for an ADMIN_MEDICINE_STOCK dispatch', async () => {
    api.getMyTransactions.mockResolvedValue(
      mkPage([makeTx({ medicineStockType: 'ADMIN_MEDICINE_STOCK' })])
    );
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Admin Stock')).toBeInTheDocument()
    );
  });

  test('renders "Regular Stock" for a dispatch with a null medicineStockType (legacy fallback)', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ medicineStockType: null })]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Regular Stock')).toBeInTheDocument()
    );
  });
});

// ── Filter tabs ───────────────────────────────────────────────────────────
//
// All filters below (status tab, medicine spec, notes) are sent to the backend as query params
// and trigger a fresh page-0 fetch (debounced) rather than being applied client-side over
// whatever's already loaded — this is a deliberate change from the previous client-side-only
// filtering, which silently missed real matches once results spanned more than one scroll-loaded
// page. See TransactionRepository.searchMyHistory's Javadoc and MyTransactions.jsx.

describe('MyTransactions — filter tabs', () => {
  test('clicking PENDING re-queries the server with status=PENDING', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'ALL', undefined, undefined)
    );

    await userEvent.click(screen.getByRole('button', { name: /^pending$/i }));

    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'PENDING', undefined, undefined)
    );
  });

  test('PENDING tab renders only what the server returns for that status', async () => {
    api.getMyTransactions.mockResolvedValueOnce(mkPage([
      makeTx({ id: 1, status: 'PENDING', notes: 'Pending note text here' }),
      makeTx({ id: 2, status: 'APPROVED', notes: 'Approved note text here' }),
    ]));
    renderPage();
    await waitFor(() => screen.getByText('Pending note text here'));

    api.getMyTransactions.mockResolvedValueOnce(
      mkPage([makeTx({ id: 1, status: 'PENDING', notes: 'Pending note text here' })])
    );
    await userEvent.click(screen.getByRole('button', { name: /^pending$/i }));

    await waitFor(() => expect(screen.queryByText('Approved note text here')).not.toBeInTheDocument());
    expect(screen.getByText('Pending note text here')).toBeInTheDocument();
  });

  test('clicking ALL after a status filter re-queries with status=ALL', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^approved$/i }));

    await userEvent.click(screen.getByRole('button', { name: /^approved$/i }));
    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'APPROVED', undefined, undefined)
    );

    await userEvent.click(screen.getByRole('button', { name: /^all$/i }));
    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'ALL', undefined, undefined)
    );
  });

  test('shows empty message when the server returns no matches for a status', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^pending$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^pending$/i }));

    await waitFor(() => expect(screen.getByText(/no transactions found/i)).toBeInTheDocument());
  });

  test('a match beyond page 0 is found immediately on switching tabs, without scrolling — '
      + 'the exact case that broke under client-side-only filtering', async () => {
    // Page 0 of the ALL view returns only an APPROVED item, exactly as a real paginated
    // backend would; the PENDING match below was never loaded into the browser's state.
    api.getMyTransactions.mockResolvedValueOnce(
      mkPage([makeTx({ id: 1, status: 'APPROVED', notes: 'Approved note' })], { last: false })
    );
    renderPage();
    await waitFor(() => screen.getByText('Approved note'));

    api.getMyTransactions.mockResolvedValueOnce(
      mkPage([makeTx({ id: 2, status: 'PENDING', notes: 'Pending note found without scrolling' })])
    );
    await userEvent.click(screen.getByRole('button', { name: /^pending$/i }));

    await waitFor(() =>
      expect(screen.getByText('Pending note found without scrolling')).toBeInTheDocument()
    );
    expect(screen.queryByText('Approved note')).not.toBeInTheDocument();
  });
});

// ── Medicine spec filter ──────────────────────────────────────────────────

describe('MyTransactions — medicine spec filter', () => {
  test('selecting a medicine re-queries the server with the medicineId param', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine spec/i));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');

    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'ALL', '10', undefined)
    );
  });

  test('switching back to All Medicines omits the medicineId param', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine spec/i));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');
    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'ALL', '10', undefined)
    );

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), 'ALL');
    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'ALL', undefined, undefined)
    );
  });

  test('spec filter combines with the status tab in the same query', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine spec/i));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');
    await userEvent.click(screen.getByRole('button', { name: /^pending$/i }));

    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'PENDING', '10', undefined)
    );
  });
});

// ── Notes search ─────────────────────────────────────────────────────────

describe('MyTransactions — notes search', () => {
  test('typing a note filter re-queries the server with the (trimmed) notes param', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() => screen.getByLabelText(/search notes/i));

    await userEvent.type(screen.getByLabelText(/search notes/i), 'clinic');

    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'ALL', undefined, 'clinic')
    );
  });

  test('clearing the notes search omits the notes param', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([]));
    renderPage();
    await waitFor(() => screen.getByLabelText(/search notes/i));

    const input = screen.getByLabelText(/search notes/i);
    await userEvent.type(input, 'clinic');
    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'ALL', undefined, 'clinic')
    );

    await userEvent.clear(input);
    await waitFor(() =>
      expect(api.getMyTransactions).toHaveBeenCalledWith(0, 10, 'ALL', undefined, undefined)
    );
  });

  test('a match beyond page 0 is found by notes search without scrolling — '
      + 'the exact case that broke under client-side-only filtering', async () => {
    api.getMyTransactions.mockResolvedValueOnce(
      mkPage([makeTx({ id: 1, notes: 'Unrelated note' })], { last: false })
    );
    renderPage();
    await waitFor(() => screen.getByText('Unrelated note'));

    api.getMyTransactions.mockResolvedValueOnce(
      mkPage([makeTx({ id: 2, notes: 'Dispatched to Clinic B for FIP treatment' })])
    );
    await userEvent.type(screen.getByLabelText(/search notes/i), 'clinic');

    await waitFor(() =>
      expect(screen.getByText('Dispatched to Clinic B for FIP treatment')).toBeInTheDocument()
    );
    expect(screen.queryByText('Unrelated note')).not.toBeInTheDocument();
  });

  test('note search matching nothing on the server shows the empty state', async () => {
    api.getMyTransactions.mockResolvedValueOnce(mkPage([makeTx({ notes: 'Something' })]));
    renderPage();
    await waitFor(() => screen.getByText('Something'));

    api.getMyTransactions.mockResolvedValueOnce(mkPage([]));
    await userEvent.type(screen.getByLabelText(/search notes/i), 'nonexistent note text');

    await waitFor(() => expect(screen.getByText(/no transactions found/i)).toBeInTheDocument());
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
