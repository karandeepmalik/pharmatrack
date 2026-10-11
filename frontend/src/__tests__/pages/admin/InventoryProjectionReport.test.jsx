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

// Vial line: flat 140mg/week = 20mg/day. forecastQuantityMg totals 3200mg across 2 weeks, which
// divides cleanly at both reference volumes: 3200/160 (8ml) = 20.0 vials, 3200/200 (10ml) = 16.0.
const SAMPLE_DATA = {
    historicalLabels: ['1 Jan', '8 Jan'],
    forecastLabels: ['15 Jan', '22 Jan'],
    forecastStartDate: '2026-01-05',
    historyWeeks: 26,
    lines: [
        {
            name: 'Total Injectable',
            dosageForm: 'VIAL',
            historicalQuantityMg: [1000, 1400],
            forecastQuantityMg: [1600, 1600],
            forecastRevenue: [32000, 36000],
            totalForecastRevenue: 68000,
            regressionSlopeMg: 0, regressionInterceptMg: 140,
        },
    ],
    totalForecastRevenue: 68000,
};

// Demand already at/below zero by the forecast start and declining — never depletes any stock.
const SAMPLE_DATA_NEVER_DEPLETES = {
    ...SAMPLE_DATA,
    lines: [{ ...SAMPLE_DATA.lines[0], regressionSlopeMg: -1, regressionInterceptMg: 0 }],
};

// Tablet line: forecastQuantityMg totals 6000mg, which divides cleanly at all three reference
// strengths: 6000/200 (20mg) = 30.0, 6000/300 (30mg) = 20.0, 6000/500 (50mg) = 12.0 strips.
const SAMPLE_DATA_TABLET = {
    historicalLabels: ['1 Jan', '8 Jan'],
    forecastLabels: ['15 Jan', '22 Jan'],
    forecastStartDate: '2026-01-05',
    historyWeeks: 26,
    lines: [
        {
            name: 'Total Oral',
            dosageForm: 'TABLET',
            historicalQuantityMg: [500, 500],
            forecastQuantityMg: [3000, 3000],
            forecastRevenue: [10000, 10000],
            totalForecastRevenue: 20000,
            regressionSlopeMg: 0, regressionInterceptMg: 300,
        },
    ],
    totalForecastRevenue: 20000,
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
    test('shows a per-line card with the line name and the default-reference equivalent quantity', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            const cardLabel = screen.getByText(/total injectable — next/i);
            // 3200mg total / 160mg-per-vial (default 8ml reference) = 20.0 vials.
            expect(cardLabel.parentElement).toHaveTextContent('20.0 vials (8ml)');
        });
    });

    test('shows Total Projected Revenue card, never titled "Profit"', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByText(/total projected revenue/i)).toBeInTheDocument());
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

    test('renders the revenue total from the response', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            const revLabel = screen.getByText(/total projected revenue/i);
            expect(revLabel.parentElement).toHaveTextContent('68,000');
        });
    });
});

// ── Unit toggle ─────────────────────────────────────────────────────────────

describe('InventoryProjectionReport — unit toggle', () => {
    test('defaults to Vials / Strips and shows the equivalent-vial quantity', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            expect(screen.getByRole('button', { name: /vials \/ strips/i })).toHaveClass('btn-primary');
            const cardLabel = screen.getByText(/total injectable — next/i);
            expect(cardLabel.parentElement).toHaveTextContent('20.0 vials (8ml)');
        });
    });

    test('switching to mg shows the raw mg total without refetching', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(api.getReportInventoryProjection).toHaveBeenCalledTimes(1));

        await userEvent.click(screen.getByRole('button', { name: /^mg$/i }));

        expect(screen.getByRole('button', { name: /^mg$/i })).toHaveClass('btn-primary');
        const cardLabel = screen.getByText(/total injectable — next/i);
        expect(cardLabel.parentElement).toHaveTextContent('3200.0 mg');
        // Toggling units is a pure display transform — the already-fetched mg series covers both
        // modes, so no second API call should happen.
        expect(api.getReportInventoryProjection).toHaveBeenCalledTimes(1);
    });

    test('the reference-spec selectors are hidden in mg mode', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByLabelText(/injection vial reference volume/i)).toBeInTheDocument());

        await userEvent.click(screen.getByRole('button', { name: /^mg$/i }));

        expect(screen.queryByLabelText(/injection vial reference volume/i)).not.toBeInTheDocument();
    });
});

// ── Reference-spec selectors (vial) ──────────────────────────────────────────

describe('InventoryProjectionReport — injection vial reference', () => {
    test('defaults to 8ml and does not show the oral reference selector', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            expect(screen.getByRole('button', { name: '8ml' })).toHaveClass('btn-primary');
        });
        expect(screen.queryByLabelText(/oral tablet reference strength/i)).not.toBeInTheDocument();
    });

    test('switching to 10ml recomputes the equivalent vial count', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByRole('button', { name: '10ml' })).toBeInTheDocument());

        await userEvent.click(screen.getByRole('button', { name: '10ml' }));

        expect(screen.getByRole('button', { name: '10ml' })).toHaveClass('btn-primary');
        const cardLabel = screen.getByText(/total injectable — next/i);
        // 3200mg total / 200mg-per-vial (10ml reference) = 16.0 vials.
        expect(cardLabel.parentElement).toHaveTextContent('16.0 vials (10ml)');
    });
});

