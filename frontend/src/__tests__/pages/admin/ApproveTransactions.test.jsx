import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ApproveTransactions from '../../../pages/admin/ApproveTransactions';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const FAKE_B64 = btoa('fake-image-bytes');

const makeTx = (overrides = {}) => ({
  id: 1,
  status: 'PENDING',
  submittedByUsername: 'john.doe',
  submittedByFullName: 'John Doe',
  medicineName: 'FIP Shield Vial',
  medicineType: 'VIAL',
  specification: 10,
  pharmaName: 'FIP Shield',
  quantity: 5,
  submittedAt: '2026-04-01T10:00:00',
  notes: 'Dispatched to Clinic B for FIP treatment',
  paymentScreenshot: null,
  paymentScreenshotType: null,
  approvedByUsername: null,
  approvedAt: null,
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <ApproveTransactions />
    </MemoryRouter>
  );

beforeEach(() => {
  jest.clearAllMocks();
});

// ── Loading & empty states ───────────────────────────────────────────────

describe('ApproveTransactions — loading & empty states', () => {
  test('shows loading indicator while fetching', () => {
    api.getAllTransactions.mockResolvedValue({ data: [] });
    renderPage();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  test('shows empty message when no PENDING transactions', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/no pending transactions found/i)).toBeInTheDocument()
    );
  });

  test('shows error alert when API call fails', async () => {
    api.getAllTransactions.mockRejectedValue(new Error('Network error'));
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to load/i)
    );
  });
});

// ── Navigation ────────────────────────────────────────────────────────────

describe('ApproveTransactions — navigation', () => {
  test('renders a Back link pointing to /admin/dashboard', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() =>
      expect(screen.queryByText(/loading/i)).not.toBeInTheDocument()
    );
    const backLink = screen.getByRole('link', { name: /← back/i });
    expect(backLink).toBeInTheDocument();
    expect(backLink).toHaveAttribute('href', '/admin/dashboard');
  });
});

// ── Transaction card rendering ────────────────────────────────────────────

describe('ApproveTransactions — transaction card', () => {
  test('renders transaction details including notes', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/dispatched to clinic b/i)).toBeInTheDocument()
    );
    expect(screen.getByText(/john doe/i)).toBeInTheDocument();
    expect(screen.getByText(/fip shield vial/i)).toBeInTheDocument();
  });

  test('shows Approve and Reject buttons for PENDING transactions', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /✓ approve/i }));
    expect(screen.getByRole('button', { name: /✕ reject/i })).toBeInTheDocument();
  });

  test('does not show action buttons for APPROVED transactions', async () => {
    api.getAllTransactions.mockResolvedValue({
      data: [makeTx({ id: 2, status: 'APPROVED', approvedByUsername: 'admin', approvedAt: '2026-04-01T11:00:00' })],
    });
    renderPage();

    // Switch to ALL filter to see approved tx
    await waitFor(() => screen.getByRole('button', { name: /^all$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^all$/i }));

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /✓ approve/i })).not.toBeInTheDocument()
    );
  });
});

// ── Payment screenshot — no screenshot ──────────────────────────────────

describe('ApproveTransactions — no screenshot uploaded', () => {
  test('shows "No screenshot" text when paymentScreenshot is null', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/no screenshot/i)).toBeInTheDocument()
    );
  });

  test('does not render screenshot thumbnail when paymentScreenshot is null', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [makeTx()] });
    renderPage();

    await waitFor(() => screen.getByText(/payment screenshot/i));
    expect(screen.queryByRole('button', { name: /view payment screenshot/i })).not.toBeInTheDocument();
  });
});

// ── Payment screenshot — with screenshot ────────────────────────────────

