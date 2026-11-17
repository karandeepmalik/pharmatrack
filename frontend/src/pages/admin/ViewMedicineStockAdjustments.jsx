import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import { medicineStockTypeLabel } from '../../constants';

const todayStr = () => new Date().toISOString().slice(0, 10);
const weekAgoStr = () => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 10);
};

const specLabel = (type, spec) =>
    type === 'VIAL' ? `${Math.round(spec)} ml` : `${Math.round(spec)} mg (10 Tablets)`;

const medOptionLabel = (m) =>
    m.type === 'VIAL'
        ? `Vial ${m.specification} ml`
        : `Tablet ${m.specification} mg (10 Tablets)`;

export default function ViewMedicineStockAdjustments() {
    const [from, setFrom]             = useState(weekAgoStr());
    const [to, setTo]                 = useState(todayStr());
    const [adjustments, setAdjustments] = useState([]);
    const [searched, setSearched]     = useState(false);
    const [loading, setLoading]       = useState(false);
    const [error, setError]           = useState('');

    const [medicines, setMedicines]   = useState([]);

    // Client-side filters applied over fetched results
    const [userFilter, setUserFilter]         = useState('ALL');
    const [typeFilter, setTypeFilter]         = useState('ALL');
    const [medicineFilter, setMedicineFilter] = useState('ALL');
    const [notesSearch, setNotesSearch]       = useState('');

    // Per-row delete state: { [id]: { confirming, deleting, error } }
    const [deleteState, setDeleteState] = useState({});
    // Per-row edit state: { [id]: { active, note, quantity, adjustmentType, medicineStockType, date, saving, error } }
    const [editState, setEditState]     = useState({});

    useEffect(() => {
        api.getMedicines().then(r => setMedicines(r.data || [])).catch(() => {});
    }, []);

    const isValid = Boolean(from) && Boolean(to) && from <= to;

    const handleSearch = async () => {
        if (!isValid) return;
        setLoading(true);
        setError('');
        setDeleteState({});
        setEditState({});
        try {
            const res = await api.getMedicineStockAdjustments(from, to);
            setAdjustments(res.data);
            setSearched(true);
            setUserFilter('ALL');
            setTypeFilter('ALL');
            setMedicineFilter('ALL');
            setNotesSearch('');
        } catch {
            setError('Failed to load adjustment records. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    // Unique usernames for the dropdown (derived from loaded data)
    const uniqueUsers = [...new Set(adjustments.map(a => a.username))].sort();

    const displayed = adjustments
        .filter(a => userFilter === 'ALL' || a.username === userFilter)
        .filter(a => typeFilter === 'ALL' || a.adjustmentType === typeFilter)
        .filter(a => medicineFilter === 'ALL' || String(a.medicineId) === medicineFilter)
        .filter(a => !notesSearch.trim() ||
            (a.note || '').toLowerCase().includes(notesSearch.trim().toLowerCase()));

    // ── Delete flow ────────────────────────────────────────────────────

    const startDelete = (id) =>
        setDeleteState(prev => ({ ...prev, [id]: { confirming: true, deleting: false, error: '' } }));

    const cancelDelete = (id) =>
        setDeleteState(prev => ({ ...prev, [id]: { confirming: false, deleting: false, error: '' } }));

    const confirmDelete = async (id) => {
        setDeleteState(prev => ({ ...prev, [id]: { ...prev[id], deleting: true, error: '' } }));
        try {
            await api.deleteMedicineStockAdjustment(id);
            setAdjustments(prev => prev.filter(a => a.id !== id));
            setDeleteState(prev => { const n = { ...prev }; delete n[id]; return n; });
        } catch (err) {
            const msg = err.response?.data?.message || 'Failed to delete record.';
            setDeleteState(prev => ({ ...prev, [id]: { confirming: true, deleting: false, error: msg } }));
        }
    };

    // ── Edit flow ──────────────────────────────────────────────────────

    const startEdit = (adj) => {
        setEditState(prev => ({
            ...prev,
            [adj.id]: {
                active: true,
                note: adj.note || '',
                quantity: String(adj.quantity ?? ''),
                adjustmentType: adj.adjustmentType || 'ADD',
                medicineStockType: adj.medicineStockType || 'REGULAR_MEDICINE_STOCK',
                date: adj.adjustedAtDate || '',
                saving: false,
                error: '',
            },
        }));
    };

    const cancelEdit = (id) =>
        setEditState(prev => ({ ...prev, [id]: { ...prev[id], active: false } }));

    const handleEditFieldChange = (id, field, value) =>
        setEditState(prev => ({ ...prev, [id]: { ...prev[id], [field]: value } }));

    const saveEdit = async (id) => {
        const edit = editState[id] || {};
        setEditState(prev => ({ ...prev, [id]: { ...prev[id], saving: true, error: '' } }));
        try {
            const res = await api.updateMedicineStockAdjustment(id, {
                note: edit.note,
                quantity: edit.quantity === '' ? null : edit.quantity,
                adjustmentType: edit.adjustmentType,
                medicineStockType: edit.medicineStockType,
                adjustmentDate: edit.date === '' ? null : edit.date,
            });
            setAdjustments(prev => prev.map(a => a.id === id ? { ...a, ...res.data } : a));
            setEditState(prev => ({ ...prev, [id]: { active: false, saving: false, error: '' } }));
        } catch (err) {
            const msg = err.response?.data?.message || 'Failed to save changes.';
            setEditState(prev => ({ ...prev, [id]: { ...prev[id], saving: false, error: msg } }));
        }
    };

    return (
        <div className="page">
            <div className="page-header">
                <h1>Medicine Stock Modifications History</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            <div role="note" className="alert" style={{ background: '#fff8e1', borderColor: '#f9a825', marginBottom: '1rem' }}>
                Deleting a record reverses its medicineStock effect (ADD is undone; REDUCE is restored).
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
                <>
                    {adjustments.length === 0 ? (
                        <p className="empty-state">No stock modifications found for the selected date range.</p>
                    ) : (
                        <div className="form-section" style={{ marginTop: '1.5rem' }}>
                            <div className="form-row" style={{ marginBottom: '1rem' }}>
                                <div className="form-group">
                                    <label htmlFor="user-filter">User</label>
                                    <select
                                        id="user-filter"
                                        value={userFilter}
                                        onChange={e => setUserFilter(e.target.value)}>
                                        <option value="ALL">All Users</option>
                                        {uniqueUsers.map(u => (
                                            <option key={u} value={u}>{u}</option>
                                        ))}
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label htmlFor="type-filter">Type</label>
                                    <select
                                        id="type-filter"
                                        value={typeFilter}
                                        onChange={e => setTypeFilter(e.target.value)}>
                                        <option value="ALL">All Types</option>
                                        <option value="ADD">ADD</option>
                                        <option value="REDUCE">REDUCE</option>
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label htmlFor="medicine-filter">Medicine Spec</label>
                                    <select
                                        id="medicine-filter"
                                        value={medicineFilter}
                                        onChange={e => setMedicineFilter(e.target.value)}>
                                        <option value="ALL">All Medicines</option>
                                        {medicines.map(m => (
                                            <option key={m.id} value={String(m.id)}>
                                                {medOptionLabel(m)}
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label htmlFor="notes-search">Search Notes</label>
                                    <input
                                        id="notes-search"
                                        type="text"
                                        placeholder="Search by note…"
                                        value={notesSearch}
                                        onChange={e => setNotesSearch(e.target.value)}
                                    />
                                </div>
                            </div>

                            <h2>Results ({displayed.length})</h2>
                            <div className="table-wrapper">
                                <table className="data-table">
                                    <thead>
                                        <tr>
                                            <th>Date</th>
                                            <th>User</th>
                                            <th>Medicine Spec</th>
                                            <th>Qty</th>
                                            <th>Type</th>
                                            <th>Stock Type</th>
                                            <th>Note</th>
                                            <th>In Transit</th>
                                            <th>Adjusted By</th>
                                            <th>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {displayed.map(adj => {
                                            const del = deleteState[adj.id] || {};
                                            const edit = editState[adj.id] || {};
                                            return (
                                                <tr key={adj.id}>
                                                    <td>
                                                        {edit.active ? (
                                                            <input
                                                                aria-label="Edit adjustment date"
                                                                type="date"
                                                                max={todayStr()}
                                                                value={edit.date}
                                                                onChange={e => handleEditFieldChange(adj.id, 'date', e.target.value)}
                                                            />
                                                        ) : (
                                                            adj.adjustedAt || '—'
                                                        )}
                                                    </td>
                                                    <td>{adj.username}</td>
                                                    <td>
                                                        {adj.medicineName}<br />
                                                        <small>{specLabel(adj.medicineType, adj.specification)}</small>
                                                    </td>
                                                    <td>
                                                        {edit.active ? (
                                                            <input
                                                                aria-label="Edit quantity"
                                                                type="number"
                                                                min="0.1"
                                                                step="0.1"
                                                                value={edit.quantity}
                                                                onChange={e => handleEditFieldChange(adj.id, 'quantity', e.target.value)}
                                                                style={{ width: '5rem' }}
                                                            />
                                                        ) : (
                                                            Number(adj.quantity).toFixed(1)
                                                        )}
                                                    </td>
                                                    <td>
                                                        {edit.active ? (
                                                            <select
                                                                aria-label="Edit adjustment type"
                                                                value={edit.adjustmentType}
                                                                onChange={e => handleEditFieldChange(adj.id, 'adjustmentType', e.target.value)}>
                                                                <option value="ADD">ADD</option>
                                                                <option value="REDUCE">REDUCE</option>
                                                            </select>
                                                        ) : (
                                                            <span className={`status-badge ${adj.adjustmentType === 'ADD' ? 'badge-approved' : 'badge-rejected'}`}>
                                                                {adj.adjustmentType}
                                                            </span>
                                                        )}
                                                    </td>
                                                    <td>
                                                        {edit.active ? (
                                                            <select
                                                                aria-label="Edit stock type"
                                                                value={edit.medicineStockType}
                                                                onChange={e => handleEditFieldChange(adj.id, 'medicineStockType', e.target.value)}>
                                                                <option value="REGULAR_MEDICINE_STOCK">Regular Medicine Stock</option>
                                                                <option value="ADMIN_MEDICINE_STOCK">Admin Medicine Stock</option>
                                                            </select>
                                                        ) : (
                                                            <span className={`status-badge ${adj.medicineStockType === 'ADMIN_MEDICINE_STOCK' ? 'badge-pending' : 'badge-approved'}`}>
                                                                {medicineStockTypeLabel(adj.medicineStockType)}
                                                            </span>
                                                        )}
                                                    </td>
                                                    <td>
                                                        {edit.active ? (
                                                            <div>
                                                                <textarea
                                                                    aria-label="Edit note"
                                                                    value={edit.note}
                                                                    onChange={e => handleEditFieldChange(adj.id, 'note', e.target.value)}
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
                                                                        onClick={() => saveEdit(adj.id)}>
                                                                        {edit.saving ? 'Saving…' : 'Save'}
                                                                    </button>
                                                                    <button
                                                                        type="button"
                                                                        className="btn btn-secondary btn-sm"
                                                                        disabled={edit.saving}
                                                                        onClick={() => cancelEdit(adj.id)}>
                                                                        Cancel
                                                                    </button>
                                                                </div>
                                                            </div>
                                                        ) : (
                                                            adj.note || '—'
                                                        )}
                                                    </td>
                                                    <td>
                                                        {adj.inTransit
                                                            ? `Yes (${adj.transitDays}d)`
                                                            : 'No'}
                                                    </td>
                                                    <td>{adj.adjustedByUsername || '—'}</td>
                                                    <td className="actions-cell">
                                                        {del.confirming ? (
                                                            <div>
                                                                <p style={{ fontSize: '0.85rem', margin: '0 0 0.25rem' }}>
                                                                    Are you sure? This will reverse the medicineStock change.
                                                                </p>
                                                                {del.error && (
                                                                    <p role="alert" className="form-error">{del.error}</p>
                                                                )}
                                                                <div className="btn-group">
                                                                    <button
                                                                        type="button"
                                                                        className="btn btn-danger btn-sm"
                                                                        disabled={del.deleting}
                                                                        onClick={() => confirmDelete(adj.id)}>
                                                                        {del.deleting ? 'Deleting…' : 'Confirm Delete'}
                                                                    </button>
                                                                    <button
                                                                        type="button"
                                                                        className="btn btn-secondary btn-sm"
                                                                        disabled={del.deleting}
                                                                        onClick={() => cancelDelete(adj.id)}>
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
                                                                        onClick={() => startEdit(adj)}>
                                                                        Edit
                                                                    </button>
                                                                )}
                                                                <button
                                                                    type="button"
                                                                    className="btn btn-danger btn-sm"
                                                                    onClick={() => startDelete(adj.id)}>
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
                    )}
                </>
            )}
        </div>
    );
}
