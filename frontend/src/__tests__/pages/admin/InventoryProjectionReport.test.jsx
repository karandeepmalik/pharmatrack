import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import InventoryProjectionReport from '../../../pages/admin/InventoryProjectionReport';
import * as api from '../../../api/api';

jest.mock('../../../api/api');
jest.mock('../../../telemetry', () => ({ trackEvent: jest.fn() }));

const USERS = [
    { id: 1, username: 'dhairya', fullName: 'Dhairya', location: 'Mumbai' },
    { id: 2, username: 'riona', fullName: 'Riona', location: 'Delhi' },
    { id: 3, username: 'noloc', fullName: 'No Location', location: '' },
];

const SAMPLE_DATA = {
    historicalLabels: ['1 Jan', '8 Jan'],
    forecastLabels: ['15 Jan', '22 Jan'],
    lines: [
        {
            name: 'Shield FX Vial 10 ml',
            historicalQuantity: [5, 7],
            forecastQuantity: [8, 9],
            forecastRevenue: [32000, 36000],
        },
    ],
    totalForecastQuantity: 17,
    totalForecastRevenue: 68000,
};

const mkResp = (data) => ({ data });

const renderPage = (users = USERS) => render(<InventoryProjectionReport users={users} />);

beforeEach(() => jest.clearAllMocks());

// ── Initial render ──────────────────────────────────────────────────────────

describe('InventoryProjectionReport — initial render', () => {
    test('renders weeks-ahead toggle buttons, defaulting to 8 Weeks', () => {
        api.getReportInventoryProjection.mockReturnValue(new Promise(() => {}));
        renderPage();
        expect(screen.getByRole('button', { name: /8 weeks/i })).toHaveClass('btn-primary');
        expect(screen.getByRole('button', { name: /12 weeks/i })).toHaveClass('btn-secondary');
    });

    test('renders Overall button, active by default', () => {
        api.getReportInventoryProjection.mockReturnValue(new Promise(() => {}));
        renderPage();
        expect(screen.getByRole('button', { name: /^overall$/i })).toHaveClass('btn-primary');
    });

    test('renders one city button per distinct non-blank user location', () => {
        api.getReportInventoryProjection.mockReturnValue(new Promise(() => {}));
        renderPage();
        expect(screen.getByRole('button', { name: /^mumbai$/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /^delhi$/i })).toBeInTheDocument();
    });

    test('shows loading state immediately', () => {
        api.getReportInventoryProjection.mockReturnValue(new Promise(() => {}));
        renderPage();
        expect(screen.getByText(/loading projection/i)).toBeInTheDocument();
    });
});

// ── Data fetching ───────────────────────────────────────────────────────────

describe('InventoryProjectionReport — data fetching', () => {
    test('calls getReportInventoryProjection with 8 weeks and no city filter on mount', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            expect(api.getReportInventoryProjection).toHaveBeenCalledWith(8, undefined);
        });
    });

    test('renders SVG chart after data loads', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            expect(screen.getByLabelText(/inventory projection line chart/i)).toBeInTheDocument();
        });
    });

    test('shows error alert on API failure', async () => {
        api.getReportInventoryProjection.mockRejectedValue(new Error('Network error'));
        renderPage();
        await waitFor(() => {
            expect(screen.getByRole('alert')).toBeInTheDocument();
        });
    });

    test('shows empty-state message when no lines returned', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp({ ...SAMPLE_DATA, lines: [] }));
        renderPage();
        await waitFor(() => {
            expect(screen.getByText(/not enough sales history/i)).toBeInTheDocument();
        });
    });
});

// ── Summary cards ───────────────────────────────────────────────────────────

describe('InventoryProjectionReport — summary cards', () => {
    test('shows Projected Stock Needed card', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByText(/projected stock needed/i)).toBeInTheDocument());
    });

    test('shows Projected Revenue card, never titled "Profit"', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByText(/projected revenue/i)).toBeInTheDocument());
        // The caveat note legitimately says "...not profit..." — only a standalone "Profit" title/label is disallowed.
        expect(screen.queryByText(/^profit$/i)).not.toBeInTheDocument();
    });

    test('shows the revenue-not-profit caveat note', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            expect(screen.getByText(/revenue, not profit/i)).toBeInTheDocument();
        });
    });

    test('renders the totals from the response', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            const qtyLabel = screen.getByText(/projected stock needed/i);
            expect(qtyLabel.parentElement).toHaveTextContent('17.0');
            const revLabel = screen.getByText(/projected revenue/i);
            expect(revLabel.parentElement).toHaveTextContent('68,000');
        });
    });
});

// ── Legend ──────────────────────────────────────────────────────────────────

describe('InventoryProjectionReport — legend', () => {
    test('renders a legend entry for each line', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            expect(screen.getByLabelText(/chart legend/i)).toBeInTheDocument();
            expect(screen.getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
        });
    });
});

// ── Horizon switching ───────────────────────────────────────────────────────

describe('InventoryProjectionReport — horizon switching', () => {
    test('clicking 12 Weeks refetches with weeksAhead=12 and highlights the button', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(api.getReportInventoryProjection).toHaveBeenCalled());

        await userEvent.click(screen.getByRole('button', { name: /12 weeks/i }));

        await waitFor(() => {
            expect(api.getReportInventoryProjection).toHaveBeenCalledWith(12, undefined);
        });
        expect(screen.getByRole('button', { name: /12 weeks/i })).toHaveClass('btn-primary');
        expect(screen.getByRole('button', { name: /8 weeks/i })).toHaveClass('btn-secondary');
    });
});

// ── City filtering ──────────────────────────────────────────────────────────

describe('InventoryProjectionReport — city filtering', () => {
    test('clicking a city refetches with that city in an array and shows the attribution caveat', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(api.getReportInventoryProjection).toHaveBeenCalled());

        await userEvent.click(screen.getByRole('button', { name: /^mumbai$/i }));

        await waitFor(() => {
            expect(api.getReportInventoryProjection).toHaveBeenCalledWith(8, ['Mumbai']);
        });
        expect(screen.getByRole('button', { name: /^mumbai$/i })).toHaveClass('btn-primary');
        expect(screen.getByRole('button', { name: /^overall$/i })).toHaveClass('btn-secondary');
        expect(screen.getByText(/city attribution is based on/i)).toBeInTheDocument();
    });

    test('selecting a second city adds it to the filter array (multi-select)', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(api.getReportInventoryProjection).toHaveBeenCalled());

        await userEvent.click(screen.getByRole('button', { name: /^mumbai$/i }));
        await waitFor(() => {
            expect(api.getReportInventoryProjection).toHaveBeenCalledWith(8, ['Mumbai']);
        });

        await userEvent.click(screen.getByRole('button', { name: /^delhi$/i }));
        await waitFor(() => {
            expect(api.getReportInventoryProjection).toHaveBeenCalledWith(8, ['Mumbai', 'Delhi']);
        });
    });

    test('clicking Overall after selecting a city clears the filter', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(api.getReportInventoryProjection).toHaveBeenCalled());

        await userEvent.click(screen.getByRole('button', { name: /^mumbai$/i }));
        await waitFor(() => {
            expect(api.getReportInventoryProjection).toHaveBeenCalledWith(8, ['Mumbai']);
        });

        await userEvent.click(screen.getByRole('button', { name: /^overall$/i }));
        await waitFor(() => {
            expect(api.getReportInventoryProjection).toHaveBeenLastCalledWith(8, undefined);
        });
        expect(screen.queryByText(/city attribution is based on/i)).not.toBeInTheDocument();
    });
});
