import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
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
      sampleReport('INVENTORY_BY_USER', 'CURRENT MEDICINE STOCK PER USER\nJohn Doe: 50 units')
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

  test('inventory-by-user report shows ADMIN INVENTORY section', async () => {
    const content = [
      'CURRENT INVENTORY LEVEL BY USER',
      'Generated: 01 Jan 2025, 12:00 PM IST',
      '========================================',
      '',
      'Shield FX',
      '---------',
      'Shield FX Vial 10 ml | 20 mg/ml',
      '-----------------------------------',
      '  john.doe: 50',
      '  TOTAL: 50',
      '',
      '========================================',
      'ADMIN INVENTORY',
      '---------------',
      'Shield FX Vial 10 ml | 20 mg/ml',
      '-----------------------------------',
      '  john.doe: 5',
      '  TOTAL: 5',
    ].join('\n');

    api.getReportInventoryByUser.mockResolvedValue(
      sampleReport('INVENTORY_BY_USER', content)
    );
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'inventory-by-user'
    );
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByText(/admin inventory/i)).toBeInTheDocument()
    );
  });
});

describe('ViewReports — inventory valuation report', () => {
  test('dropdown shows "Medicine Stock Valuation" option', () => {
    renderPage();
    expect(screen.getByRole('option', { name: /^medicine stock valuation$/i })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /current medicine stock valuation/i })).not.toBeInTheDocument();
  });

  test('generates inventory-valuation report', async () => {
    api.getReportInventoryValuation.mockResolvedValue(
      sampleReport('INVENTORY_VALUATION', 'MEDICINE STOCK VALUATION\nTOTAL VALUATION: Rs 200,000')
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

  test('shows As of Date picker when inventory-valuation is selected', async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'inventory-valuation');
    expect(screen.getByLabelText(/as of date/i)).toBeInTheDocument();
  });

  test('As of Date picker not shown for other reports', async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'inventory-by-user');
    expect(screen.queryByLabelText(/as of date/i)).not.toBeInTheDocument();
  });

  test('As of Date picker defaults to today', async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'inventory-valuation');
    const today = new Date().toISOString().slice(0, 10);
    expect(screen.getByLabelText(/as of date/i)).toHaveValue(today);
  });

  test('calls getReportInventoryValuation with selected date', async () => {
    api.getReportInventoryValuation.mockResolvedValue(
      sampleReport('INVENTORY_VALUATION', 'MEDICINE STOCK VALUATION\nAs of: 01 May 2026\nTOTAL VALUATION: Rs 0')
    );
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'inventory-valuation');
    fireEvent.change(screen.getByLabelText(/as of date/i), { target: { value: '2026-05-01' } });
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(api.getReportInventoryValuation).toHaveBeenCalledWith('2026-05-01')
    );
  });
});

describe("ViewReports — sales report label", () => {
  test('dropdown shows "Sales Report" option (not "Today\'s Sales")', () => {
    renderPage();
    expect(screen.getByRole('option', { name: /^sales report$/i })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /today's sales/i })).not.toBeInTheDocument();
  });
});

describe("ViewReports — today's sales report", () => {
  test("generates today-sales report with default date range (today)", async () => {
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
    expect(api.getReportTodaySales).toHaveBeenCalledWith(
      expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/)
    );
  });

  test("shows From Date and To Date inputs when today-sales is selected", async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'today-sales');
    expect(screen.getByLabelText(/from date/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/to date/i)).toBeInTheDocument();
  });

  test("from and to date inputs default to today", async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'today-sales');
    const today = new Date().toISOString().slice(0, 10);
    expect(screen.getByLabelText(/from date/i)).toHaveValue(today);
    expect(screen.getByLabelText(/to date/i)).toHaveValue(today);
  });

  test("passes selected date range to API call", async () => {
    api.getReportTodaySales.mockResolvedValue(
      sampleReport('TODAY_SALES', 'SALES - 01 May 2026 to 07 May 2026\nTOTAL: Rs 0')
    );
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'today-sales');
    fireEvent.change(screen.getByLabelText(/from date/i), { target: { value: '2026-05-01' } });
    fireEvent.change(screen.getByLabelText(/to date/i), { target: { value: '2026-05-07' } });
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(api.getReportTodaySales).toHaveBeenCalledWith('2026-05-01', '2026-05-07')
    );
  });

  test("date range inputs are not shown for other reports", async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'inventory-by-user');
    expect(screen.queryByLabelText(/from date/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/to date/i)).not.toBeInTheDocument();
  });
});

describe('ViewReports — daily report', () => {
  test('generates daily report', async () => {
    api.getReportDaily.mockResolvedValue(
      sampleReport('DAILY_REPORT',
        'DAILY REPORT - 04 May 2026\nShield FX\n---------\n\nVial 10 ml\n  john.doe: 30\n  TOTAL: 30\n\nADMIN INVENTORY\n---------------\nVial 10 ml\n  (none)\n  TOTAL: 0\n\nDAILY TRANSACTION SUMMARY\n2 x 10 ml  Clinic B')
    );
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'daily');
    expect(screen.getByRole('button', { name: /generate report/i })).not.toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByText(/vial 10 ml/i)).toBeInTheDocument()
    );
    expect(api.getReportDaily).toHaveBeenCalledTimes(1);
  });

  test('daily report option appears in dropdown', () => {
    renderPage();
    expect(screen.getByRole('option', { name: /daily report/i })).toBeInTheDocument();
  });

  test('shows date picker when daily report is selected', async () => {
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'daily');

    expect(screen.getByLabelText(/report date/i)).toBeInTheDocument();
  });

  test('date picker is not shown for other reports', async () => {
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'inventory-by-user');

    expect(screen.queryByLabelText(/report date/i)).not.toBeInTheDocument();
  });

  test('calls getReportDaily with a date string when generate is clicked', async () => {
    api.getReportDaily.mockResolvedValue(
      sampleReport('DAILY_REPORT', 'DAILY REPORT - 05 May 2026')
    );
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'daily');
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      // The date input is initialised to today in YYYY-MM-DD format
      expect(api.getReportDaily).toHaveBeenCalledWith(
        expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/)
      )
    );
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
