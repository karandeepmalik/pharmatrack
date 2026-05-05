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

beforeEach(() => jest.clearAllMocks());

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

  test('renders Search button', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /search/i })).toBeInTheDocument();
  });

  test('does not show results table before search', () => {
    renderPage();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
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
