import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import AdminEditDispatch from '../../../pages/admin/AdminEditDispatch';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const makeTx = (overrides = {}) => ({
  id: 1,
  submittedByUsername: 'john.doe',
  medicineName: 'Shield FX Vial 10 ml',
  medicineType: 'VIAL',
  specification: 10,
  quantity: 5,
  status: 'APPROVED',
  notes: 'Clinic B dispatch today',
  submittedAt: '2026-05-01T10:00:00',
  medicineStockType: 'REGULAR_MEDICINE_STOCK',
  screenshots: [],
  ...overrides,
});

// Wrap a list into the paginated response shape /transactions/history actually returns
// (PagedResponse<TransactionResponse>, not a bare array) — a prior regression shipped
// because this file's mocks used the old bare-array shape while the real endpoint had
// already changed, so the mismatch was invisible to this test suite.
const mkPage = (items) => ({ data: { content: items, last: true, totalElements: items.length } });

const renderPage = () =>
  render(
    <MemoryRouter>
      <AdminEditDispatch />
    </MemoryRouter>
  );

beforeEach(() => jest.clearAllMocks());

// ── Render ──────────────────────────────────────────────────────────────

describe('AdminEditDispatch — render', () => {
  test('shows page heading', () => {
    renderPage();
    expect(
      screen.getByRole('heading', { name: /modify or delete a medicine dispatch record/i })
    ).toBeInTheDocument();
  });

  test('has Back link to admin dashboard', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /back/i })).toHaveAttribute('href', '/admin/dashboard');
  });

  test('shows From Date and To Date inputs', () => {
    renderPage();
    expect(screen.getByLabelText(/from date/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/to date/i)).toBeInTheDocument();
  });

  test('shows Search button initially enabled', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /search/i })).not.toBeDisabled();
  });
});

// ── Search ──────────────────────────────────────────────────────────────

describe('AdminEditDispatch — search', () => {
  test('calls getTransactionHistory with ALL status after clicking Search', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        'ALL',
        expect.any(Number),
        expect.any(Number)
      )
    );
  });

  test('shows empty state when no records found', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/no dispatch records found/i)).toBeInTheDocument()
    );
  });

  test('shows results table with rows when records returned', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => screen.getByRole('table'));
    expect(screen.getByText('john.doe')).toBeInTheDocument();
    expect(screen.getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
  });

  test('shows error alert when search fails', async () => {
    api.getTransactionHistory.mockRejectedValue(new Error('Network error'));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to load records/i)
    );
  });

  test('does not crash and shows empty state when API returns a malformed (non-paginated) response', async () => {
    // Regression guard: if a future backend change alters the response shape again,
    // this must degrade to the empty state rather than crashing the page silently.
    api.getTransactionHistory.mockResolvedValue({ data: null });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/no dispatch records found/i)).toBeInTheDocument()
    );
  });
});

// ── Stock type column ──────────────────────────────────────────────────

describe('AdminEditDispatch — stock type column', () => {
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

  test('renders the Stock Type column header', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByRole('columnheader', { name: /stock type/i })).toBeInTheDocument()
    );
  });
});

// ── Price/Unit column ───────────────────────────────────────────────────

describe('AdminEditDispatch — price/unit column', () => {
  test('renders the Price/Unit column header', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByRole('columnheader', { name: /price\/unit/i })).toBeInTheDocument()
    );
  });

  test('shows the transaction pricePerUnit when set', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ pricePerUnit: 4500, price: 4000 })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => expect(screen.getByText('Rs 4,500')).toBeInTheDocument());
  });

  test('falls back to the medicine catalogue price when pricePerUnit is not set', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ pricePerUnit: null, price: 4000 })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => expect(screen.getByText('Rs 4,000')).toBeInTheDocument());
  });
});

// ── Screenshot column ──────────────────────────────────────────────────

