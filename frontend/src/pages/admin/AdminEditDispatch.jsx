import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import { medicineStockTypeLabel } from '../../constants';
import PaymentScreenshotViewer from '../../components/PaymentScreenshotViewer';

const todayStr = () => new Date().toISOString().slice(0, 10);
const weekAgoStr = () => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 10);
};

const specLabel = (type, spec) =>
    type === 'VIAL' ? `${spec} ml` : `${spec} mg (10 Tablets)`;

const medOptionLabel = (m) =>
    m.type === 'VIAL'
        ? `Vial ${m.specification} ml`
        : `Tablet ${m.specification} mg (10 Tablets)`;

export default function AdminEditDispatch() {
    const [from, setFrom]               = useState(weekAgoStr());
    const [to, setTo]                   = useState(todayStr());
    const [transactions, setTransactions] = useState([]);
    const [searched, setSearched]       = useState(false);
    const [loading, setLoading]         = useState(false);
    const [error, setError]             = useState('');

    const [users, setUsers]                       = useState([]);
    const [medicines, setMedicines]               = useState([]);
    const [userFilter, setUserFilter]             = useState('ALL');
    const [medicineFilter, setMedicineFilter]     = useState('ALL');
    const [stockTypeFilter, setStockTypeFilter]   = useState('ALL');
    const [notesSearch, setNotesSearch]           = useState('');

    // Inline edit state: { [id]: { active, notes, quantity, medicineStockType, pricePerUnit, date, screenshotFiles, saving, error } }
    const [editState, setEditState]     = useState({});
    // Inline delete state: { [id]: { confirming: bool, deleting: bool, error: string } }
    const [deleteState, setDeleteState] = useState({});

    const isValid = Boolean(from) && Boolean(to) && from <= to;

    useEffect(() => {
        api.getUsers().then(r => setUsers(
            (r.data || []).filter(u => u.role !== 'ADMIN')
        )).catch(() => {});
        api.getMedicines().then(r => setMedicines(r.data || [])).catch(() => {});
    }, []);

    // Any filter change invalidates the currently-displayed (already-searched) results — the
    // admin must press Search again for it to take effect, consistent with From/To.
    const invalidateSearch = () => setSearched(false);

    const handleSearch = async () => {
        if (!isValid) return;
        setLoading(true);
        setError('');
        setSearched(false);
        setEditState({});
        setDeleteState({});
        try {
            const username      = userFilter === 'ALL' ? undefined : userFilter;
            const medicineId    = medicineFilter === 'ALL' ? undefined : medicineFilter;
            const stockType     = stockTypeFilter === 'ALL' ? undefined : stockTypeFilter;
            const notes         = notesSearch.trim() ? notesSearch.trim() : undefined;
            const res = await api.getTransactionHistory(from, to, 'ALL', 0, 1000, username, medicineId, notes, stockType);
            setTransactions(res.data?.content ?? []);
            setSearched(true);
        } catch {
            setError('Failed to load records. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    // ── Edit dispatch record ─────────────────────────────────────────────

    const startEdit = (tx) => {
        setEditState(prev => ({
            ...prev,
            [tx.id]: {
                active: true,
                notes: tx.notes || '',
                quantity: String(tx.quantity ?? ''),
                medicineStockType: tx.medicineStockType || 'REGULAR_MEDICINE_STOCK',
                // Blank means "no override" — same as the underlying record — so leaving it
                // untouched keeps using the medicine's catalogue price, exactly as before.
                pricePerUnit: tx.pricePerUnit != null ? String(tx.pricePerUnit) : '',
                date: tx.submittedAt ? tx.submittedAt.slice(0, 10) : '',
                screenshotFiles: [],
                saving: false,
                error: '',
            },
        }));
    };

    const cancelEdit = (id) => {
        setEditState(prev => ({ ...prev, [id]: { ...prev[id], active: false } }));
    };

    const handleFieldChange = (id, field, value) => {
        setEditState(prev => ({ ...prev, [id]: { ...prev[id], [field]: value } }));
    };

    const saveEdit = async (id) => {
        const edit = editState[id] || {};
        setEditState(prev => ({ ...prev, [id]: { ...prev[id], saving: true, error: '' } }));
        try {
            const res = await api.updateTransaction(id, {
                notes: edit.notes ?? '',
                quantity: edit.quantity === '' ? null : edit.quantity,
                medicineStockType: edit.medicineStockType,
                pricePerUnit: edit.pricePerUnit === '' ? null : edit.pricePerUnit,
                submittedDate: edit.date === '' ? null : edit.date,
                screenshotFiles: edit.screenshotFiles,
            });
            setTransactions(prev => prev.map(tx => tx.id === id ? { ...tx, ...res.data } : tx));
            setEditState(prev => ({ ...prev, [id]: { active: false, saving: false, error: '' } }));
        } catch (err) {
            const msg = err.response?.data?.message || 'Failed to save changes.';
            setEditState(prev => ({ ...prev, [id]: { ...prev[id], saving: false, error: msg } }));
        }
    };

    // ── Delete ─────────────────────────────────────────────────────────

    const startDelete = (id) => {
        setDeleteState(prev => ({ ...prev, [id]: { confirming: true, deleting: false, error: '' } }));
    };

    const cancelDelete = (id) => {
        setDeleteState(prev => ({ ...prev, [id]: { confirming: false, deleting: false, error: '' } }));
    };

    const confirmDelete = async (id) => {
        setDeleteState(prev => ({ ...prev, [id]: { ...prev[id], deleting: true, error: '' } }));
        try {
            await api.deleteTransaction(id);
            setTransactions(prev => prev.filter(tx => tx.id !== id));
            setDeleteState(prev => {
                const next = { ...prev };
                delete next[id];
                return next;
            });
        } catch (err) {
            const msg = err.response?.data?.message || 'Failed to delete record.';
            setDeleteState(prev => ({ ...prev, [id]: { confirming: true, deleting: false, error: msg } }));
        }
    };

    const statusBadge = (s) => {
        const cls = s === 'APPROVED' ? 'badge-approved'
                  : s === 'REJECTED' ? 'badge-rejected'
                  : 'badge-pending';
        return <span className={`status-badge ${cls}`}>{s}</span>;
    };

    return (
        <div className="page">
            <div className="page-header">
                <h1>Modify or Delete a Medicine Dispatch Record</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error && <div role="alert" className="alert alert-error">{error}</div>}

            <div className="form-card">
                <div className="form-row">
                    <div className="form-group">
                        <label htmlFor="from-date">From Date</label>
                        <input
                            id="from-date"
                            type="date"
                            value={from}
                            onChange={e => { setFrom(e.target.value); setSearched(false); }}
                        />
                    </div>
                    <div className="form-group">
                        <label htmlFor="to-date">To Date</label>
                        <input
                            id="to-date"
                            type="date"
                            value={to}
                            onChange={e => { setTo(e.target.value); setSearched(false); }}
                        />
                    </div>
                </div>

                <div className="form-row">
                    <div className="form-group">
                        <label htmlFor="user-filter">User</label>
                        <select
                            id="user-filter"
                            value={userFilter}
                            onChange={e => { setUserFilter(e.target.value); invalidateSearch(); }}>
                            <option value="ALL">All Users</option>
                            {users.map(u => (
                                <option key={u.id} value={u.username}>
                                    {u.fullName} ({u.username})
                                </option>
                            ))}
                        </select>
                    </div>
                    <div className="form-group">
                        <label htmlFor="medicine-filter">Medicine Spec</label>
                        <select
                            id="medicine-filter"
                            value={medicineFilter}
                            onChange={e => { setMedicineFilter(e.target.value); invalidateSearch(); }}>
                            <option value="ALL">All Medicines</option>
                            {medicines.map(m => (
                                <option key={m.id} value={String(m.id)}>
                                    {medOptionLabel(m)}
                                </option>
                            ))}
                        </select>
                    </div>
                    <div className="form-group">
                        <label htmlFor="stock-type-filter">Stock Type</label>
                        <select
                            id="stock-type-filter"
                            value={stockTypeFilter}
                            onChange={e => { setStockTypeFilter(e.target.value); invalidateSearch(); }}>
                            <option value="ALL">All Stock Types</option>
                            <option value="REGULAR_MEDICINE_STOCK">Regular Medicine Stock</option>
                            <option value="ADMIN_MEDICINE_STOCK">Admin Medicine Stock</option>
                        </select>
                    </div>
                    <div className="form-group">
                        <label htmlFor="notes-search">Search Notes</label>
                        <input
                            id="notes-search"
                            type="text"
                            placeholder="Search by dispatch note…"
                            value={notesSearch}
                            onChange={e => { setNotesSearch(e.target.value); invalidateSearch(); }}
                        />
                    </div>
                </div>

                {from > to && (
                    <p className="form-error" role="alert">
                        "From" date must be before or equal to "To" date.
                    </p>
                )}

                <button
                    type="button"
                    className="btn btn-primary"
                    disabled={!isValid || loading}
                    onClick={handleSearch}>
                    {loading ? 'Searching…' : 'Search'}
                </button>
            </div>

            {searched && (
                transactions.length === 0 ? (
                    <p className="empty-state">No dispatch records found for the selected criteria.</p>
                ) : (
                    <div className="form-section" style={{ marginTop: '1.5rem' }}>
                        <h2>Results ({transactions.length})</h2>
                        <div className="table-wrapper">
                            <table className="data-table">
                                <thead>
                                    <tr>
                                        <th>Date</th>
                                        <th>User</th>
                                        <th>Medicine</th>
                                        <th>Qty</th>
                                        <th>Stock Type</th>
                                        <th>Price/Unit</th>
                                        <th>Status</th>
                                        <th>Notes</th>
                                        <th>Screenshot</th>
                                        <th>Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {transactions.map(tx => {
                                        const edit = editState[tx.id] || {};
                                        const del  = deleteState[tx.id] || {};
                                        return (
                                            <tr key={tx.id}>
                                                <td>
                                                    {edit.active ? (
                                                        <input
                                                            aria-label="Edit dispatch date"
                                                            type="date"
                                                            max={todayStr()}
                                                            value={edit.date}
                                                            onChange={e => handleFieldChange(tx.id, 'date', e.target.value)}
                                                        />
                                                    ) : (
                                                        tx.submittedAt
                                                            ? new Date(tx.submittedAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
                                                            : '—'
                                                    )}
                                                </td>
                                                <td>{tx.submittedByUsername}</td>
                                                <td>{tx.medicineName}<br /><small>{specLabel(tx.medicineType, tx.specification?.toFixed(0))}</small></td>
                                                <td>
                                                    {edit.active ? (
                                                        <input
                                                            aria-label="Edit quantity"
                                                            type="number"
                                                            min="0.1"
                                                            step="0.1"
                                                            value={edit.quantity}
                                                            onChange={e => handleFieldChange(tx.id, 'quantity', e.target.value)}
                                                            style={{ width: '5rem' }}
                                                        />
                                                    ) : (
                                                        Number(tx.quantity).toFixed(1)
                                                    )}
                                                </td>
                                                <td>
                                                    {edit.active ? (
                                                        <select
                                                            aria-label="Edit stock type"
                                                            value={edit.medicineStockType}
                                                            onChange={e => handleFieldChange(tx.id, 'medicineStockType', e.target.value)}>
                                                            <option value="REGULAR_MEDICINE_STOCK">Regular Medicine Stock</option>
                                                            <option value="ADMIN_MEDICINE_STOCK">Admin Medicine Stock</option>
                                                        </select>
                                                    ) : (
                                                        <span className={`status-badge ${tx.medicineStockType === 'ADMIN_MEDICINE_STOCK' ? 'badge-pending' : 'badge-approved'}`}>
                                                            {medicineStockTypeLabel(tx.medicineStockType)}
                                                        </span>
                                                    )}
                                                </td>
                                                <td>
                                                    {edit.active ? (
                                                        <input
                                                            aria-label="Edit price per unit"
                                                            type="number"
                                                            min="1"
                                                            step="1"
                                                            placeholder={`Default: Rs ${tx.price ?? '—'}`}
                                                            value={edit.pricePerUnit}
                                                            onChange={e => handleFieldChange(tx.id, 'pricePerUnit', e.target.value)}
                                                            style={{ width: '6rem' }}
                                                        />
                                                    ) : (
                                                        tx.pricePerUnit != null
                                                            ? `Rs ${tx.pricePerUnit.toLocaleString('en-IN')}`
                                                            : tx.price != null
                                                                ? `Rs ${tx.price.toLocaleString('en-IN')}`
                                                                : '—'
                                                    )}
                                                </td>
                                                <td>{statusBadge(tx.status)}</td>
                                                <td>
                                                    {edit.active ? (
                                                        <div>
                                                            <textarea
                                                                aria-label="Edit notes"
                                                                value={edit.notes}
                                                                onChange={e => handleFieldChange(tx.id, 'notes', e.target.value)}
                                                                rows={3}
                                                                style={{ width: '100%', resize: 'vertical' }}
                                                            />
                                                            {edit.error && (
                                                                <p role="alert" className="form-error">{edit.error}</p>
                                                            )}
                                                            <div className="btn-group" style={{ marginTop: '0.25rem' }}>
                                                                <button
                                                                    type="button"
                                                                    className="btn btn-primary btn-sm"
                                                                    disabled={edit.saving}
                                                                    onClick={() => saveEdit(tx.id)}>
                                                                    {edit.saving ? 'Saving…' : 'Save'}
                                                                </button>
                                                                <button
                                                                    type="button"
                                                                    className="btn btn-secondary btn-sm"
                                                                    disabled={edit.saving}
                                                                    onClick={() => cancelEdit(tx.id)}>
                                                                    Cancel
                                                                </button>
                                                            </div>
                                                        </div>
                                                    ) : (
                                                        tx.notes || '—'
                                                    )}
                                                </td>
                                                <td>
                                                    {edit.active ? (
                                                        <div>
                                                            <input
                                                                aria-label="Replace screenshots"
                                                                type="file"
                                                                accept="image/*"
                                                                multiple
                                                                onChange={e => handleFieldChange(tx.id, 'screenshotFiles', Array.from(e.target.files))}
                                                            />
                                                            <p style={{ fontSize: '0.75rem', margin: '0.25rem 0 0' }}>
                                                                Leave empty to keep existing screenshot(s).
                                                            </p>
                                                        </div>
                                                    ) : (
                                                        <PaymentScreenshotViewer
                                                            screenshots={tx.screenshots}
                                                            transactionId={tx.id}
                                                        />
                                                    )}
                                                </td>
                                                <td className="actions-cell">
                                                    {del.confirming ? (
                                                        <div>
                                                            <p style={{ fontSize: '0.85rem', margin: '0 0 0.25rem' }}>Are you sure?</p>
                                                            {del.error && (
                                                                <p role="alert" className="form-error">{del.error}</p>
                                                            )}
                                                            <div className="btn-group">
                                                                <button
                                                                    type="button"
                                                                    className="btn btn-danger btn-sm"
                                                                    disabled={del.deleting}
                                                                    onClick={() => confirmDelete(tx.id)}>
                                                                    {del.deleting ? 'Deleting…' : 'Confirm Delete'}
                                                                </button>
                                                                <button
                                                                    type="button"
                                                                    className="btn btn-secondary btn-sm"
                                                                    disabled={del.deleting}
                                                                    onClick={() => cancelDelete(tx.id)}>
                                                                    Cancel
                                                                </button>
                                                            </div>
                                                        </div>
                                                    ) : (
                                                        <div className="btn-group">
                                                            {!edit.active && (
                                                                <button
                                                                    type="button"
                                                                    className="btn btn-secondary btn-sm"
                                                                    onClick={() => startEdit(tx)}>
                                                                    Edit
                                                                </button>
                                                            )}
                                                            <button
                                                                type="button"
                                                                className="btn btn-danger btn-sm"
                                                                onClick={() => startDelete(tx.id)}>
                                                                Delete
                                                            </button>
                                                        </div>
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )
            )}
        </div>
    );
}
