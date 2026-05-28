import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import MyTransactions from '../../../pages/user/MyTransactions';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

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
  paymentScreenshot: null,
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <MyTransactions />
    </MemoryRouter>
  );

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
    api.getMyTransactions.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /medicine dispatch history/i })).toBeInTheDocument()
    );
  });

  test('renders Back link to user dashboard', async () => {
    api.getMyTransactions.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('link', { name: /← back/i })).toHaveAttribute('href', '/user/dashboard')
    );
  });

  test('renders ALL, PENDING, APPROVED, REJECTED filter tabs', async () => {
    api.getMyTransactions.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() => screen.getByRole('group'));
    expect(screen.getByRole('button', { name: /^all$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^pending$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^approved$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^rejected$/i })).toBeInTheDocument();
  });
});

// ── Empty state ───────────────────────────────────────────────────────────

describe('MyTransactions — empty state', () => {
  test('shows empty message when no transactions exist', async () => {
    api.getMyTransactions.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/no transactions found/i)).toBeInTheDocument()
    );
  });
});

// ── Transaction display ───────────────────────────────────────────────────

describe('MyTransactions — transaction display', () => {
  test('shows medicine name in transaction card', async () => {
    api.getMyTransactions.mockResolvedValue({ data: [makeTx()] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/shield fx vial/i)).toBeInTheDocument()
    );
  });

  test('shows transaction quantity', async () => {
    api.getMyTransactions.mockResolvedValue({ data: [makeTx({ quantity: 5 })] });
    renderPage();
    await waitFor(() => screen.getByText(/shield fx vial/i));
    expect(screen.getByText(/quantity/i)).toBeInTheDocument();
  });

  test('shows APPROVED badge for approved transaction', async () => {
    api.getMyTransactions.mockResolvedValue({ data: [makeTx({ status: 'APPROVED' })] });
    renderPage();
    // Filter tab also contains 'APPROVED'; expect at least 2 (tab + badge)
    await waitFor(() =>
      expect(screen.getAllByText('APPROVED').length).toBeGreaterThanOrEqual(2)
    );
  });

  test('shows PENDING badge for pending transaction', async () => {
    api.getMyTransactions.mockResolvedValue({ data: [makeTx({ status: 'PENDING' })] });
    renderPage();
    await waitFor(() =>
      expect(screen.getAllByText('PENDING').length).toBeGreaterThanOrEqual(2)
    );
  });

  test('shows REJECTED badge for rejected transaction', async () => {
    api.getMyTransactions.mockResolvedValue({ data: [makeTx({ status: 'REJECTED' })] });
    renderPage();
    await waitFor(() =>
      expect(screen.getAllByText('REJECTED').length).toBeGreaterThanOrEqual(2)
    );
  });

  test('shows mg/ml spec for VIAL medicine', async () => {
    api.getMyTransactions.mockResolvedValue({ data: [makeTx({ medicineType: 'VIAL', concentrationMgPerMl: 20 })] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/20 mg\/ml/i)).toBeInTheDocument()
    );
  });

  test('shows mg (10 Tablets) spec for TABLET medicine', async () => {
    api.getMyTransactions.mockResolvedValue({
      data: [makeTx({ medicineType: 'TABLET', specification: 25, concentrationMgPerMl: null })],
    });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/25 mg \(10 tablets\)/i)).toBeInTheDocument()
    );
  });
});

// ── Filter tabs ───────────────────────────────────────────────────────────

describe('MyTransactions — filter tabs', () => {
  const pending  = makeTx({ id: 1, status: 'PENDING',   notes: 'Pending note text here' });
  const approved = makeTx({ id: 2, status: 'APPROVED',  notes: 'Approved note text here' });
  const rejected = makeTx({ id: 3, status: 'REJECTED',  notes: 'Rejected note text here' });

  beforeEach(() => {
    api.getMyTransactions.mockResolvedValue({ data: [pending, approved, rejected] });
  });

  test('ALL tab shows all three transactions by default', async () => {
    renderPage();
    // Wait for data and verify all three note texts are visible (unique per transaction)
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
    api.getMyTransactions.mockResolvedValue({ data: [approved] });
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^pending$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^pending$/i }));

    expect(screen.getByText(/no transactions found/i)).toBeInTheDocument();
  });
});
