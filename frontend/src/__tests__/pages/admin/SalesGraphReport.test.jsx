import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import SalesGraphReport from '../../../pages/admin/SalesGraphReport';
import * as api from '../../../api/api';

jest.mock('../../../api/api');
jest.mock('../../../telemetry', () => ({ trackEvent: jest.fn() }));

const TWO_SPEC_DATA = {
    period: 'daily',
    dataPoints: [
        {
            label: '1 Jun',
            quantity: 30,
            value: 120000,
            specs: [
                { specName: 'Vial 10 ml', quantity: 20, value: 80000 },
                { specName: 'Vial 5 ml',  quantity: 10, value: 40000 },
            ],
        },
        {
            label: '2 Jun',
            quantity: 10,
            value: 40000,
            specs: [
                { specName: 'Vial 10 ml', quantity: 0, value: 0 },
                { specName: 'Vial 5 ml',  quantity: 10, value: 40000 },
            ],
        },
    ],
};

const mkResp = (data) => ({ data });

const renderPage = () =>
    render(
        <MemoryRouter>
            <SalesGraphReport />
        </MemoryRouter>
    );

beforeEach(() => jest.clearAllMocks());

// ── Initial render ──────────────────────────────────────────────────────────

describe('SalesGraphReport — initial render', () => {
    test('renders period toggle buttons', () => {
        api.getReportSalesGraph.mockReturnValue(new Promise(() => {}));
        renderPage();
        expect(screen.getByRole('button', { name: /daily/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /weekly/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /monthly/i })).toBeInTheDocument();
    });

    test('renders metric toggle buttons', () => {
        api.getReportSalesGraph.mockReturnValue(new Promise(() => {}));
        renderPage();
        expect(screen.getByRole('button', { name: /quantity/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /value/i })).toBeInTheDocument();
    });

    test('shows loading state immediately', () => {
        api.getReportSalesGraph.mockReturnValue(new Promise(() => {}));
        renderPage();
        expect(screen.getByText(/loading chart/i)).toBeInTheDocument();
    });

    test('renders From and To date inputs', () => {
        api.getReportSalesGraph.mockReturnValue(new Promise(() => {}));
        renderPage();
        expect(screen.getByLabelText(/^from$/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/^to$/i)).toBeInTheDocument();
    });
});

// ── Data fetching ───────────────────────────────────────────────────────────

describe('SalesGraphReport — data fetching', () => {
    test('calls getReportSalesGraph with default daily period on mount', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp(TWO_SPEC_DATA));
        renderPage();
        await waitFor(() => {
            expect(api.getReportSalesGraph).toHaveBeenCalledWith(
                'daily', expect.any(String), expect.any(String)
            );
        });
    });

    test('renders SVG chart after data loads', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp(TWO_SPEC_DATA));
        renderPage();
        await waitFor(() => {
            expect(screen.getByLabelText(/sales bar chart/i)).toBeInTheDocument();
        });
    });

    test('shows error alert on API failure', async () => {
        api.getReportSalesGraph.mockRejectedValue(new Error('Network error'));
        renderPage();
        await waitFor(() => {
            expect(screen.getByRole('alert')).toBeInTheDocument();
        });
    });

    test('shows empty-state message when no data points returned', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp({ period: 'daily', dataPoints: [] }));
        renderPage();
        await waitFor(() => {
            expect(screen.getByText(/no approved sales/i)).toBeInTheDocument();
        });
    });
});

// ── Summary cards ───────────────────────────────────────────────────────────

describe('SalesGraphReport — summary cards', () => {
    test('shows Total Units card', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp(TWO_SPEC_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByText(/total units/i)).toBeInTheDocument());
    });

    test('shows Total Value card', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp(TWO_SPEC_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByText(/total value/i)).toBeInTheDocument());
    });

    test('total units sums all data points', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp(TWO_SPEC_DATA));
        renderPage();
        // 30 + 10 = 40 — look inside the Total Units card, not the SVG y-axis ticks
        await waitFor(() => {
            const label = screen.getByText(/total units/i);
            expect(label.parentElement).toHaveTextContent('40');
        });
    });
});

// ── Legend ──────────────────────────────────────────────────────────────────

describe('SalesGraphReport — spec legend', () => {
    test('renders legend container', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp(TWO_SPEC_DATA));
        renderPage();
        await waitFor(() => {
            expect(screen.getByLabelText(/chart legend/i)).toBeInTheDocument();
        });
    });

    test('renders a legend entry for each spec', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp(TWO_SPEC_DATA));
        renderPage();
        await waitFor(() => {
            expect(screen.getByText('Vial 10 ml')).toBeInTheDocument();
            expect(screen.getByText('Vial 5 ml')).toBeInTheDocument();
        });
    });
});

// ── Period switching ────────────────────────────────────────────────────────

describe('SalesGraphReport — period switching', () => {
    test('clicking Weekly button refetches with weekly period', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp({ period: 'weekly', dataPoints: [] }));
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /weekly/i }));
        await waitFor(() => {
            expect(api.getReportSalesGraph).toHaveBeenCalledWith(
                'weekly', expect.any(String), expect.any(String)
            );
        });
    });

    test('clicking Monthly button refetches with monthly period', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp({ period: 'monthly', dataPoints: [] }));
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /monthly/i }));
        await waitFor(() => {
            expect(api.getReportSalesGraph).toHaveBeenCalledWith(
                'monthly', expect.any(String), expect.any(String)
            );
        });
    });
});

// ── Date validation ─────────────────────────────────────────────────────────

describe('SalesGraphReport — date validation', () => {
    test('shows error when From date is after To date', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp(TWO_SPEC_DATA));
        renderPage();
        await waitFor(() => expect(api.getReportSalesGraph).toHaveBeenCalled());

        fireEvent.change(screen.getByLabelText(/^from$/i), { target: { value: '2026-06-30' } });
        fireEvent.change(screen.getByLabelText(/^to$/i),   { target: { value: '2026-06-01' } });

        await waitFor(() => {
            expect(screen.getByRole('alert')).toBeInTheDocument();
        });
    });

    test('does not fetch when From > To', async () => {
        api.getReportSalesGraph.mockResolvedValue(mkResp(TWO_SPEC_DATA));
        renderPage();
        await waitFor(() => expect(api.getReportSalesGraph).toHaveBeenCalledTimes(1));

        // Change 'to' first so the range is immediately invalid (from>to), avoiding an extra fetch
        fireEvent.change(screen.getByLabelText(/^to$/i),   { target: { value: '2026-06-01' } });
        fireEvent.change(screen.getByLabelText(/^from$/i), { target: { value: '2026-07-01' } });

        // still only called once (from mount)
        await waitFor(() => expect(api.getReportSalesGraph).toHaveBeenCalledTimes(1));
    });
});
