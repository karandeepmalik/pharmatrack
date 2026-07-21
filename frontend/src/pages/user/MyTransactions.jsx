import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import PaymentScreenshotViewer from '../../components/PaymentScreenshotViewer';
import { inventoryTypeLabel } from '../../constants';

const PAGE_SIZE = 20;

const specLabel = (tx) => tx.medicineType === 'VIAL'
  ? `${tx.concentrationMgPerMl ?? tx.specification ?? '?'} mg/ml`
  : `${tx.specification ?? '?'} mg`;

export default function MyTransactions() {
  const [allTxs, setAllTxs]         = useState([]);
  const [hasMore, setHasMore]       = useState(false);
  const [filter, setFilter]         = useState('ALL');
  const [specFilter, setSpecFilter] = useState('ALL');
  const [notesSearch, setNotesSearch] = useState('');
  const [loading, setLoading]       = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError]           = useState(null);
  const [deletingId, setDeletingId] = useState(null);

  const sentinelRef = useRef(null);
  const pageRef     = useRef(0);
  const loadingRef  = useRef(false);

  const loadPage = useCallback((pg) => {
    if (loadingRef.current) return;
    loadingRef.current = true;
    if (pg === 0) {
      setLoading(true);
      setAllTxs([]);
      setHasMore(false);
    } else {
      setLoadingMore(true);
    }

    api.getMyTransactions(pg, PAGE_SIZE)
      .then((r) => {
        const content = r.data?.content;
        const last    = r.data?.last ?? true;
        const safe    = Array.isArray(content) ? content : [];
        setAllTxs((prev) => pg === 0 ? safe : [...prev, ...safe]);
        setHasMore(!last);
        pageRef.current = pg;
      })
      .catch(() => setError('Failed to load transactions. Please try again.'))
      .finally(() => {
        loadingRef.current = false;
        setLoading(false);
        setLoadingMore(false);
      });
  }, []);

  useEffect(() => { loadPage(0); }, [loadPage]);

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this dispatch record? This cannot be undone.')) return;
    setError(null);
    setDeletingId(id);
    try {
      await api.deleteMyTransaction(id);
      setAllTxs((prev) => prev.filter((t) => t.id !== id));
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete dispatch record. Please try again.');
    } finally {
      setDeletingId(null);
    }
  };

  // Infinite-scroll sentinel
  useEffect(() => {
    const el = sentinelRef.current;
    if (!el) return;
    const obs = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting && !loadingRef.current) {
        loadPage(pageRef.current + 1);
      }
    }, { threshold: 0 });
    obs.observe(el);
    return () => obs.disconnect();
  }, [loadPage]);

  // Unique medicine specs present in the loaded dispatches, for the spec filter dropdown
  const specOptions = [...new Map(
    allTxs.map((t) => [t.medicineId, { medicineId: t.medicineId, label: `${t.medicineName ?? 'Unknown'} — ${specLabel(t)}` }])
  ).values()].sort((a, b) => a.label.localeCompare(b.label));

  const filtered = allTxs
    .filter((t) => filter === 'ALL' || t.status === filter)
    .filter((t) => specFilter === 'ALL' || String(t.medicineId) === specFilter)
    .filter((t) => !notesSearch.trim() ||
      (t.notes || '').toLowerCase().includes(notesSearch.trim().toLowerCase()));

  if (loading) return <div className="loading">Loading…</div>;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Medicine Dispatch History</h1>
        <Link to="/user/dashboard" className="btn btn-secondary">← Back</Link>
      </div>

      {error && <div role="alert" className="alert alert-error">{error}</div>}

      <div className="filter-tabs" role="group">
        {['ALL', 'PENDING', 'APPROVED', 'REJECTED'].map((s) => (
          <button key={s} type="button"
            className={`filter-tab ${filter === s ? 'active' : ''}`}
            onClick={() => setFilter(s)}>
            {s}
          </button>
        ))}
      </div>

      <div className="form-row">
        <div className="form-group">
          <label htmlFor="spec-filter">Medicine Spec</label>
          <select
            id="spec-filter"
            value={specFilter}
            onChange={(e) => setSpecFilter(e.target.value)}>
            <option value="ALL">All Medicines</option>
            {specOptions.map((o) => (
              <option key={o.medicineId} value={String(o.medicineId)}>{o.label}</option>
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
            onChange={(e) => setNotesSearch(e.target.value)}
          />
        </div>
      </div>

      {filtered.length === 0 ? (
        <p className="empty-message">No transactions found.</p>
      ) : (
        <div className="transactions-list">
          {filtered.map((tx) => {
            const status = tx.status ?? 'UNKNOWN';
            return (
              <div key={tx.id} className={`transaction-card status-${status.toLowerCase()}`}>
                <div className="tx-header">
                  <span>#{tx.id}</span>
                  <span className={`badge badge-${status.toLowerCase()}`}>{status}</span>
                  {status === 'PENDING' && (
                    <button
                      type="button"
                      className="btn btn-sm btn-delete"
                      disabled={deletingId === tx.id}
                      onClick={() => handleDelete(tx.id)}>
                      {deletingId === tx.id ? '…' : 'Delete'}
                    </button>
                  )}
                </div>
                <p><strong>Medicine:</strong> {tx.medicineName ?? 'Unknown'} ({tx.medicineType ?? 'Unknown'}, {specLabel(tx)})</p>
                <p><strong>Stock Type:</strong> {inventoryTypeLabel(tx.inventoryType)}</p>
                <p><strong>Quantity:</strong> {Number(tx.quantity).toFixed(1)}</p>
                <p><strong>Note:</strong> {tx.notes}</p>
                <p><strong>Submitted:</strong> {tx.submittedAt ? new Date(tx.submittedAt).toLocaleString() : '—'}</p>
                {tx.screenshots?.length > 0 && (
                  <div className="tx-screenshot-user">
                    <p className="screenshot-label"><strong>Payment Screenshot{tx.screenshots.length > 1 ? 's' : ''}:</strong></p>
                    <PaymentScreenshotViewer screenshots={tx.screenshots} transactionId={tx.id} />
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {hasMore && (
        <div ref={sentinelRef} className="scroll-sentinel">
          {loadingMore && <div className="loading">Loading more…</div>}
        </div>
      )}

      {!hasMore && !loading && allTxs.length > 0 && (
        <p className="all-loaded-msg" aria-live="polite">
          All {allTxs.length} transactions loaded.
        </p>
      )}
    </div>
  );
}
