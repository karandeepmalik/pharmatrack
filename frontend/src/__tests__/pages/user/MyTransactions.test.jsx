import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
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
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/unknown/i)).toBeInTheDocument()
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
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/shield fx vial/i)).toBeInTheDocument()
    );
  });

  test('renders without crashing when screenshots field is absent', async () => {
    const tx = makeTx();
    delete tx.screenshots;
    api.getMyTransactions.mockResolvedValue(mkPage([tx]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/shield fx vial/i)).toBeInTheDocument()
    );
  });
});

// ── Transaction display ───────────────────────────────────────────────────

describe('MyTransactions — transaction display', () => {
  test('shows medicine name in transaction card', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx()]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/shield fx vial/i)).toBeInTheDocument()
    );
  });

  test('shows transaction quantity', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ quantity: 5 })]));
    renderPage();
    await waitFor(() => screen.getByText(/shield fx vial/i));
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
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/20 mg\/ml/i)).toBeInTheDocument()
    );
  });

  test('shows mg spec for TABLET medicine', async () => {
    api.getMyTransactions.mockResolvedValue(
      mkPage([makeTx({ medicineType: 'TABLET', specification: 25, concentrationMgPerMl: null })])
    );
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/25 mg/i)).toBeInTheDocument()
    );
  });

  test('shows screenshot attached indicator when screenshots list is non-empty', async () => {
    api.getMyTransactions.mockResolvedValue(
      mkPage([makeTx({ screenshots: [{ data: 'base64', contentType: 'image/png' }] })])
    );
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/attached ✓/i)).toBeInTheDocument()
    );
  });

  test('does not show screenshot indicator when screenshots list is empty', async () => {
    api.getMyTransactions.mockResolvedValue(mkPage([makeTx({ screenshots: [] })]));
    renderPage();
    await waitFor(() => screen.getByText(/shield fx vial/i));
    expect(screen.queryByText(/payment screenshot/i)).not.toBeInTheDocument();
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