describe('AdminEditDispatch — screenshot column', () => {
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

// ── Edit dispatch record ──────────────────────────────────────────────────

describe('AdminEditDispatch — edit', () => {
  test('clicking Edit shows notes textarea with current notes', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }));

    expect(screen.getByRole('textbox', { name: /edit notes/i })).toHaveValue('Clinic B dispatch today');
  });

  test('clicking Edit shows quantity and stock type editable with current values', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ quantity: 5, medicineStockType: 'REGULAR_MEDICINE_STOCK' })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }));

    expect(screen.getByRole('spinbutton', { name: /edit quantity/i })).toHaveValue(5);
    expect(screen.getByRole('combobox', { name: /edit stock type/i })).toHaveValue('REGULAR_MEDICINE_STOCK');
    expect(screen.getByLabelText(/replace screenshots/i)).toBeInTheDocument();
  });

  test('clicking Edit shows price per unit editable, prefilled from pricePerUnit', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ pricePerUnit: 4500 })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }));

    expect(screen.getByRole('spinbutton', { name: /edit price per unit/i })).toHaveValue(4500);
  });

  test('clicking Edit leaves price per unit blank when the record has no override', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ pricePerUnit: null, price: 4000 })]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }));

    expect(screen.getByRole('spinbutton', { name: /edit price per unit/i })).toHaveValue(null);
  });

  test('editing price per unit sends the new value', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ pricePerUnit: null, price: 4000 })]));
    api.updateTransaction.mockResolvedValue({ data: makeTx({ pricePerUnit: 4200 }) });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }));
    const priceInput = screen.getByRole('spinbutton', { name: /edit price per unit/i });
    await userEvent.type(priceInput, '4200');
    const textarea = screen.getByRole('textbox', { name: /edit notes/i });
    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'Correcting the recorded price for this dispatch');
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(api.updateTransaction).toHaveBeenCalledWith(1, {
        notes: 'Correcting the recorded price for this dispatch',
        quantity: '5',
        medicineStockType: 'REGULAR_MEDICINE_STOCK',
        pricePerUnit: '4200',
        screenshotFiles: [],
      })
    );
  });

  test('clicking Cancel hides the edit form and restores original notes', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }));
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(screen.queryByRole('textbox', { name: /edit notes/i })).not.toBeInTheDocument();
    expect(screen.getByText('Clinic B dispatch today')).toBeInTheDocument();
  });

  test('saving notes-only edit calls updateTransaction and updates row', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    api.updateTransaction.mockResolvedValue({
      data: { ...makeTx(), notes: 'Updated note here today' },
    });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }));
    const textarea = screen.getByRole('textbox', { name: /edit notes/i });
    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'Updated note here today');
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(api.updateTransaction).toHaveBeenCalledWith(1, {
        notes: 'Updated note here today',
        quantity: '5',
        medicineStockType: 'REGULAR_MEDICINE_STOCK',
        pricePerUnit: null,
        screenshotFiles: [],
      })
    );
    await waitFor(() =>
      expect(screen.getByText('Updated note here today')).toBeInTheDocument()
    );
  });

  test('editing quantity and stock type sends the new values, including on an APPROVED record', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx({ status: 'APPROVED', quantity: 5 })]));
    api.updateTransaction.mockResolvedValue({
      data: makeTx({ status: 'APPROVED', quantity: 8, medicineStockType: 'ADMIN_MEDICINE_STOCK' }),
    });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }));
    const qtyInput = screen.getByRole('spinbutton', { name: /edit quantity/i });
    await userEvent.clear(qtyInput);
    await userEvent.type(qtyInput, '8');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: /edit stock type/i }), 'ADMIN_MEDICINE_STOCK');
    const textarea = screen.getByRole('textbox', { name: /edit notes/i });
    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'Correcting quantity and stock type after review');
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(api.updateTransaction).toHaveBeenCalledWith(1, {
        notes: 'Correcting quantity and stock type after review',
        quantity: '8',
        medicineStockType: 'ADMIN_MEDICINE_STOCK',
        pricePerUnit: null,
        screenshotFiles: [],
      })
    );
  });

  test('shows inline error when save fails', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    api.updateTransaction.mockRejectedValue({
      response: { data: { message: 'Note must be between 5 and 500 characters' } },
    });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }));
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/note must be between/i)
    );
  });
});

// ── Delete ──────────────────────────────────────────────────────────────

describe('AdminEditDispatch — delete', () => {
  test('clicking Delete shows confirmation buttons', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^delete$/i }));

    expect(screen.getByRole('button', { name: /confirm delete/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
  });

  test('clicking Cancel on delete hides confirmation', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^delete$/i }));
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(screen.queryByRole('button', { name: /confirm delete/i })).not.toBeInTheDocument();
  });

  test('confirming delete calls deleteTransaction and removes row', async () => {
    api.getTransactionHistory.mockResolvedValue(
      mkPage([makeTx({ id: 1 }), makeTx({ id: 2, submittedByUsername: 'jane.smith' })])
    );
    api.deleteTransaction.mockResolvedValue({});
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    const rows = screen.getAllByRole('row');
    const firstDataRow = rows[1];
    await userEvent.click(within(firstDataRow).getByRole('button', { name: /^delete$/i }));
    await userEvent.click(screen.getByRole('button', { name: /confirm delete/i }));

    await waitFor(() =>
      expect(api.deleteTransaction).toHaveBeenCalledWith(1)
    );
    await waitFor(() =>
      expect(screen.queryByText('john.doe')).not.toBeInTheDocument()
    );
    expect(screen.getByText('jane.smith')).toBeInTheDocument();
  });

  test('shows inline error when delete fails', async () => {
    api.getTransactionHistory.mockResolvedValue(mkPage([makeTx()]));
    api.deleteTransaction.mockRejectedValue({
      response: { data: { message: 'Failed to delete record.' } },
    });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^delete$/i }));
    await userEvent.click(screen.getByRole('button', { name: /confirm delete/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to delete record/i)
    );
  });
});
