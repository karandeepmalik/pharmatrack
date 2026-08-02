import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ViewPastTransactions from '../../../pages/admin/ViewPastTransactions';
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
  medicineStockType: 'REGULAR_MEDICINE_STOCK',
  screenshots: [],
  ...overrides,
});

// Wrap a list into the paginated response shape the component expects
const mkPage = (items, { last = true, totalElements = items.length, totalQuantity = 0 } = {}) => ({
  data: { content: items, last, totalElements, totalQuantity },
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
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByRole('table')).toBeInTheDocument()
    );
    expect(api.getTransactionHistory).toHaveBeenCalledTimes(1);
  });

  test('shows transaction username in results', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('john.doe')).toBeInTheDocument()
    );
  });

  test('shows APPROVED status badge for approved transactions', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ status: 'APPROVED' })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('APPROVED')).toBeInTheDocument()
    );
  });

  test('shows REJECTED status badge for rejected transactions', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ status: 'REJECTED' })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('REJECTED')).toBeInTheDocument()
    );
  });

  test('shows empty state message when no transactions found', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/no transactions found/i)).toBeInTheDocument()
    );
  });

  test('calls API with APPROVED by default on first search', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String),
        expect.any(String),
        'APPROVED',
        0,
        10,
        undefined,
        undefined,
        undefined
      )
    );
  });

  test('calls API with ALL when status changed to All', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([]));
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/status/i), 'ALL');
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String),
        expect.any(String),
        'ALL',
        0,
        10,
        undefined,
        undefined,
        undefined
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
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx(), makeTx({ id: 2 })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/results \(2\)/i)).toBeInTheDocument()
    );
  });
});

// ── Pagination (scroll-loaded pages) ──────────────────────────────────────

describe('ViewPastTransactions — pagination', () => {
  test('requests page 0 with a page size of 10', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), expect.any(String), 0, 10, undefined, undefined, undefined
      )
    );
  });

  test('scrolling the sentinel into view loads the next page and appends its results', async () => {
    const page0 = [makeTx({ id: 1, notes: 'First page dispatch' })];
    const page1 = [makeTx({ id: 2, notes: 'Second page dispatch' })];
    api.getTransactionHistory.mockResolvedValueOnce(mkPage(page0, { last: false }));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('First page dispatch'));

    api.getTransactionHistory.mockResolvedValueOnce(mkPage(page1, { last: true }));
    observerCallback([{ isIntersecting: true }]);

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), expect.any(String), 1, 10, undefined, undefined, undefined
      )
    );
    await waitFor(() => screen.getByText('Second page dispatch'));
    expect(screen.getByText('First page dispatch')).toBeInTheDocument();
  });

  test('shows "all loaded" message once the last page has been fetched', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()], { last: true }));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/all 1 transactions loaded/i)).toBeInTheDocument()
    );
  });

  test('results heading shows the total matching count, not just the loaded page size — '
      + 'available immediately from page 0, before scrolling', async () => {
    const page0 = Array.from({ length: 10 }, (_, i) => makeTx({ id: i + 1 }));
    api.getTransactionHistory.mockResolvedValue(mkPage(page0, { last: false, totalElements: 47 }));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/results \(47\)/i)).toBeInTheDocument()
    );
  });

  test('shows a "Showing X of Y" hint while more matching pages remain unloaded', async () => {
    const page0 = Array.from({ length: 10 }, (_, i) => makeTx({ id: i + 1 }));
    api.getTransactionHistory.mockResolvedValue(mkPage(page0, { last: false, totalElements: 47 }));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/showing 10 of 47 matching dispatches/i)).toBeInTheDocument()
    );
  });

  test('the "Showing X of Y" hint disappears once every matching page has been loaded', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()], { last: true, totalElements: 1 }));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument()
    );
    expect(screen.queryByText(/showing \d+ of \d+ matching dispatches/i)).not.toBeInTheDocument();
  });

  test('shows the total quantity across the full matching set, sourced from the server', async () => {
    const page0 = Array.from({ length: 10 }, (_, i) => makeTx({ id: i + 1 }));
    api.getTransactionHistory.mockResolvedValue(mkPage(page0, { last: false, totalElements: 47, totalQuantity: 123.4 }));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/total quantity: 123\.4/i)).toBeInTheDocument()
    );
  });

  test('formats the total quantity to one decimal place', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()], { totalQuantity: 5 }));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/total quantity: 5\.0/i)).toBeInTheDocument()
    );
  });
});

// ── Stock Type column ───────────────────────────────────────────────────────

describe('ViewPastTransactions — stock type column', () => {
  test('shows Regular Stock badge for a REGULAR_MEDICINE_STOCK dispatch', async () => {
    api.getTransactionHistory.mockResolvedValue(
      mkPage([makeTx({ medicineStockType: 'REGULAR_MEDICINE_STOCK' })])
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('Regular Stock')).toBeInTheDocument()
    );
  });

  test('shows Admin Stock badge for an ADMIN_MEDICINE_STOCK dispatch', async () => {
    api.getTransactionHistory.mockResolvedValue(
      mkPage([makeTx({ medicineStockType: 'ADMIN_MEDICINE_STOCK' })])
    );
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('Admin Stock')).toBeInTheDocument()
    );
  });

  test('renders "Regular Stock" for a dispatch with a null medicineStockType (legacy fallback)', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ medicineStockType: null })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('Regular Stock')).toBeInTheDocument()
    );
  });

  test('shows both stock types when mixed dispatches are present', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([
      makeTx({ id: 1, medicineStockType: 'REGULAR_MEDICINE_STOCK' }),
      makeTx({ id: 2, medicineStockType: 'ADMIN_MEDICINE_STOCK' }),
    ]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => screen.getByRole('table'));
    expect(screen.getByText('Regular Stock')).toBeInTheDocument();
    expect(screen.getByText('Admin Stock')).toBeInTheDocument();
  });
});

