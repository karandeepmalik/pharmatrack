import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ViewInventoryAdjustments from '../../../pages/admin/ViewInventoryAdjustments';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const ADJ_ADD = {
    id: 1,
    userId: 2,
    username: 'john.doe',
    userFullName: 'John Doe',
    medicineId: 1,
    medicineName: 'Shield FX Vial 10 ml',
    medicineType: 'VIAL',
    specification: 10,
    quantity: 8,
    adjustmentType: 'ADD',
    note: 'Restocking Ward 3 supply',
    adjustedAt: '06 Jun 2026, 10:00 AM',
    adjustedByUsername: 'admin',
    inTransit: true,
    transitDays: 3,
    internalMovement: false,
    inventoryType: 'REGULAR_MEDICINE_STOCK',
};

const ADJ_REDUCE = {
    id: 2,
    userId: 3,
    username: 'jane.smith',
    userFullName: 'Jane Smith',
    medicineId: 2,
    medicineName: 'Shield FX Tablet 25 mg',
    medicineType: 'TABLET',
    specification: 25,
    quantity: 5,
    adjustmentType: 'REDUCE',
    note: 'Returned expired stock from clinic',
    adjustedAt: '05 Jun 2026, 02:00 PM',
    adjustedByUsername: 'admin',
    inTransit: false,
    transitDays: 2,
    internalMovement: false,
    inventoryType: 'REGULAR_MEDICINE_STOCK',
};

const renderPage = () =>
    render(<MemoryRouter><ViewInventoryAdjustments /></MemoryRouter>);

beforeEach(() => {
    jest.clearAllMocks();
    api.getInventoryAdjustments.mockResolvedValue({ data: [] });
    api.deleteInventoryAdjustment.mockResolvedValue({});
});

// ── Render ────────────────────────────────────────────────────────────────

describe('ViewInventoryAdjustments — render', () => {
    test('renders page heading', () => {
        renderPage();
        expect(screen.getByRole('heading', { name: /medicine stock modifications history/i })).toBeInTheDocument();
    });

    test('renders Back link to admin dashboard', () => {
        renderPage();
        expect(screen.getByRole('link', { name: /← back/i }))
            .toHaveAttribute('href', '/admin/dashboard');
    });

    test('renders from and to date inputs with defaults', () => {
        renderPage();
        expect(screen.getByLabelText(/from date/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/to date/i)).toBeInTheDocument();
    });

    test('renders Search button', () => {
        renderPage();
        expect(screen.getByRole('button', { name: /search/i })).toBeInTheDocument();
    });

    test('renders deletion warning notice', () => {
        renderPage();
        expect(screen.getByRole('note')).toHaveTextContent(/reverses its inventory effect/i);
    });

    test('does not show results before search', () => {
        renderPage();
        expect(screen.queryByRole('table')).not.toBeInTheDocument();
        expect(screen.queryByText(/results/i)).not.toBeInTheDocument();
    });

    test('Search button is disabled when from > to', async () => {
        renderPage();
        const fromInput = screen.getByLabelText(/from date/i);
        const toInput   = screen.getByLabelText(/to date/i);
        await userEvent.clear(fromInput);
        await userEvent.type(fromInput, '2026-06-10');
        await userEvent.clear(toInput);
        await userEvent.type(toInput, '2026-06-01');
        expect(screen.getByRole('button', { name: /search/i })).toBeDisabled();
    });

    test('shows date validation error when from > to', async () => {
        renderPage();
        const fromInput = screen.getByLabelText(/from date/i);
        const toInput   = screen.getByLabelText(/to date/i);
        await userEvent.clear(fromInput);
        await userEvent.type(fromInput, '2026-06-10');
        await userEvent.clear(toInput);
        await userEvent.type(toInput, '2026-06-01');
        expect(screen.getByRole('alert')).toHaveTextContent(/"from" date must be before/i);
    });
});

// ── Search ────────────────────────────────────────────────────────────────

describe('ViewInventoryAdjustments — search', () => {
    test('calls getInventoryAdjustments with date range on search', async () => {
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        expect(api.getInventoryAdjustments).toHaveBeenCalledTimes(1);
    });

    test('shows empty-state message when no records found', async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() =>
            expect(screen.getByText(/no stock modifications found/i)).toBeInTheDocument()
        );
    });

    test('shows results table when records are returned', async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [ADJ_ADD] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    });

    test('shows correct result count', async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_REDUCE] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => expect(screen.getByText(/results \(2\)/i)).toBeInTheDocument());
    });

    test('shows error alert on API failure', async () => {
        api.getInventoryAdjustments.mockRejectedValue(new Error('network error'));
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() =>
            expect(screen.getByRole('alert')).toHaveTextContent(/failed to load/i)
        );
    });
});

// ── Table content ─────────────────────────────────────────────────────────