describe('ApproveTransactions — with screenshot', () => {
  const txWithScreenshot = makeTx({
    paymentScreenshot: FAKE_B64,
    paymentScreenshotType: 'image/png',
  });

  test('renders screenshot thumbnail button when screenshot is present', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [txWithScreenshot] });
    renderPage();

    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: /view payment screenshot for transaction #1/i })
      ).toBeInTheDocument()
    );
  });

  test('thumbnail img has correct data URI src', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [txWithScreenshot] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /view payment screenshot/i }));

    const img = screen.getByAltText(/payment screenshot for transaction #1/i);
    expect(img).toHaveAttribute('src', `data:image/png;base64,${FAKE_B64}`);
  });

  test('clicking thumbnail opens the lightbox dialog', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [txWithScreenshot] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /view payment screenshot/i }));
    await userEvent.click(screen.getByRole('button', { name: /view payment screenshot/i }));

    await waitFor(() =>
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    );
  });

  test('lightbox shows full-size image with correct src', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [txWithScreenshot] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /view payment screenshot/i }));
    await userEvent.click(screen.getByRole('button', { name: /view payment screenshot/i }));

    await waitFor(() => screen.getByRole('dialog'));
    const fullImg = screen.getByAltText(/full payment screenshot for transaction #1/i);
    expect(fullImg).toHaveAttribute('src', `data:image/png;base64,${FAKE_B64}`);
  });

  test('lightbox has a close button', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [txWithScreenshot] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /view payment screenshot/i }));
    await userEvent.click(screen.getByRole('button', { name: /view payment screenshot/i }));

    await waitFor(() => screen.getByRole('button', { name: /close screenshot viewer/i }));
    expect(screen.getByRole('button', { name: /close screenshot viewer/i })).toBeInTheDocument();
  });

  test('close button dismisses the lightbox', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [txWithScreenshot] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /view payment screenshot/i }));
    await userEvent.click(screen.getByRole('button', { name: /view payment screenshot/i }));

    await waitFor(() => screen.getByRole('dialog'));
    await userEvent.click(screen.getByRole('button', { name: /close screenshot viewer/i }));

    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    );
  });

  test('clicking the overlay backdrop closes the lightbox', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [txWithScreenshot] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /view payment screenshot/i }));
    await userEvent.click(screen.getByRole('button', { name: /view payment screenshot/i }));

    const overlay = await waitFor(() => screen.getByRole('dialog'));
    await userEvent.click(overlay);

    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    );
  });

  test('lightbox has a download link for the screenshot', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [txWithScreenshot] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /view payment screenshot/i }));
    await userEvent.click(screen.getByRole('button', { name: /view payment screenshot/i }));

    await waitFor(() => screen.getByRole('dialog'));
    const downloadLink = screen.getByRole('link', { name: /download/i });
    expect(downloadLink).toHaveAttribute('download');
    expect(downloadLink).toHaveAttribute('href', `data:image/png;base64,${FAKE_B64}`);
  });

  test('lightbox shows transaction ID in header', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [txWithScreenshot] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /view payment screenshot/i }));
    await userEvent.click(screen.getByRole('button', { name: /view payment screenshot/i }));

    await waitFor(() =>
      expect(screen.getByText(/payment screenshot.*transaction #1/i)).toBeInTheDocument()
    );
  });

  test('uses correct MIME type for JPEG screenshot', async () => {
    const jpegTx = makeTx({
      paymentScreenshot: FAKE_B64,
      paymentScreenshotType: 'image/jpeg',
    });
    api.getAllTransactions.mockResolvedValue({ data: [jpegTx] });
    renderPage();

    await waitFor(() => screen.getByRole('button', { name: /view payment screenshot/i }));
    const img = screen.getByAltText(/payment screenshot for transaction #1/i);
    expect(img).toHaveAttribute('src', `data:image/jpeg;base64,${FAKE_B64}`);
  });
});

// ── Filter tabs ───────────────────────────────────────────────────────────

