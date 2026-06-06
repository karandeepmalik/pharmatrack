import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';

const todayStr = () => new Date().toISOString().slice(0, 10);
const weekAgoStr = () => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 10);
};

const specLabel = (type, spec) =>
    type === 'VIAL' ? `${Math.round(spec)} ml` : `${Math.round(spec)} mg (10 Tablets)`;

export default function ViewInventoryAdjustments() {
    const [from, setFrom]             = useState(weekAgoStr());
    const [to, setTo]                 = useState(todayStr());
    const [adjustments, setAdjustments] = useState([]);
    const [searched, setSearched]     = useState(false);
    const [loading, setLoading]       = useState(false);
    const [error, setError]           = useState('');

    // Client-side filters applied over fetched results
    const [userFilter, setUserFilter]       = useState('ALL');
    const [typeFilter, setTypeFilter]       = useState('ALL');

    // Per-row delete state: { [id]: { confirming, deleting, error } }
    const [deleteState, setDeleteState] = useState({});

    const isValid = Boolean(from) && Boolean(to) && from <= to;

    const handleSearch = async () => {
        if (!isValid) return;
        setLoading(true);
        setError('');
        setDeleteState({});
        try {
            const res = await api.getInventoryAdjustments(from, to);
            setAdjustments(res.data);
            setSearched(true);
            setUserFilter('ALL');
            setTypeFilter('ALL');
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
        .filter(a => typeFilter === 'ALL' || a.adjustmentType === typeFilter);

    // ── Delete flow ────────────────────────────────────────────────────

    const startDelete = (id) =>
        setDeleteState(prev => ({ ...prev, [id]: { confirming: true, deleting: false, error: '' } }));

    const cancelDelete = (id) =>
        setDeleteState(prev => ({ ...prev, [id]: { confirming: false, deleting: false, error: '' } }));

    const confirmDelete = async (id) => {
        setDeleteState(prev => ({ ...prev, [id]: { ...prev[id], deleting: true, error: '' } }));
        try {
            await api.deleteInventoryAdjustment(id);
            setAdjustments(prev => prev.filter(a => a.id !== id));
            setDeleteState(prev => { const n = { ...prev }; delete n[id]; return n; });
        } catch (err) {
            const msg = err.response?.data?.message || 'Failed to delete record.';
            setDeleteState(prev => ({ ...prev, [id]: { confirming: true, deleting: false, error: msg } }));
        }
    };

    return (
        <div className="page">
            <div className="page-header">
                <h1>View Stock Modifications</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            <div role="note" className="alert" style={{ background: '#fff8e1', borderColor: '#f9a825', marginBottom: '1rem' }}>
                Deleting a record reverses its inventory effect (ADD is undone; REDUCE is restored).
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
                                            <th>Note</th>
                                            <th>In Transit</th>
                                            <th>Adjusted By</th>
                                            <th>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {displayed.map(adj => {
                                            const del = deleteState[adj.id] || {};
                                            return (
                                                <tr key={adj.id}>
                                                    <td>{adj.adjustedAt || '—'}</td>
                                                    <td>{adj.username}</td>
                                                    <td>
                                                        {adj.medicineName}<br />
                                                        <small>{specLabel(adj.medicineType, adj.specification)}</small>
                                                    </td>
                                                    <td>{adj.quantity}</td>
                                                    <td>
                                                        <span className={`status-badge ${adj.adjustmentType === 'ADD' ? 'badge-approved' : 'badge-rejected'}`}>
                                                            {adj.adjustmentType}
                                                        </span>
                                                    </td>
                                                    <td>{adj.note || '—'}</td>
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
                                                                    Are you sure? This will reverse the inventory change.
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
                                                            <button
                                                                type="button"
                                                                className="btn btn-danger btn-sm"
                                                                onClick={() => startDelete(adj.id)}>
                                                                Delete
                                                            </button>
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