// ── Screenshot column ────────────────────────────────────────────────────

describe('ViewPastTransactions — screenshot column', () => {
  test('shows "No screenshot" for a dispatch with no screenshots', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ screenshots: [] })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/no screenshot/i)).toBeInTheDocument()
    );
  });

  test('shows a viewable thumbnail button for a dispatch with a screenshot', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({
      id: 5,
      screenshots: [{ data: 'ZmFrZQ==', mimeType: 'image/png' }],
    })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /view payment screenshot 1 of 1 for transaction #5/i })).toBeInTheDocument()
    );
  });

  test('renders the Screenshot column header', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByRole('columnheader', { name: /screenshot/i })).toBeInTheDocument()
    );
  });
});

// ── User filter ──────────────────────────────────────────────────────────
//
// All filters below are sent to the backend as query params and take effect only after
// pressing Search again (consistent with From/To/Status) — this is a deliberate change from
// the previous client-side-only filtering, which silently missed real matches once results
// spanned more than one scroll-loaded page. See ViewPastTransactions.jsx's invalidateSearch.

describe('ViewPastTransactions — user filter', () => {
  const johnTx  = makeTx({ id: 1, submittedByUsername: 'john.doe',   notes: 'John note' });
  const janeTx  = makeTx({ id: 2, submittedByUsername: 'jane.smith', notes: 'Jane note' });

  test('selecting a user and searching sends it as the username query param', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([johnTx]));
    renderPage();
    await waitFor(() => screen.getByLabelText(/^user$/i));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), expect.any(String), 0, 10, 'john.doe', undefined, undefined
      )
    );
  });

  test('All Users omits the username param (undefined)', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([johnTx, janeTx]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), expect.any(String), 0, 10, undefined, undefined, undefined
      )
    );
  });

  test('changing the user filter after a search hides results until Search is pressed again', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([johnTx, janeTx]));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('John note'));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(api.getTransactionHistory).toHaveBeenCalledTimes(1);
  });

  test('server-side filtering finds a match even when the admin never scrolled past page 0 — '
      + 'the exact case that broke under client-side-only filtering', async () => {
    // Page 0 returns only johnTx (as the real paginated backend would); janeTx is a real match
    // that exists server-side but was never loaded into the browser's state.
    api.getTransactionHistory.mockResolvedValueOnce(mkPage([johnTx], { last: false }));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('John note'));

    // Now filter by jane.smith and search again — the backend (not the browser) does the
    // filtering, so it can return janeTx even though it was never in loaded state.
    api.getTransactionHistory.mockResolvedValueOnce(mkPage([janeTx], { last: true }));
    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'jane.smith');
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => expect(screen.getByText('Jane note')).toBeInTheDocument());
    expect(screen.queryByText('John note')).not.toBeInTheDocument();
  });
});

// ── Medicine filter ──────────────────────────────────────────────────────

describe('ViewPastTransactions — medicine filter', () => {
  const vial10Tx = makeTx({ id: 1, medicineId: 10, medicineName: 'Vial 10 ml', medicineType: 'VIAL', specification: 10, notes: 'Vial 10 note' });

  test('selecting a medicine and searching sends it as the medicineId query param', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([vial10Tx]));
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine spec/i));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), expect.any(String), 0, 10, undefined, '10', undefined
      )
    );
  });

  test('All Medicines omits the medicineId param (undefined)', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([vial10Tx]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), expect.any(String), 0, 10, undefined, undefined, undefined
      )
    );
  });

  test('changing the medicine filter after a search hides results until Search is pressed again', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([vial10Tx]));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText('Vial 10 note'));

    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

// ── Notes search ─────────────────────────────────────────────────────────

describe('ViewPastTransactions — notes search', () => {
  const clinicTx = makeTx({ id: 1, notes: 'Dispatched to Clinic B for FIP treatment' });

  test('typing a note filter and searching sends it as the (trimmed) notes query param', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([clinicTx]));
    renderPage();
    await waitFor(() => screen.getByLabelText(/search notes/i));

    await userEvent.type(screen.getByLabelText(/search notes/i), '  clinic  ');
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), expect.any(String), 0, 10, undefined, undefined, 'clinic'
      )
    );
  });

  test('empty notes search omits the notes param (undefined)', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([clinicTx]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), expect.any(String), 0, 10, undefined, undefined, undefined
      )
    );
  });

  test('typing into notes search after a search hides results until Search is pressed again', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([clinicTx]));
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByText(clinicTx.notes));

    await userEvent.type(screen.getByLabelText(/search notes/i), 'clinic');

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

// ── Combined filters ─────────────────────────────────────────────────────

describe('ViewPastTransactions — combined filters', () => {
  test('user, medicine, and notes filters are all sent together in one search', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([
      makeTx({ id: 1, submittedByUsername: 'john.doe', medicineId: 10, notes: 'Clinic B note' }),
    ]));
    renderPage();
    await waitFor(() => screen.getByLabelText(/^user$/i));

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');
    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');
    await userEvent.type(screen.getByLabelText(/search notes/i), 'clinic b');
    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), expect.any(String), 0, 10, 'john.doe', '10', 'clinic b'
      )
    );
  });
});
