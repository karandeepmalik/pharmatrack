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
    type === 'VIAL' ? `${spec} ml` : `${spec} mg (10 Tablets)`;

export default function AdminEditDispatch() {
    const [from, setFrom]               = useState(weekAgoStr());
    const [to, setTo]                   = useState(todayStr());
    const [transactions, setTransactions] = useState([]);
    const [searched, setSearched]       = useState(false);
    const [loading, setLoading]         = useState(false);
    const [error, setError]             = useState('');

    // Inline edit state: { [id]: { active: bool, notes: string, saving: bool, error: string } }
    const [editState, setEditState]     = useState({});
    // Inline delete state: { [id]: { confirming: bool, deleting: bool, error: string } }
    const [deleteState, setDeleteState] = useState({});

    const isValid = Boolean(from) && Boolean(to) && from <= to;

    const handleSearch = async () => {
        if (!isValid) return;
        setLoading(true);
        setError('');
        setSearched(false);
        setEditState({});
        setDeleteState({});
        try {
            const res = await api.getTransactionHistory(from, to, 'ALL');
            setTransactions(res.data);
            setSearched(true);
        } catch {
            setError('Failed to load records. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    // ── Edit notes ─────────────────────────────────────────────────────

    const startEdit = (tx) => {
        setEditState(prev => ({
            ...prev,
            [tx.id]: { active: true, notes: tx.notes || '', saving: false, error: '' },
        }));
    };

    const cancelEdit = (id) => {
        setEditState(prev => ({ ...prev, [id]: { ...prev[id], active: false } }));
    };

    const handleNotesChange = (id, value) => {
        setEditState(prev => ({ ...prev, [id]: { ...prev[id], notes: value } }));
    };

    const saveNotes = async (id) => {
        const notes = editState[id]?.notes ?? '';
        setEditState(prev => ({ ...prev, [id]: { ...prev[id], saving: true, error: '' } }));
        try {
            const res = await api.updateTransaction(id, { notes });
            setTransactions(prev => prev.map(tx => tx.id === id ? { ...tx, notes: res.data.notes } : tx));
            setEditState(prev => ({ ...prev, [id]: { active: false, notes: res.data.notes, saving: false, error: '' } }));
        } catch (err) {
            const msg = err.response?.data?.message || 'Failed to save notes.';
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
                <div className="form-row" style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
                    <div className="form-group" style={{ flex: 1 }}>
                        <label htmlFor="from-date">From Date</label>
                        <input
                            id="from-date"
                            type="date"
                            value={from}
                            onChange={e => { setFrom(e.target.value); setSearched(false); }}
                        />
                    </div>
                    <div className="form-group" style={{ flex: 1 }}>
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
                    <p className="form-error" role="alert" style={{ color: 'var(--color-error, red)', marginBottom: '0.5rem' }}>
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
                    <p className="empty-state">No dispatch records found for the selected date range.</p>
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
                                        <th>Status</th>
                                        <th>Notes</th>
                                        <th>Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {transactions.map(tx => {
                                        const edit = editState[tx.id] || {};
                                        const del  = deleteState[tx.id] || {};
                                        return (
                                            <tr key={tx.id}>
                                                <td>{tx.submittedAt
                                                    ? new Date(tx.submittedAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
                                                    : '—'}</td>
                                                <td>{tx.submittedByUsername}</td>
                                                <td>{tx.medicineName}<br /><small>{specLabel(tx.medicineType, tx.specification?.toFixed(0))}</small></td>
                                                <td>{tx.quantity}</td>
                                                <td>{statusBadge(tx.status)}</td>
                                                <td style={{ minWidth: '200px' }}>
                                                    {edit.active ? (
                                                        <div>
                                                            <textarea
                                                                aria-label="Edit notes"
                                                                value={edit.notes}
                                                                onChange={e => handleNotesChange(tx.id, e.target.value)}
                                                                rows={3}
                                                                style={{ width: '100%', resize: 'vertical' }}
                                                            />
                                                            {edit.error && (
                                                                <p role="alert" style={{ color: 'var(--color-error, red)', fontSize: '0.85rem', margin: '0.25rem 0' }}>
                                                                    {edit.error}
                                                                </p>
                                                            )}
                                                            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.25rem' }}>
                                                                <button
                                                                    type="button"
                                                                    className="btn btn-primary btn-sm"
                                                                    disabled={edit.saving}
                                                                    onClick={() => saveNotes(tx.id)}>
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
                                                <td style={{ whiteSpace: 'nowrap' }}>
                                                    {del.confirming ? (
                                                        <div>
                                                            <p style={{ fontSize: '0.85rem', margin: '0 0 0.25rem' }}>Are you sure?</p>
                                                            {del.error && (
                                                                <p role="alert" style={{ color: 'var(--color-error, red)', fontSize: '0.85rem', margin: '0.25rem 0' }}>
                                                                    {del.error}
                                                                </p>
                                                            )}
                                                            <div style={{ display: 'flex', gap: '0.5rem' }}>
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
                                                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                                                            {!edit.active && (
                                                                <button
                                                                    type="button"
                                                                    className="btn btn-secondary btn-sm"
                                                                    onClick={() => startEdit(tx)}>
                                                                    Edit Notes
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