describe('ViewInventoryAdjustments — table content', () => {
    beforeEach(async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_REDUCE] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));
    });

    test('shows adjustment date', () => {
        expect(screen.getByText('06 Jun 2026, 10:00 AM')).toBeInTheDocument();
    });

    test('shows username', () => {
        const table = screen.getByRole('table');
        expect(within(table).getByText('john.doe')).toBeInTheDocument();
        expect(within(table).getByText('jane.smith')).toBeInTheDocument();
    });

    test('shows medicine name and spec label for vial', () => {
        expect(screen.getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
        expect(screen.getByText('10 ml')).toBeInTheDocument();
    });

    test('shows medicine name and spec label for tablet', () => {
        expect(screen.getByText('Shield FX Tablet 25 mg')).toBeInTheDocument();
        expect(screen.getByText('25 mg (10 Tablets)')).toBeInTheDocument();
    });

    test('shows quantity', () => {
        expect(screen.getByText('8.0')).toBeInTheDocument();
        expect(screen.getByText('5.0')).toBeInTheDocument();
    });

    test('shows ADD and REDUCE type badges', () => {
        const table = screen.getByRole('table');
        expect(within(table).getByText('ADD')).toBeInTheDocument();
        expect(within(table).getByText('REDUCE')).toBeInTheDocument();
    });

    test('shows note text', () => {
        expect(screen.getByText('Restocking Ward 3 supply')).toBeInTheDocument();
    });

    test('shows in-transit status with transit days for in-transit record', () => {
        expect(screen.getByText('Yes (3d)')).toBeInTheDocument();
    });

    test('shows No for non-in-transit record', () => {
        expect(screen.getByText('No')).toBeInTheDocument();
    });

    test('shows adjustedByUsername', () => {
        const cells = screen.getAllByText('admin');
        expect(cells.length).toBeGreaterThanOrEqual(1);
    });

    test('shows Delete button for each row', () => {
        const deleteBtns = screen.getAllByRole('button', { name: /^delete$/i });
        expect(deleteBtns).toHaveLength(2);
    });
});

// ── Filters ───────────────────────────────────────────────────────────────

describe('ViewInventoryAdjustments — filters', () => {
    beforeEach(async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_REDUCE] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));
    });

    test('shows User and Type dropdowns after search', () => {
        expect(screen.getByLabelText(/^user$/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/^type$/i)).toBeInTheDocument();
    });

    test('user dropdown lists unique usernames from results', () => {
        const select = screen.getByLabelText(/^user$/i);
        expect(within(select).getByRole('option', { name: 'john.doe' })).toBeInTheDocument();
        expect(within(select).getByRole('option', { name: 'jane.smith' })).toBeInTheDocument();
    });

    test('filtering by user shows only that user\'s records', async () => {
        await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');
        expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument();
        const table = screen.getByRole('table');
        expect(within(table).getByText('john.doe')).toBeInTheDocument();
        expect(within(table).queryByText('jane.smith')).not.toBeInTheDocument();
    });

    test('filtering by ADD type shows only ADD records', async () => {
        await userEvent.selectOptions(screen.getByLabelText(/^type$/i), 'ADD');
        expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument();
        const table = screen.getByRole('table');
        expect(within(table).getByText('ADD')).toBeInTheDocument();
        expect(within(table).queryByText('REDUCE')).not.toBeInTheDocument();
    });

    test('filtering by REDUCE type shows only REDUCE records', async () => {
        await userEvent.selectOptions(screen.getByLabelText(/^type$/i), 'REDUCE');
        expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument();
        const table = screen.getByRole('table');
        expect(within(table).getByText('REDUCE')).toBeInTheDocument();
        expect(within(table).queryByText('ADD')).not.toBeInTheDocument();
    });

    test('combined user and type filter narrows results', async () => {
        await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');
        await userEvent.selectOptions(screen.getByLabelText(/^type$/i), 'REDUCE');
        // john.doe only has ADD, so REDUCE filter produces 0
        expect(screen.getByText(/results \(0\)/i)).toBeInTheDocument();
    });

    test('resetting user filter back to All Users restores all rows', async () => {
        await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'john.doe');
        await userEvent.selectOptions(screen.getByLabelText(/^user$/i), 'ALL');
        expect(screen.getByText(/results \(2\)/i)).toBeInTheDocument();
    });
});

// ── Delete flow ───────────────────────────────────────────────────────────

describe('ViewInventoryAdjustments — delete flow', () => {
    beforeEach(async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_REDUCE] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));
    });

    test('clicking Delete shows confirmation prompt', async () => {
        const [firstDelete] = screen.getAllByRole('button', { name: /^delete$/i });
        await userEvent.click(firstDelete);
        expect(screen.getByText(/are you sure\?/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /confirm delete/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    });

    test('Cancel dismisses the confirmation prompt', async () => {
        const [firstDelete] = screen.getAllByRole('button', { name: /^delete$/i });
        await userEvent.click(firstDelete);
        await userEvent.click(screen.getByRole('button', { name: /cancel/i }));
        expect(screen.queryByText(/are you sure\?/i)).not.toBeInTheDocument();
    });

    test('Confirm Delete calls deleteInventoryAdjustment with correct id', async () => {
        const [firstDelete] = screen.getAllByRole('button', { name: /^delete$/i });
        await userEvent.click(firstDelete);
        await userEvent.click(screen.getByRole('button', { name: /confirm delete/i }));
        await waitFor(() => expect(api.deleteInventoryAdjustment).toHaveBeenCalledWith(ADJ_ADD.id));
    });

    test('deleted record is removed from the table', async () => {
        const [firstDelete] = screen.getAllByRole('button', { name: /^delete$/i });
        await userEvent.click(firstDelete);
        await userEvent.click(screen.getByRole('button', { name: /confirm delete/i }));
        await waitFor(() => expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument());
        expect(screen.queryByText('john.doe')).not.toBeInTheDocument();
    });

    test('shows inline error when delete API call fails', async () => {
        api.deleteInventoryAdjustment.mockRejectedValue({
            response: { data: { message: 'Record not found' } },
        });
        const [firstDelete] = screen.getAllByRole('button', { name: /^delete$/i });
        await userEvent.click(firstDelete);
        await userEvent.click(screen.getByRole('button', { name: /confirm delete/i }));
        await waitFor(() =>
            expect(screen.getByRole('alert')).toHaveTextContent(/record not found/i)
        );
    });
});
