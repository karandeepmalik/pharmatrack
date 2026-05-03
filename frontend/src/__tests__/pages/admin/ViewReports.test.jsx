import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ViewReports from '../../../pages/admin/ViewReports';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const renderPage = () =>
  render(
    <MemoryRouter>
      <ViewReports />
    </MemoryRouter>
  );

const sampleReport = (type, content) => ({
  data: { reportType: type, generatedAt: '01 Jan 2025, 12:00 PM', content },
});

beforeEach(() => jest.clearAllMocks());

// ── Initial render ───────────────────────────────────────────────────────

describe('ViewReports — render', () => {
  test('renders page heading', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /view reports/i })).toBeInTheDocument();
  });

  test('renders report select dropdown', () => {
    renderPage();
    expect(screen.getByLabelText(/select report/i)).toBeInTheDocument();
  });

  test('Generate Report button is disabled initially', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /generate report/i })).toBeDisabled();
  });

  test('has Back link to admin dashboard', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /back/i })).toHaveAttribute('href', '/admin/dashboard');
  });
});

// ── Generating reports ───────────────────────────────────────────────────

describe('ViewReports — inventory by user report', () => {
  test('generates inventory-by-user report', async () => {
    api.getReportInventoryByUser.mockResolvedValue(
      sampleReport('INVENTORY_BY_USER', 'CURRENT INVENTORY LEVEL BY USER\nJohn Doe: 50 units')
    );
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'inventory-by-user'
    );
    expect(screen.getByRole('button', { name: /generate report/i })).not.toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByText(/john doe: 50 units/i)).toBeInTheDocument()
    );
    expect(api.getReportInventoryByUser).toHaveBeenCalledTimes(1);
  });
});

describe('ViewReports — inventory valuation report', () => {
  test('generates inventory-valuation report', async () => {
    api.getReportInventoryValuation.mockResolvedValue(
      sampleReport('INVENTORY_VALUATION', 'CURRENT INVENTORY VALUATION\nTOTAL VALUATION: Rs 200,000')
    );
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'inventory-valuation'
    );
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByText(/total valuation: rs 200,000/i)).toBeInTheDocument()
    );
    expect(api.getReportInventoryValuation).toHaveBeenCalledTimes(1);
  });
});

describe("ViewReports — today's sales report", () => {
  test("generates today-sales report", async () => {
    api.getReportTodaySales.mockResolvedValue(
      sampleReport('TODAY_SALES', "TODAY'S SALES\nJohn Doe:\n  Shield FX Vial\nTOTAL: Rs 12,000")
    );
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'today-sales'
    );
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByText(/total: rs 12,000/i)).toBeInTheDocument()
    );
    expect(api.getReportTodaySales).toHaveBeenCalledTimes(1);
  });
});

// ── Error handling ───────────────────────────────────────────────────────

describe('ViewReports — error handling', () => {
  test('shows error alert when API fails', async () => {
    api.getReportInventoryByUser.mockRejectedValue(new Error('Network error'));
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'inventory-by-user'
    );
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to generate report/i)
    );
  });
});

// ── Copy to clipboard ────────────────────────────────────────────────────

describe('ViewReports — copy to clipboard', () => {
  test('shows Copy to Clipboard button after report is generated', async () => {
    api.getReportInventoryValuation.mockResolvedValue(
      sampleReport('INVENTORY_VALUATION', 'TOTAL VALUATION: Rs 0')
    );
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'inventory-valuation'
    );
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /copy to clipboard/i })).toBeInTheDocument()
    );
  });
});
