import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import { medicineStockTypeLabel } from '../../constants';

const PAGE_SIZE = 10;

// Default date range: last 7 days
const todayStr = () => new Date().toISOString().slice(0, 10);
const weekAgoStr = () => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 10);
};

const STATUS_OPTIONS = [
    { value: 'ALL',      label: 'All' },
    { value: 'APPROVED', label: 'Approved' },
    { value: 'REJECTED', label: 'Rejected' },
];

const specLabel = (type, spec) =>
    type === 'VIAL' ? `${spec} ml` : `${spec} mg (10 Tablets)`;

const medOptionLabel = (m) =>
    m.type === 'VIAL'
        ? `Vial ${m.specification} ml`
        : `Tablet ${m.specification} mg (10 Tablets)`;

export default function ViewPastTransactions() {
    const [from, setFrom]             = useState(weekAgoStr());
    const [to, setTo]                 = useState(todayStr());
    const [status, setStatus]         = useState('APPROVED');
    const [transactions, setTransactions] = useState([]);
    const [totalCount, setTotalCount] = useState(0);
    const [hasMore, setHasMore]       = useState(false);
    const [searched, setSearched]     = useState(false);
    const [loading, setLoading]       = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);
    const [error, setError]           = useState('');

    const [users, setUsers]           = useState([]);
    const [medicines, setMedicines]   = useState([]);
    const [userFilter, setUserFilter] = useState('ALL');
    const [medicineFilter, setMedicineFilter] = useState('ALL');
    const [notesSearch, setNotesSearch] = useState('');

    const sentinelRef = useRef(null);
    const pageRef      = useRef(0);
    const loadingRef   = useRef(false);
    // The exact filters a page-0 search was run with — scroll-triggered loads of subsequent
    // pages must keep using these, not whatever the form currently shows (the admin may have
    // changed a filter without pressing Search again yet).
    const searchParamsRef = useRef({ from, to, status, userFilter, medicineFilter, notesSearch });

    useEffect(() => {
        api.getUsers().then(r => setUsers(
            (r.data || []).filter(u => u.role !== 'ADMIN')
        )).catch(() => {});
        api.getMedicines().then(r => setMedicines(r.data || [])).catch(() => {});
    }, []);

    const isValid = Boolean(from) && Boolean(to) && from <= to;

    const loadPage = useCallback((pg, params) => {
        if (loadingRef.current) return;
        loadingRef.current = true;
        if (pg === 0) {
            setLoading(true);
            setTransactions([]);
            setHasMore(false);
            setError('');
        } else {
            setLoadingMore(true);
        }

        const username   = params.userFilter === 'ALL' ? undefined : params.userFilter;
        const medicineId = params.medicineFilter === 'ALL' ? undefined : params.medicineFilter;
        const notes      = params.notesSearch.trim() ? params.notesSearch.trim() : undefined;

        api.getTransactionHistory(params.from, params.to, params.status, pg, PAGE_SIZE, username, medicineId, notes)
            .then((r) => {
                const content = r.data?.content;
                const last    = r.data?.last ?? true;
                const safe    = Array.isArray(content) ? content : [];
                setTransactions((prev) => pg === 0 ? safe : [...prev, ...safe]);
                setHasMore(!last);
                // totalElements reflects the full server-side matching set for the current
                // filters, not just what's been scroll-loaded — available immediately from
                // page 0's response, so the admin doesn't have to scroll to the end to find
                // out how many results a filter actually matched.
                setTotalCount(r.data?.totalElements ?? 0);
                pageRef.current = pg;
            })
            .catch(() => setError('Failed to load transactions. Please try again.'))
            .finally(() => {
                loadingRef.current = false;
                setLoading(false);
                setLoadingMore(false);
            });
    }, []);

    const handleSearch = () => {
        if (!isValid) return;
        const params = { from, to, status, userFilter, medicineFilter, notesSearch };
        searchParamsRef.current = params;
        pageRef.current = 0;
        setSearched(true);
        loadPage(0, params);
    };

    // Infinite-scroll sentinel — (re)attaches whenever the sentinel enters the DOM.
    // hasMore must be in deps: at mount/reset hasMore=false so the sentinel isn't
    // rendered yet and sentinelRef.current is null. The effect re-runs when hasMore
    // flips true and the sentinel is in the DOM.
    useEffect(() => {
        if (!hasMore) return;
        const el = sentinelRef.current;
        if (!el) return;
        const obs = new IntersectionObserver(([entry]) => {
            if (entry.isIntersecting && !loadingRef.current) {
                loadPage(pageRef.current + 1, searchParamsRef.current);
            }
        }, { threshold: 0 });
        obs.observe(el);
        return () => obs.disconnect();
    }, [hasMore, loadPage]);

    const statusBadge = (s) => {
        const cls = s === 'APPROVED' ? 'badge-approved'
                  : s === 'REJECTED' ? 'badge-rejected'
                  : 'badge-pending';
        return <span className={`status-badge ${cls}`}>{s}</span>;
    };

    // Any filter change invalidates the currently-displayed (already-searched) results — the
    // admin must press Search again for it to take effect, consistent with From/To/Status.
    // All filters run server-side against the full matching set (see api.getTransactionHistory),
    // not just whatever page has been scroll-loaded so far — a client-side-only filter would
    // silently miss real matches that hadn't been loaded yet once results span multiple pages.
    const invalidateSearch = () => setSearched(false);

    return (
        <div className="page">
            <div className="page-header">
                <h1>View Past Medicine Dispatches</h1>
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
                            onChange={e => { setFrom(e.target.value); invalidateSearch(); }}
                        />
                    </div>
                    <div className="form-group">
                        <label htmlFor="to-date">To Date</label>
                        <input
                            id="to-date"
                            type="date"
                            value={to}
                            onChange={e => { setTo(e.target.value); invalidateSearch(); }}
                        />
                    </div>
                    <div className="form-group">
                        <label htmlFor="status-filter">Status</label>
                        <select
                            id="status-filter"
                            value={status}
                            onChange={e => { setStatus(e.target.value); invalidateSearch(); }}>
                            {STATUS_OPTIONS.map(o => (
                                <option key={o.value} value={o.value}>{o.label}</option>
                            ))}
                        </select>
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
                loading ? (
                    <p className="loading">Searching…</p>
                ) : transactions.length === 0 ? (
                    <p className="empty-state">No transactions found for the selected criteria.</p>
                ) : (
                    <div className="form-section" style={{ marginTop: '1.5rem' }}>
                        <h2>Results ({totalCount})</h2>
                        {transactions.length < totalCount && (
                            <p className="page-subtitle">
                                Showing {transactions.length} of {totalCount} matching dispatches — scroll down to load more.
                            </p>
                        )}
                        <div className="table-wrapper">
                            <table className="data-table">
                                <thead>
                                    <tr>
                                        <th>Date</th>
                                        <th>User</th>
                                        <th>Medicine</th>
                                        <th>Spec</th>
                                        <th>Qty</th>
                                        <th>Stock Type</th>
                                        <th>Price/Unit</th>
                                        <th>Status</th>
                                        <th>Notes</th>
                                        <th>Approved By</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {transactions.map(tx => (
                                        <tr key={tx.id}>
                                            <td>{tx.submittedAt
                                                ? new Date(tx.submittedAt).toLocaleDateString('en-IN', { day:'2-digit', month:'short', year:'numeric' })
                                                : '—'}</td>
                                            <td>{tx.submittedByUsername}</td>
                                            <td>{tx.medicineName}</td>
                                            <td>{specLabel(tx.medicineType, tx.specification?.toFixed(0))}</td>
                                            <td>{Number(tx.quantity).toFixed(1)}</td>
                                            <td>
                                                <span className={`status-badge ${tx.medicineStockType === 'ADMIN_MEDICINE_STOCK' ? 'badge-pending' : 'badge-approved'}`}>
                                                    {medicineStockTypeLabel(tx.medicineStockType)}
                                                </span>
                                            </td>
                                            <td>{tx.pricePerUnit != null
                                                ? `Rs ${tx.pricePerUnit.toLocaleString('en-IN')}`
                                                : tx.price != null
                                                    ? `Rs ${tx.price.toLocaleString('en-IN')}`
                                                    : '—'}</td>
                                            <td>{statusBadge(tx.status)}</td>
                                            <td>{tx.notes || '—'}</td>
                                            <td>{tx.approvedByUsername || '—'}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        {hasMore && (
                            <div ref={sentinelRef} className="scroll-sentinel">
                                {loadingMore && <div className="loading">Loading more…</div>}
                            </div>
                        )}

                        {!hasMore && !loadingMore && (
                            <p className="all-loaded-msg" aria-live="polite">
                                All {transactions.length} transactions loaded.
                            </p>
                        )}
                    </div>
                )
            )}
        </div>
    );
}
