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

const USERS = [
  { id: 1, username: 'john.doe', fullName: 'John Doe', role: 'USER', active: true },
  { id: 2, username: 'jane.smith', fullName: 'Jane Smith', role: 'USER', active: true },
  { id: 3, username: 'admin', fullName: 'Admin', role: 'ADMIN', active: true },
];

const MEDICINES = [
  { id: 10, name: 'Shield FX Vial 10 ml', type: 'VIAL', specification: 10, price: 4000 },
  { id: 20, name: 'Shield FX Tablet 25 mg (10 Tablets)', type: 'TABLET', specification: 25, price: 4000 },
];

beforeEach(() => {
  jest.clearAllMocks();
  api.getUsers.mockResolvedValue({ data: USERS });
  api.getMedicines.mockResolvedValue({ data: MEDICINES });
});

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

describe('ViewReports — medicine stock by user report', () => {
  test('generates medicine-stock-by-user report', async () => {
    api.getReportMedicineStockByUser.mockResolvedValue(
      sampleReport('MEDICINE_STOCK_BY_USER', 'CURRENT MEDICINE STOCK PER USER\nJohn Doe: 50 units')
    );
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'medicine-stock-by-user'
    );
    expect(screen.getByRole('button', { name: /generate report/i })).not.toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByText(/john doe: 50 units/i)).toBeInTheDocument()
    );
    expect(api.getReportMedicineStockByUser).toHaveBeenCalledTimes(1);
  });

  test('medicine-stock-by-user report shows ADMIN MEDICINE STOCK section', async () => {
    const content = [
      'CURRENT MEDICINE STOCK LEVEL BY USER',
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
      'ADMIN MEDICINE STOCK',
      '---------------',
      'Shield FX Vial 10 ml | 20 mg/ml',
      '-----------------------------------',
      '  john.doe: 5',
      '  TOTAL: 5',
    ].join('\n');

    api.getReportMedicineStockByUser.mockResolvedValue(
      sampleReport('MEDICINE_STOCK_BY_USER', content)
    );
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'medicine-stock-by-user'
    );
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByText(/admin medicine stock/i)).toBeInTheDocument()
    );
  });
});