describe('ApproveTransactions — filter tabs', () => {
  const pendingTx = makeTx({ id: 1, status: 'PENDING' });
  const approvedTx = makeTx({
    id: 2, status: 'APPROVED',
    notes: 'Monthly resupply for Ward 3',
    approvedByUsername: 'admin',
    approvedAt: '2026-04-01T12:00:00',
  });
  const rejectedTx = makeTx({
    id: 3, status: 'REJECTED',
    notes: 'Returned to stock',
    approvedByUsername: 'admin',
    approvedAt: '2026-04-01T13:00:00',
  });

  beforeEach(() => {
    api.getAllTransactions.mockResolvedValue({
      data: [pendingTx, approvedTx, rejectedTx],
    });
  });

  test('default filter is PENDING, shows only pending tx', async () => {
    renderPage();
    await waitFor(() => screen.getByText(/dispatched to clinic b/i));
    expect(screen.queryByText(/monthly resupply/i)).not.toBeInTheDocument();
  });

  test('ALL filter shows all transactions', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^all$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^all$/i }));

    await waitFor(() =>
      expect(screen.getByText(/monthly resupply for ward 3/i)).toBeInTheDocument()
    );
    expect(screen.getByText(/returned to stock/i)).toBeInTheDocument();
  });

  test('APPROVED filter shows only approved transactions', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^approved$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^approved$/i }));

    await waitFor(() =>
      expect(screen.getByText(/monthly resupply for ward 3/i)).toBeInTheDocument()
    );
    expect(screen.queryByText(/returned to stock/i)).not.toBeInTheDocument();
  });

  test('REJECTED filter shows only rejected transactions', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /^rejected$/i }));
    await userEvent.click(screen.getByRole('button', { name: /^rejected$/i }));

    await waitFor(() =>
      expect(screen.getByText(/returned to stock/i)).toBeInTheDocument()
    );
    expect(screen.queryByText(/monthly resupply/i)).not.toBeInTheDocument();
  });
});

// ── Approve / Reject actions ──────────────────────────────────────────────

describe('ApproveTransactions — approve and reject actions', () => {
  test('clicking Approve calls approveTransaction with approved: true', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [makeTx()] });
    api.approveTransaction.mockResolvedValue({ data: { id: 1, status: 'APPROVED' } });

    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /✓ approve/i }));
    await userEvent.click(screen.getByRole('button', { name: /✓ approve/i }));

    await waitFor(() =>
      expect(api.approveTransaction).toHaveBeenCalledWith(1, { approved: true })
    );
  });

  test('clicking Reject calls approveTransaction with approved: false', async () => {
    api.getAllTransactions.mockResolvedValue({ data: [makeTx()] });
    api.approveTransaction.mockResolvedValue({ data: { id: 1, status: 'REJECTED' } });

    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /✕ reject/i }));
    await userEvent.click(screen.getByRole('button', { name: /✕ reject/i }));

    await waitFor(() =>
      expect(api.approveTransaction).toHaveBeenCalledWith(1, { approved: false })
    );
  });

  test('refetches transactions after approve', async () => {
    api.getAllTransactions
      .mockResolvedValueOnce({ data: [makeTx()] })
      .mockResolvedValueOnce({ data: [] });
    api.approveTransaction.mockResolvedValue({});

    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /✓ approve/i }));
    await userEvent.click(screen.getByRole('button', { name: /✓ approve/i }));

    await waitFor(() => expect(api.getAllTransactions).toHaveBeenCalledTimes(2));
  });
});

// ── Multiple transactions with mixed screenshot states ────────────────────

describe('ApproveTransactions — multiple transactions', () => {
  test('renders screenshot for tx with one, and "No screenshot" for tx without', async () => {
    api.getAllTransactions.mockResolvedValue({
      data: [
        makeTx({ id: 1, status: 'PENDING', paymentScreenshot: FAKE_B64, paymentScreenshotType: 'image/png' }),
        makeTx({ id: 2, status: 'PENDING', notes: 'Second dispatch note here', paymentScreenshot: null }),
      ],
    });
    renderPage();

    // tx #1 has a screenshot — thumbnail button visible
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: /view payment screenshot for transaction #1/i })
      ).toBeInTheDocument()
    );
    // tx #2 has no screenshot — "No screenshot" label visible
    expect(screen.getByText(/no screenshot/i)).toBeInTheDocument();
  });
});
