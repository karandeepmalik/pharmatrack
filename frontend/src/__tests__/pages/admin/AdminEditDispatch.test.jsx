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
  ...overrides,
});

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
    api.getTransactionHistory.mockResolvedValue({ data: [] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(api.getTransactionHistory).toHaveBeenCalledWith(
        expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        'ALL'
      )
    );
  });

  test('shows empty state when no records found', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText(/no dispatch records found/i)).toBeInTheDocument()
    );
  });

  test('shows results table with rows when records returned', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
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
});

// ── Edit notes ──────────────────────────────────────────────────────────

describe('AdminEditDispatch — edit notes', () => {
  test('clicking Edit Notes shows textarea with current notes', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /edit notes/i }));

    expect(screen.getByRole('textbox', { name: /edit notes/i })).toHaveValue('Clinic B dispatch today');
  });

  test('clicking Cancel hides textarea and restores original notes', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /edit notes/i }));
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(screen.queryByRole('textbox', { name: /edit notes/i })).not.toBeInTheDocument();
    expect(screen.getByText('Clinic B dispatch today')).toBeInTheDocument();
  });

  test('saving notes calls updateTransaction and updates row', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
    api.updateTransaction.mockResolvedValue({
      data: { ...makeTx(), notes: 'Updated note here today' },
    });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /edit notes/i }));
    const textarea = screen.getByRole('textbox', { name: /edit notes/i });
    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'Updated note here today');
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(api.updateTransaction).toHaveBeenCalledWith(1, { notes: 'Updated note here today' })
    );
    await waitFor(() =>
      expect(screen.getByText('Updated note here today')).toBeInTheDocument()
    );
  });

  test('shows inline error when save fails', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
    api.updateTransaction.mockRejectedValue({
      response: { data: { message: 'Note must be between 5 and 500 characters' } },
    });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /edit notes/i }));
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/note must be between/i)
    );
  });
});

// ── Delete ──────────────────────────────────────────────────────────────

describe('AdminEditDispatch — delete', () => {
  test('clicking Delete shows confirmation buttons', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^delete$/i }));

    expect(screen.getByRole('button', { name: /confirm delete/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
  });

  test('clicking Cancel on delete hides confirmation', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    await waitFor(() => screen.getByRole('table'));

    await userEvent.click(screen.getByRole('button', { name: /^delete$/i }));
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(screen.queryByRole('button', { name: /confirm delete/i })).not.toBeInTheDocument();
  });

  test('confirming delete calls deleteTransaction and removes row', async () => {
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx({ id: 1 }), makeTx({ id: 2, submittedByUsername: 'jane.smith' })] });
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
    api.getTransactionHistory.mockResolvedValue({ data: [makeTx()] });
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
