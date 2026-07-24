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

const MEDICINES = [
    { id: 1, name: 'Shield FX Vial 10 ml', type: 'VIAL',   specification: 10, price: 4000 },
    { id: 2, name: 'Shield FX Tablet 25 mg (10 Tablets)', type: 'TABLET', specification: 25, price: 4000 },
];

beforeEach(() => {
    jest.clearAllMocks();
    api.getInventoryAdjustments.mockResolvedValue({ data: [] });
    api.deleteInventoryAdjustment.mockResolvedValue({});
    api.getMedicines.mockResolvedValue({ data: MEDICINES });
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

    test('shows Regular Stock badge for REGULAR_MEDICINE_STOCK records', () => {
        const table = screen.getByRole('table');
        expect(within(table).getAllByText('Regular Stock').length).toBe(2);
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

// ── Stock Type column ───────────────────────────────────────────────────────

describe('ViewInventoryAdjustments — stock type column', () => {
    const ADJ_ADMIN = {
        ...ADJ_ADD,
        id: 3,
        username: 'karan',
        note: 'Admin bucket restock',
        inventoryType: 'ADMIN_MEDICINE_STOCK',
    };

    test('shows Admin Stock badge for ADMIN_MEDICINE_STOCK records', async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_ADMIN] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));

        const table = screen.getByRole('table');
        expect(within(table).getByText('Regular Stock')).toBeInTheDocument();
        expect(within(table).getByText('Admin Stock')).toBeInTheDocument();
    });

    test('renders "Regular Stock" for a record with a null inventoryType (legacy fallback)', async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [{ ...ADJ_ADD, inventoryType: null }] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));

        expect(within(screen.getByRole('table')).getByText('Regular Stock')).toBeInTheDocument();
    });
});

// ── Medicine spec filter ─────────────────────────────────────────────────────

describe('ViewInventoryAdjustments — medicine spec filter', () => {
    const vial10Adj = { ...ADJ_ADD,    id: 1, medicineId: 1, note: 'Vial 10 note' };
    const tabletAdj = { ...ADJ_REDUCE, id: 2, medicineId: 2, note: 'Tablet note' };

    beforeEach(async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [vial10Adj, tabletAdj] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));
    });

    test('renders Medicine Spec filter dropdown after search', () => {
        expect(screen.getByLabelText(/medicine spec/i)).toBeInTheDocument();
    });

    test('medicine dropdown lists medicines fetched on mount', () => {
        const select = screen.getByLabelText(/medicine spec/i);
        expect(within(select).getByRole('option', { name: /vial 10 ml/i })).toBeInTheDocument();
        expect(within(select).getByRole('option', { name: /tablet 25 mg/i })).toBeInTheDocument();
    });

    test('filtering by a specific medicine hides other medicines\' records', async () => {
        await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '1');
        expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument();
        const table = screen.getByRole('table');
        expect(within(table).getByText('Vial 10 note')).toBeInTheDocument();
        expect(within(table).queryByText('Tablet note')).not.toBeInTheDocument();
    });

    test('resetting medicine filter to All Medicines restores all rows', async () => {
        await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '1');
        await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), 'ALL');
        expect(screen.getByText(/results \(2\)/i)).toBeInTheDocument();
    });

    test('medicine filter does not trigger a new API call', async () => {
        await userEvent.selectOptions(screen.getByLabelText(/medicine spec/i), '1');
        expect(api.getInventoryAdjustments).toHaveBeenCalledTimes(1);
    });
});

// ── Notes search ─────────────────────────────────────────────────────────

describe('ViewInventoryAdjustments — notes search', () => {
    const restockAdj = { ...ADJ_ADD,    id: 1, note: 'Restocking Ward 3 supply' };
    const expiredAdj = { ...ADJ_REDUCE, id: 2, note: 'Returned expired stock from clinic' };

    beforeEach(async () => {
        api.getInventoryAdjustments.mockResolvedValue({ data: [restockAdj, expiredAdj] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));
    });

    test('renders Search Notes text input after search', () => {
        expect(screen.getByLabelText(/search notes/i)).toBeInTheDocument();
    });

    test('empty search shows all records', () => {
        expect(screen.getByText(restockAdj.note)).toBeInTheDocument();
        expect(screen.getByText(expiredAdj.note)).toBeInTheDocument();
    });

    test('searching by note text filters to matching rows only', async () => {
        await userEvent.type(screen.getByLabelText(/search notes/i), 'ward');
        expect(screen.getByText(restockAdj.note)).toBeInTheDocument();
        expect(screen.queryByText(expiredAdj.note)).not.toBeInTheDocument();
    });

    test('note search is case-insensitive', async () => {
        await userEvent.type(screen.getByLabelText(/search notes/i), 'WARD');
        expect(screen.getByText(restockAdj.note)).toBeInTheDocument();
        expect(screen.queryByText(expiredAdj.note)).not.toBeInTheDocument();
    });

    test('note search matching no rows shows zero results', async () => {
        await userEvent.type(screen.getByLabelText(/search notes/i), 'nonexistent text');
        expect(screen.getByText(/results \(0\)/i)).toBeInTheDocument();
    });

    test('note search does not trigger a new API call', async () => {
        await userEvent.type(screen.getByLabelText(/search notes/i), 'ward');
        expect(api.getInventoryAdjustments).toHaveBeenCalledTimes(1);
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