// ── Reference-spec selectors (tablet) ────────────────────────────────────────

describe('InventoryProjectionReport — oral tablet reference', () => {
    test('defaults to 30mg and does not show the vial reference selector', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA_TABLET));
        renderPage();
        await waitFor(() => {
            expect(screen.getByRole('button', { name: '30mg' })).toHaveClass('btn-primary');
        });
        expect(screen.queryByLabelText(/injection vial reference volume/i)).not.toBeInTheDocument();

        const cardLabel = screen.getByText(/total oral — next/i);
        // 6000mg total / 300mg-per-strip (default 30mg reference) = 20.0 strips.
        expect(cardLabel.parentElement).toHaveTextContent('20.0 strips (30mg)');
    });

    test('switching to 20mg recomputes the equivalent strip count', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA_TABLET));
        renderPage();
        await waitFor(() => expect(screen.getByRole('button', { name: '20mg' })).toBeInTheDocument());

        await userEvent.click(screen.getByRole('button', { name: '20mg' }));

        expect(screen.getByRole('button', { name: '20mg' })).toHaveClass('btn-primary');
        const cardLabel = screen.getByText(/total oral — next/i);
        // 6000mg total / 200mg-per-strip (20mg reference) = 30.0 strips.
        expect(cardLabel.parentElement).toHaveTextContent('30.0 strips (20mg)');
    });
});

// ── Legend ──────────────────────────────────────────────────────────────────

describe('InventoryProjectionReport — legend', () => {
    test('renders a legend entry for each line', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => {
            expect(screen.getByLabelText(/chart legend/i)).toBeInTheDocument();
            expect(screen.getByText('Total Injectable')).toBeInTheDocument();
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

// ── Stock-out date projection ────────────────────────────────────────────────

describe('InventoryProjectionReport — stock-out date projection', () => {
    test('shows no stock-out message until a stock value is entered', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByLabelText(/current stock/i)).toBeInTheDocument());

        expect(screen.queryByText(/projected to run out/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/not projected to run out/i)).not.toBeInTheDocument();
    });

    test('entering a stock value (in the default 8ml-equivalent vials) computes the stock-out date', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByLabelText(/current stock/i)).toBeInTheDocument());

        // 8 vials x 160mg/vial (8ml ref) = 1280mg, at a flat 20mg/day (140mg/week) rate:
        // ceil(1280/20) = 64 days from the 5 Jan 2026 anchor -> index 63 -> 9 Mar 2026.
        await userEvent.type(screen.getByLabelText(/current stock/i), '8');

        await waitFor(() => {
            expect(screen.getByText(/projected to run out around 9 mar 2026/i)).toBeInTheDocument();
        });
    });

    test('a declining-to-zero trend reports it will never run out', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA_NEVER_DEPLETES));
        renderPage();
        await waitFor(() => expect(screen.getByLabelText(/current stock/i)).toBeInTheDocument());

        await userEvent.type(screen.getByLabelText(/current stock/i), '8');

        await waitFor(() => {
            expect(screen.getByText(/not projected to run out within the next 10 years/i)).toBeInTheDocument();
        });
    });

    test('entering zero or a negative value shows no stock-out message', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByLabelText(/current stock/i)).toBeInTheDocument());

        await userEvent.type(screen.getByLabelText(/current stock/i), '0');

        expect(screen.queryByText(/projected to run out/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/not projected to run out/i)).not.toBeInTheDocument();
    });

    test('switching the unit toggle clears any entered stock value', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByLabelText(/current stock/i)).toBeInTheDocument());

        await userEvent.type(screen.getByLabelText(/current stock/i), '8');
        await waitFor(() => {
            expect(screen.getByText(/projected to run out around/i)).toBeInTheDocument();
        });

        await userEvent.click(screen.getByRole('button', { name: /^mg$/i }));

        expect(screen.getByLabelText(/current stock/i)).toHaveValue(null);
        expect(screen.queryByText(/projected to run out/i)).not.toBeInTheDocument();
    });

    test('switching the reference spec clears any entered stock value', async () => {
        api.getReportInventoryProjection.mockResolvedValue(mkResp(SAMPLE_DATA));
        renderPage();
        await waitFor(() => expect(screen.getByLabelText(/current stock/i)).toBeInTheDocument());

        await userEvent.type(screen.getByLabelText(/current stock/i), '8');
        await waitFor(() => {
            expect(screen.getByText(/projected to run out around/i)).toBeInTheDocument();
        });

        await userEvent.click(screen.getByRole('button', { name: '10ml' }));

        expect(screen.getByLabelText(/current stock/i)).toHaveValue(null);
        expect(screen.queryByText(/projected to run out/i)).not.toBeInTheDocument();
    });
});
