import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import PaymentScreenshotViewer from '../../components/PaymentScreenshotViewer';

const PAGE_SIZE = 20;

export default function MyTransactions() {
  const [allTxs, setAllTxs]         = useState([]);
  const [hasMore, setHasMore]       = useState(false);
  const [filter, setFilter]         = useState('ALL');
  const [loading, setLoading]       = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError]           = useState(null);

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

  const filtered = filter === 'ALL' ? allTxs : allTxs.filter((t) => t.status === filter);

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

      {filtered.length === 0 ? (
        <p className="empty-message">No transactions found.</p>
      ) : (
        <div className="transactions-list">
          {filtered.map((tx) => {
            const status   = tx.status ?? 'UNKNOWN';
            const specLabel = tx.medicineType === 'VIAL'
              ? `${tx.concentrationMgPerMl ?? tx.specification ?? '?'} mg/ml`
              : `${tx.specification ?? '?'} mg`;
            return (
              <div key={tx.id} className={`transaction-card status-${status.toLowerCase()}`}>
                <div className="tx-header">
                  <span>#{tx.id}</span>
                  <span className={`badge badge-${status.toLowerCase()}`}>{status}</span>
                </div>
                <p><strong>Medicine:</strong> {tx.medicineName ?? 'Unknown'} ({tx.medicineType ?? 'Unknown'}, {specLabel})</p>
                <p><strong>Quantity:</strong> {tx.quantity}</p>
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
