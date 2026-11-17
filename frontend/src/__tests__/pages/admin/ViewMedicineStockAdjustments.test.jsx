import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ViewMedicineStockAdjustments from '../../../pages/admin/ViewMedicineStockAdjustments';
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
    adjustedAtDate: '2026-06-06',
    adjustedByUsername: 'admin',
    inTransit: true,
    transitDays: 3,
    internalMovement: false,
    medicineStockType: 'REGULAR_MEDICINE_STOCK',
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
    adjustedAtDate: '2026-06-05',
    adjustedByUsername: 'admin',
    inTransit: false,
    transitDays: 2,
    internalMovement: false,
    medicineStockType: 'REGULAR_MEDICINE_STOCK',
};

const renderPage = () =>
    render(<MemoryRouter><ViewMedicineStockAdjustments /></MemoryRouter>);

const MEDICINES = [
    { id: 1, name: 'Shield FX Vial 10 ml', type: 'VIAL',   specification: 10, price: 4000 },
    { id: 2, name: 'Shield FX Tablet 25 mg (10 Tablets)', type: 'TABLET', specification: 25, price: 4000 },
];

beforeEach(() => {
    jest.clearAllMocks();
    api.getMedicineStockAdjustments.mockResolvedValue({ data: [] });
    api.deleteMedicineStockAdjustment.mockResolvedValue({});
    api.updateMedicineStockAdjustment.mockResolvedValue({ data: {} });
    api.getMedicines.mockResolvedValue({ data: MEDICINES });
});

// ── Render ────────────────────────────────────────────────────────────────

describe('ViewMedicineStockAdjustments — render', () => {
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
        expect(screen.getByRole('note')).toHaveTextContent(/reverses its medicineStock effect/i);
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

describe('ViewMedicineStockAdjustments — search', () => {
    test('calls getMedicineStockAdjustments with date range on search', async () => {
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        expect(api.getMedicineStockAdjustments).toHaveBeenCalledTimes(1);
    });

    test('shows empty-state message when no records found', async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() =>
            expect(screen.getByText(/no stock modifications found/i)).toBeInTheDocument()
        );
    });

    test('shows results table when records are returned', async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [ADJ_ADD] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    });

    test('shows correct result count', async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_REDUCE] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => expect(screen.getByText(/results \(2\)/i)).toBeInTheDocument());
    });

    test('shows error alert on API failure', async () => {
        api.getMedicineStockAdjustments.mockRejectedValue(new Error('network error'));
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() =>
            expect(screen.getByRole('alert')).toHaveTextContent(/failed to load/i)
        );
    });
});

// ── Table content ─────────────────────────────────────────────────────────

describe('ViewMedicineStockAdjustments — table content', () => {
    beforeEach(async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_REDUCE] });
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

    test('shows Edit button for each row', () => {
        const editBtns = screen.getAllByRole('button', { name: /^edit$/i });
        expect(editBtns).toHaveLength(2);
    });
});

// ── Filters ───────────────────────────────────────────────────────────────

describe('ViewMedicineStockAdjustments — filters', () => {
    beforeEach(async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_REDUCE] });
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

describe('ViewMedicineStockAdjustments — stock type column', () => {
    const ADJ_ADMIN = {
        ...ADJ_ADD,
        id: 3,
        username: 'karan',
        note: 'Admin bucket restock',
        medicineStockType: 'ADMIN_MEDICINE_STOCK',
    };

    test('shows Admin Stock badge for ADMIN_MEDICINE_STOCK records', async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_ADMIN] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));

        const table = screen.getByRole('table');
        expect(within(table).getByText('Regular Stock')).toBeInTheDocument();
        expect(within(table).getByText('Admin Stock')).toBeInTheDocument();
    });

    test('renders "Regular Stock" for a record with a null medicineStockType (legacy fallback)', async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [{ ...ADJ_ADD, medicineStockType: null }] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));

        expect(within(screen.getByRole('table')).getByText('Regular Stock')).toBeInTheDocument();
    });
});

