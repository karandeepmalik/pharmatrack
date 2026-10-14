import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import StockRunwayReport from '../../../pages/admin/StockRunwayReport';
import * as api from '../../../api/api';

jest.mock('../../../api/api');
jest.mock('../../../telemetry', () => ({ trackEvent: jest.fn() }));

const USERS = [
    { id: 1, username: 'dhairya', fullName: 'Dhairya', location: 'Mumbai' },
    { id: 2, username: 'riona', fullName: 'Riona', location: 'Delhi' },
];

const SAMPLE_RESPONSE = {
    lines: [
        {
            name: 'Total Oral',
            unit: 'strips (30mg)',
            currentStock: 50,
            insufficientData: false,
            stockOutDate: '2026-03-01',
            dateLabels: ['14 Oct', '21 Oct', '28 Oct'],
            remainingQuantity: [50, 30, 0],
        },
    ],
};

const mkResp = (data) => ({ data });

const renderPage = (users = USERS) => render(<StockRunwayReport users={users} />);

beforeEach(() => jest.clearAllMocks());

// ── Initial render ──────────────────────────────────────────────────────────

describe('StockRunwayReport — initial render', () => {
    test('renders city select defaulting to Overall', () => {
        renderPage();
        expect(screen.getByLabelText(/^city$/i)).toHaveValue('');
    });

    test('renders one city option per distinct user location', () => {
        renderPage();
        const select = screen.getByLabelText(/^city$/i);
        expect(select).toHaveTextContent('Mumbai');
        expect(select).toHaveTextContent('Delhi');
    });

    test('renders Oral and Vial stock inputs', () => {
        renderPage();
        expect(screen.getByLabelText(/oral stock on hand/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/injection vial stock on hand/i)).toBeInTheDocument();
    });

    test('shows the Strip Strength selector by default (oral unit defaults to Strips)', () => {
        renderPage();
        expect(screen.getByLabelText(/strip strength/i)).toBeInTheDocument();
    });

    test('Generate button is disabled until a stock value is entered', () => {
        renderPage();
        expect(screen.getByRole('button', { name: /generate stock runway/i })).toBeDisabled();
    });

    test('does not fetch anything on mount', () => {
        renderPage();
        expect(api.getReportStockRunway).not.toHaveBeenCalled();
    });
});

// ── Form interaction ─────────────────────────────────────────────────────────

describe('StockRunwayReport — form interaction', () => {
    test('entering an oral stock value enables Generate', async () => {
        renderPage();
        await userEvent.type(screen.getByLabelText(/oral stock on hand/i), '50');
        expect(screen.getByRole('button', { name: /generate stock runway/i })).toBeEnabled();
    });

    test('entering a vial stock value alone also enables Generate', async () => {
        renderPage();
        await userEvent.type(screen.getByLabelText(/injection vial stock on hand/i), '20');
        expect(screen.getByRole('button', { name: /generate stock runway/i })).toBeEnabled();
    });

    test('switching oral unit to mg hides the Strip Strength selector', async () => {
        renderPage();
        await userEvent.selectOptions(screen.getByLabelText(/oral unit/i), 'MG');
        expect(screen.queryByLabelText(/strip strength/i)).not.toBeInTheDocument();
    });
});

// ── Generating ────────────────────────────────────────────────────────────────

describe('StockRunwayReport — generating', () => {
    test('clicking Generate calls the API with the entered values', async () => {
        api.getReportStockRunway.mockResolvedValue(mkResp(SAMPLE_RESPONSE));
        renderPage();

        await userEvent.selectOptions(screen.getByLabelText(/^city$/i), 'Mumbai');
        await userEvent.type(screen.getByLabelText(/oral stock on hand/i), '50');
        await userEvent.type(screen.getByLabelText(/injection vial stock on hand/i), '20');
        await userEvent.click(screen.getByRole('button', { name: /generate stock runway/i }));

        await waitFor(() => {
            expect(api.getReportStockRunway).toHaveBeenCalledWith('Mumbai', 50, 'STRIPS', 30, 20, 8);
        });
    });

    test('omits oral/vial args when that field is left blank', async () => {
        api.getReportStockRunway.mockResolvedValue(mkResp(SAMPLE_RESPONSE));
        renderPage();

        await userEvent.type(screen.getByLabelText(/oral stock on hand/i), '50');
        await userEvent.click(screen.getByRole('button', { name: /generate stock runway/i }));

        await waitFor(() => {
            expect(api.getReportStockRunway).toHaveBeenCalledWith(
                undefined, 50, 'STRIPS', 30, undefined, undefined
            );
        });
    });

    test('shows a results table with the stock-out date after generating', async () => {
        api.getReportStockRunway.mockResolvedValue(mkResp(SAMPLE_RESPONSE));
        renderPage();

        await userEvent.type(screen.getByLabelText(/oral stock on hand/i), '50');
        await userEvent.click(screen.getByRole('button', { name: /generate stock runway/i }));

        await waitFor(() => {
            expect(screen.getByText('Total Oral')).toBeInTheDocument();
            expect(screen.getByText('2026-03-01')).toBeInTheDocument();
        });
    });

    test('renders the receding-stock chart', async () => {
        api.getReportStockRunway.mockResolvedValue(mkResp(SAMPLE_RESPONSE));
        renderPage();

        await userEvent.type(screen.getByLabelText(/oral stock on hand/i), '50');
        await userEvent.click(screen.getByRole('button', { name: /generate stock runway/i }));

        await waitFor(() => {
            expect(screen.getByLabelText(/receding stock chart/i)).toBeInTheDocument();
        });
    });

    test('shows an insufficient-data caveat when the line has no history', async () => {
        api.getReportStockRunway.mockResolvedValue(mkResp({
            lines: [{ ...SAMPLE_RESPONSE.lines[0], insufficientData: true, stockOutDate: null }],
        }));
        renderPage();

        await userEvent.type(screen.getByLabelText(/oral stock on hand/i), '50');
        await userEvent.click(screen.getByRole('button', { name: /generate stock runway/i }));

        await waitFor(() => {
            expect(screen.getByText(/no sales history for this selection/i)).toBeInTheDocument();
        });
    });

    test('shows "doesn\'t run out" when stockOutDate is null but data exists', async () => {
        api.getReportStockRunway.mockResolvedValue(mkResp({
            lines: [{ ...SAMPLE_RESPONSE.lines[0], stockOutDate: null }],
        }));
        renderPage();

        await userEvent.type(screen.getByLabelText(/oral stock on hand/i), '50');
        await userEvent.click(screen.getByRole('button', { name: /generate stock runway/i }));

        await waitFor(() => {
            expect(screen.getByText(/doesn't run out within 10 years/i)).toBeInTheDocument();
        });
    });

    test('shows an error alert on API failure', async () => {
        api.getReportStockRunway.mockRejectedValue(new Error('Network error'));
        renderPage();

        await userEvent.type(screen.getByLabelText(/oral stock on hand/i), '50');
        await userEvent.click(screen.getByRole('button', { name: /generate stock runway/i }));

        await waitFor(() => {
            expect(screen.getByRole('alert')).toBeInTheDocument();
        });
    });
});