describe('ViewReports — medicine stock valuation report', () => {
  test('dropdown shows "Medicine Stock Valuation" option', () => {
    renderPage();
    expect(screen.getByRole('option', { name: /^medicine stock valuation$/i })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /current medicine stock valuation/i })).not.toBeInTheDocument();
  });

  test('generates medicine-stock-valuation report', async () => {
    api.getReportMedicineStockValuation.mockResolvedValue(
      sampleReport('MEDICINE_STOCK_VALUATION', 'MEDICINE STOCK VALUATION\nTOTAL VALUATION: Rs 200,000')
    );
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'medicine-stock-valuation'
    );
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByText(/total valuation: rs 200,000/i)).toBeInTheDocument()
    );
    expect(api.getReportMedicineStockValuation).toHaveBeenCalledTimes(1);
  });

  test('shows As of Date picker when medicine-stock-valuation is selected', async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'medicine-stock-valuation');
    expect(screen.getByLabelText(/as of date/i)).toBeInTheDocument();
  });

  test('As of Date picker not shown for other reports', async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'medicine-stock-by-user');
    expect(screen.queryByLabelText(/as of date/i)).not.toBeInTheDocument();
  });

  test('As of Date picker defaults to today', async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'medicine-stock-valuation');
    const today = new Date().toISOString().slice(0, 10);
    expect(screen.getByLabelText(/as of date/i)).toHaveValue(today);
  });

  test('calls getReportMedicineStockValuation with selected date', async () => {
    api.getReportMedicineStockValuation.mockResolvedValue(
      sampleReport('MEDICINE_STOCK_VALUATION', 'MEDICINE STOCK VALUATION\nAs of: 01 May 2026\nTOTAL VALUATION: Rs 0')
    );
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'medicine-stock-valuation');
    fireEvent.change(screen.getByLabelText(/as of date/i), { target: { value: '2026-05-01' } });
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(api.getReportMedicineStockValuation).toHaveBeenCalledWith('2026-05-01')
    );
  });

  test('renders report with new valuation format (Valuation: N units x Rs Y = Rs Z)', async () => {
    api.getReportMedicineStockValuation.mockResolvedValue(
      sampleReport('MEDICINE_STOCK_VALUATION',
        'MEDICINE STOCK VALUATION\nShield FX\n---------\nVial 10 ml\n  john.doe: 7 + 3 (in transit)\n  Valuation: 10 units x Rs 4,000 = Rs 40,000\n\nTOTAL VALUATION: Rs 40,000')
    );
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'medicine-stock-valuation');
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByText(/valuation: 10 units x rs 4,000 = rs 40,000/i)).toBeInTheDocument()
    );
    expect(screen.getByText(/7 \+ 3 \(in transit\)/i)).toBeInTheDocument();
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
      expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      undefined,
      undefined
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
      expect(api.getReportTodaySales).toHaveBeenCalledWith('2026-05-01', '2026-05-07', undefined, undefined)
    );
  });

  test("date range inputs are not shown for other reports", async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'medicine-stock-by-user');
    expect(screen.queryByLabelText(/from date/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/to date/i)).not.toBeInTheDocument();
  });

  test("shows User and Medicine Spec filter dropdowns when today-sales is selected", async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'today-sales');
    await waitFor(() => expect(screen.getByLabelText(/^user$/i)).toBeInTheDocument());
    expect(screen.getByLabelText(/medicine spec/i)).toBeInTheDocument();
  });

  test("User and Medicine Spec dropdowns default to All", async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'today-sales');
    await waitFor(() => expect(screen.getByLabelText(/^user$/i)).toHaveValue('ALL'));
    expect(screen.getByLabelText(/medicine spec/i)).toHaveValue('ALL');
  });

  test("User and Medicine Spec dropdowns are populated from the API", async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'today-sales');
    await waitFor(() =>
      expect(screen.getByRole('option', { name: /john doe \(john\.doe\)/i })).toBeInTheDocument()
    );
    expect(screen.getByRole('option', { name: /jane smith \(jane\.smith\)/i })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /admin.*admin/i })).not.toBeInTheDocument();
    expect(screen.getByRole('option', { name: /vial 10 ml/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /tablet 25 mg/i })).toBeInTheDocument();
  });

  test("selecting a user and medicine and generating sends them as query params", async () => {
    api.getReportTodaySales.mockResolvedValue(sampleReport('TODAY_SALES', 'TOTAL: Rs 0'));
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'today-sales');
    await waitFor(() => screen.getByRole('option', { name: /john doe \(john\.doe\)/i }));
    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');
    await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '10');
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(api.getReportTodaySales).toHaveBeenCalledWith(
        expect.any(String), expect.any(String), 'john.doe', '10'
      )
    );
  });

  test("changing the user filter after generating a report hides it until Generate is pressed again", async () => {
    api.getReportTodaySales.mockResolvedValue(sampleReport('TODAY_SALES', 'TOTAL: Rs 0'));
    renderPage();

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'today-sales');
    await waitFor(() => screen.getByRole('option', { name: /john doe \(john\.doe\)/i }));
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));
    await waitFor(() => expect(screen.getByText(/total: rs 0/i)).toBeInTheDocument());

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');

    expect(screen.queryByText(/total: rs 0/i)).not.toBeInTheDocument();
  });
});

describe('ViewReports — daily report', () => {
  test('generates daily report', async () => {
    api.getReportDaily.mockResolvedValue(
      sampleReport('DAILY_REPORT',
        'DAILY REPORT - 04 May 2026\nShield FX\n---------\n\nVial 10 ml\n  john.doe: 30\n  TOTAL: 30\n\nADMIN MEDICINE STOCK\n---------------\nVial 10 ml\n  (none)\n  TOTAL: 0\n\nDAILY TRANSACTION SUMMARY\n2 x 10 ml  Clinic B')
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

    await userEvent.selectOptions(screen.getByLabelText(/select report/i), 'medicine-stock-by-user');

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
    api.getReportMedicineStockByUser.mockRejectedValue(new Error('Network error'));
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'medicine-stock-by-user'
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
    api.getReportMedicineStockValuation.mockResolvedValue(
      sampleReport('MEDICINE_STOCK_VALUATION', 'TOTAL VALUATION: Rs 0')
    );
    renderPage();

    await userEvent.selectOptions(
      screen.getByLabelText(/select report/i),
      'medicine-stock-valuation'
    );
    await userEvent.click(screen.getByRole('button', { name: /generate report/i }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /copy to clipboard/i })).toBeInTheDocument()
    );
  });
});