// ── Medicine spec filter ─────────────────────────────────────────────────────

describe('ViewMedicineStockAdjustments — medicine spec filter', () => {
    const vial10Adj = { ...ADJ_ADD,    id: 1, medicineId: 1, note: 'Vial 10 note' };
    const tabletAdj = { ...ADJ_REDUCE, id: 2, medicineId: 2, note: 'Tablet note' };

    beforeEach(async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [vial10Adj, tabletAdj] });
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
        expect(api.getMedicineStockAdjustments).toHaveBeenCalledTimes(1);
    });
});

// ── Notes search ─────────────────────────────────────────────────────────

describe('ViewMedicineStockAdjustments — notes search', () => {
    const restockAdj = { ...ADJ_ADD,    id: 1, note: 'Restocking Ward 3 supply' };
    const expiredAdj = { ...ADJ_REDUCE, id: 2, note: 'Returned expired stock from clinic' };

    beforeEach(async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [restockAdj, expiredAdj] });
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
        expect(api.getMedicineStockAdjustments).toHaveBeenCalledTimes(1);
    });
});

// ── Delete flow ───────────────────────────────────────────────────────────

describe('ViewMedicineStockAdjustments — delete flow', () => {
    beforeEach(async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_REDUCE] });
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

    test('Confirm Delete calls deleteMedicineStockAdjustment with correct id', async () => {
        const [firstDelete] = screen.getAllByRole('button', { name: /^delete$/i });
        await userEvent.click(firstDelete);
        await userEvent.click(screen.getByRole('button', { name: /confirm delete/i }));
        await waitFor(() => expect(api.deleteMedicineStockAdjustment).toHaveBeenCalledWith(ADJ_ADD.id));
    });

    test('deleted record is removed from the table', async () => {
        const [firstDelete] = screen.getAllByRole('button', { name: /^delete$/i });
        await userEvent.click(firstDelete);
        await userEvent.click(screen.getByRole('button', { name: /confirm delete/i }));
        await waitFor(() => expect(screen.getByText(/results \(1\)/i)).toBeInTheDocument());
        expect(screen.queryByText('john.doe')).not.toBeInTheDocument();
    });

    test('shows inline error when delete API call fails', async () => {
        api.deleteMedicineStockAdjustment.mockRejectedValue({
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

// ── Edit flow ─────────────────────────────────────────────────────────────

describe('ViewMedicineStockAdjustments — edit flow', () => {
    beforeEach(async () => {
        api.getMedicineStockAdjustments.mockResolvedValue({ data: [ADJ_ADD, ADJ_REDUCE] });
        renderPage();
        await userEvent.click(screen.getByRole('button', { name: /search/i }));
        await waitFor(() => screen.getByRole('table'));
    });

    test('clicking Edit shows editable note, quantity, type, stock type and date fields prefilled', async () => {
        const [firstEdit] = screen.getAllByRole('button', { name: /^edit$/i });
        await userEvent.click(firstEdit);

        expect(screen.getByRole('textbox', { name: /edit note/i })).toHaveValue(ADJ_ADD.note);
        expect(screen.getByRole('spinbutton', { name: /edit quantity/i })).toHaveValue(8);
        expect(screen.getByRole('combobox', { name: /edit adjustment type/i })).toHaveValue('ADD');
        expect(screen.getByRole('combobox', { name: /edit stock type/i })).toHaveValue('REGULAR_MEDICINE_STOCK');
        expect(screen.getByLabelText(/edit adjustment date/i)).toHaveValue('2026-06-06');
    });

    test('clicking Cancel hides the edit form and restores the original note display', async () => {
        const [firstEdit] = screen.getAllByRole('button', { name: /^edit$/i });
        await userEvent.click(firstEdit);
        await userEvent.click(screen.getByRole('button', { name: /cancel/i }));

        expect(screen.queryByRole('textbox', { name: /edit note/i })).not.toBeInTheDocument();
        expect(screen.getByText(ADJ_ADD.note)).toBeInTheDocument();
    });

    test('saving an edit calls updateMedicineStockAdjustment with the new values', async () => {
        const [firstEdit] = screen.getAllByRole('button', { name: /^edit$/i });
        await userEvent.click(firstEdit);

        const qtyInput = screen.getByRole('spinbutton', { name: /edit quantity/i });
        await userEvent.clear(qtyInput);
        await userEvent.type(qtyInput, '12');
        const noteInput = screen.getByRole('textbox', { name: /edit note/i });
        await userEvent.clear(noteInput);
        await userEvent.type(noteInput, 'Corrected after a recount');
        await userEvent.click(screen.getByRole('button', { name: /save/i }));

        await waitFor(() =>
            expect(api.updateMedicineStockAdjustment).toHaveBeenCalledWith(ADJ_ADD.id, {
                note: 'Corrected after a recount',
                quantity: '12',
                adjustmentType: 'ADD',
                medicineStockType: 'REGULAR_MEDICINE_STOCK',
                adjustmentDate: '2026-06-06',
            })
        );
    });

    test('changing adjustmentType and stock type sends both through to the service', async () => {
        const [firstEdit] = screen.getAllByRole('button', { name: /^edit$/i });
        await userEvent.click(firstEdit);

        await userEvent.selectOptions(screen.getByRole('combobox', { name: /edit adjustment type/i }), 'REDUCE');
        await userEvent.selectOptions(screen.getByRole('combobox', { name: /edit stock type/i }), 'ADMIN_MEDICINE_STOCK');
        const noteInput = screen.getByRole('textbox', { name: /edit note/i });
        await userEvent.clear(noteInput);
        await userEvent.type(noteInput, 'This was actually an admin-stock reduction');
        await userEvent.click(screen.getByRole('button', { name: /save/i }));

        await waitFor(() =>
            expect(api.updateMedicineStockAdjustment).toHaveBeenCalledWith(ADJ_ADD.id, expect.objectContaining({
                adjustmentType: 'REDUCE',
                medicineStockType: 'ADMIN_MEDICINE_STOCK',
            }))
        );
    });

    test('row reflects the updated values returned by the API after saving', async () => {
        api.updateMedicineStockAdjustment.mockResolvedValue({
            data: { ...ADJ_ADD, note: 'Corrected after a recount', quantity: 12 },
        });
        const [firstEdit] = screen.getAllByRole('button', { name: /^edit$/i });
        await userEvent.click(firstEdit);
        const noteInput = screen.getByRole('textbox', { name: /edit note/i });
        await userEvent.clear(noteInput);
        await userEvent.type(noteInput, 'Corrected after a recount');
        await userEvent.click(screen.getByRole('button', { name: /save/i }));

        await waitFor(() => expect(screen.getByText('Corrected after a recount')).toBeInTheDocument());
        expect(screen.getByText('12.0')).toBeInTheDocument();
    });

    test('shows inline error when save fails', async () => {
        api.updateMedicineStockAdjustment.mockRejectedValue({
            response: { data: { message: 'Note must be between 5 and 500 characters' } },
        });
        const [firstEdit] = screen.getAllByRole('button', { name: /^edit$/i });
        await userEvent.click(firstEdit);
        await userEvent.click(screen.getByRole('button', { name: /save/i }));

        await waitFor(() =>
            expect(screen.getByRole('alert')).toHaveTextContent(/note must be between 5 and 500 characters/i)
        );
        // Edit mode stays active so the admin can correct and retry.
        expect(screen.getByRole('textbox', { name: /edit note/i })).toBeInTheDocument();
    });

    test('only one row enters edit mode at a time per button clicked', async () => {
        const [firstEdit, secondEdit] = screen.getAllByRole('button', { name: /^edit$/i });
        await userEvent.click(firstEdit);
        await userEvent.click(secondEdit);

        expect(screen.getAllByRole('textbox', { name: /edit note/i })).toHaveLength(2);
    });
});
