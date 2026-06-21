import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import { TRANSACTION_STATUSES } from '../../constants';

function PaymentScreenshotViewer({ screenshots, transactionId }) {
  const [open, setOpen] = useState(false);
  const [activeIdx, setActiveIdx] = useState(0);

  if (!screenshots || screenshots.length === 0) {
    return <span className="no-screenshot">No screenshot</span>;
  }

  const active = screenshots[activeIdx] || screenshots[0];
  const dataUri = `data:${active.mimeType || 'image/png'};base64,${active.data}`;
  const total = screenshots.length;

  return (
    <>
      <div className="screenshot-thumbs">
        {screenshots.map((ss, idx) => {
          const uri = `data:${ss.mimeType || 'image/png'};base64,${ss.data}`;
          return (
            <button
              key={idx}
              type="button"
              className="screenshot-thumb-btn"
              onClick={() => { setActiveIdx(idx); setOpen(true); }}
              aria-label={`View payment screenshot ${idx + 1} of ${total} for transaction #${transactionId}`}
              title="Click to view full screenshot"
            >
              <img
                src={uri}
                alt={`Payment screenshot ${idx + 1} for transaction #${transactionId}`}
                className="screenshot-thumb-img"
              />
              <span className="screenshot-badge">🔍 View</span>
            </button>
          );
        })}
      </div>

      {open && (
        <div
          className="screenshot-lightbox-overlay"
          role="dialog" aria-modal="true"
          aria-label={`Payment screenshots for transaction #${transactionId}`}
          onClick={() => setOpen(false)}
        >
          <div className="screenshot-lightbox-content" onClick={(e) => e.stopPropagation()}>
            <div className="lightbox-header">
              <h3>
                Payment Screenshot {total > 1 ? `${activeIdx + 1} / ${total} — ` : '— '}
                Transaction #{transactionId}
              </h3>
              <button type="button" className="lightbox-close"
                onClick={() => setOpen(false)} aria-label="Close screenshot viewer">
                ✕
              </button>
            </div>
            <img src={dataUri}
              alt={`Full payment screenshot ${activeIdx + 1} for transaction #${transactionId}`}
              className="screenshot-full-img" />
            <div className="lightbox-footer">
              {total > 1 && (
                <div className="lightbox-nav">
                  <button
                    type="button"
                    className="btn btn-sm btn-secondary"
                    disabled={activeIdx === 0}
                    onClick={() => setActiveIdx((i) => i - 1)}
                    aria-label="Previous screenshot"
                  >
                    ← Prev
                  </button>
                  <span className="lightbox-counter">{activeIdx + 1} / {total}</span>
                  <button
                    type="button"
                    className="btn btn-sm btn-secondary"
                    disabled={activeIdx === total - 1}
                    onClick={() => setActiveIdx((i) => i + 1)}
                    aria-label="Next screenshot"
                  >
                    Next →
                  </button>
                </div>
              )}
              <a href={dataUri}
                download={`payment-screenshot-tx-${transactionId}-${activeIdx + 1}.png`}
                className="btn btn-sm btn-download">
                ⬇ Download
              </a>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

const PAGE_SIZE = 20;

export default function ApproveTransactions() {
  const [transactions, setTransactions] = useState([]);
  const [hasMore, setHasMore]           = useState(false);
  const [filter, setFilter]             = useState('PENDING');
  const [loading, setLoading]           = useState(true);
  const [loadingMore, setLoadingMore]   = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [actionLoading, setActionLoading] = useState(null);
  const [priceOverrides, setPriceOverrides] = useState({});

  const sentinelRef  = useRef(null);
  const pageRef      = useRef(0);       // last successfully loaded page number
  const filterRef    = useRef('PENDING');
  const loadingRef   = useRef(false);   // guard against concurrent requests

  const loadPage = useCallback((pg, status) => {
    if (loadingRef.current) return;
    loadingRef.current = true;
    if (pg === 0) {
      setLoading(true);
      setTransactions([]);
      setHasMore(false);
    } else {
      setLoadingMore(true);
    }
    setErrorMessage('');

    api.getAllTransactions(pg, PAGE_SIZE, status)
      .then((r) => {
        const { content = [], last = true } = r.data ?? {};
        setTransactions((prev) => pg === 0 ? content : [...prev, ...content]);
        setHasMore(!last);
        pageRef.current = pg;
      })
      .catch(() => setErrorMessage('Failed to load transactions'))
      .finally(() => {
        loadingRef.current = false;
        setLoading(false);
        setLoadingMore(false);
      });
  }, []);

  // Reload from page 0 whenever the status filter changes
  useEffect(() => {
    filterRef.current = filter;
    pageRef.current   = 0;
    loadPage(0, filter);
  }, [filter, loadPage]);

  // Infinite-scroll sentinel — fires when the bottom div enters the viewport
  useEffect(() => {
    const el = sentinelRef.current;
    if (!el) return;
    const obs = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting && !loadingRef.current) {
        loadPage(pageRef.current + 1, filterRef.current);
      }
    }, { threshold: 0 });
    obs.observe(el);
    return () => obs.disconnect();
  }, [loadPage]);

  const handleDecision = async (id, approved) => {
    setActionLoading(id);
    setErrorMessage('');
    try {
      const payload = { approved };
      if (approved) {
        const priceStr = priceOverrides[id];
        if (priceStr !== undefined && priceStr !== '') {
          payload.newPrice = parseInt(priceStr, 10);
        }
      }
      await api.approveTransaction(id, payload);
      // Reset and reload from page 0
      pageRef.current = 0;
      loadPage(0, filterRef.current);
    } catch {
      setErrorMessage(`Failed to ${approved ? 'approve' : 'reject'} transaction`);
    } finally {
      setActionLoading(null);
    }
  };

  if (loading) return <div className="loading">Loading transactions…</div>;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Review Adjustments</h1>
        <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
      </div>

      {errorMessage && (
        <div role="alert" className="alert alert-error">{errorMessage}</div>
      )}

      <div className="filter-tabs" role="group" aria-label="Filter transactions by status">
        {TRANSACTION_STATUSES.map((s) => (
          <button key={s} type="button"
            className={`filter-tab ${filter === s ? 'active' : ''}`}
            onClick={() => setFilter(s)}>
            {s}
          </button>
        ))}
      </div>

      {transactions.length === 0 && !hasMore ? (
        <p className="empty-message">
          No {filter === 'ALL' ? '' : filter.toLowerCase()} transactions found.
        </p>
      ) : (
        <div className="transactions-list">
          {transactions.map((tx) => (
            <div key={tx.id} className={`transaction-card status-${tx.status.toLowerCase()}`}>
              <div className="tx-header">
                <span className="tx-id">#{tx.id}</span>
                <span className={`tx-status badge-${tx.status.toLowerCase()}`}>{tx.status}</span>
              </div>

              <div className="tx-body">
                <div className="tx-fields">
                  <p><strong>Submitted by:</strong> {tx.submittedByFullName} ({tx.submittedByUsername})</p>
                  <p><strong>Medicine:</strong> {tx.medicineName} — {tx.medicineType === 'VIAL' ? `${tx.concentrationMgPerMl ?? tx.specification} mg/ml` : `${tx.specification} mg (10 Tablets)`}</p>
                  <p><strong>Pharma:</strong> {tx.pharmaName}</p>
                  <p><strong>Quantity:</strong> {tx.quantity}</p>
                  {tx.status === 'PENDING' && (
                    <div className="tx-price-edit">
                      <label htmlFor={`price-${tx.id}`}><strong>Price (Rs):</strong></label>
                      <input
                        id={`price-${tx.id}`}
                        type="number"
                        min="0"
                        value={priceOverrides[tx.id] ?? (tx.pricePerUnit ?? tx.price ?? '')}
                        onChange={(e) =>
                          setPriceOverrides((prev) => ({ ...prev, [tx.id]: e.target.value }))
                        }
                        className="price-input"
                      />
                    </div>
                  )}
                  <p><strong>Submitted:</strong>{' '}
                    {tx.submittedAt ? new Date(tx.submittedAt).toLocaleString() : '—'}
                  </p>
                  {tx.approvedByUsername && (
                    <p><strong>Reviewed by:</strong> {tx.approvedByUsername} on{' '}
                      {new Date(tx.approvedAt).toLocaleString()}
                    </p>
                  )}
                  <p><strong>Medicine Dispatch Note:</strong>{' '}
                    <span className="tx-notes">{tx.notes}</span>
                  </p>
                </div>

                <div className="tx-screenshot-col">
                  <p className="screenshot-label"><strong>Payment Screenshot{tx.screenshots?.length > 1 ? 's' : ''}:</strong></p>
                  <PaymentScreenshotViewer
                    screenshots={tx.screenshots}
                    transactionId={tx.id}
                  />
                </div>
              </div>

              {tx.status === 'PENDING' && (
                <div className="tx-actions">
                  <button type="button" className="btn btn-approve"
                    disabled={actionLoading === tx.id}
                    onClick={() => handleDecision(tx.id, true)}>
                    {actionLoading === tx.id ? '…' : '✓ Approve'}
                  </button>
                  <button type="button" className="btn btn-reject"
                    disabled={actionLoading === tx.id}
                    onClick={() => handleDecision(tx.id, false)}>
                    {actionLoading === tx.id ? '…' : '✕ Reject'}
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Infinite-scroll sentinel: observer attaches here */}
      {hasMore && (
        <div ref={sentinelRef} className="scroll-sentinel">
          {loadingMore && <div className="loading">Loading more…</div>}
        </div>
      )}

      {!hasMore && !loading && transactions.length > 0 && (
        <p className="all-loaded-msg" aria-live="polite">
          All {transactions.length} transactions loaded.
        </p>
      )}
    </div>
  );
}
