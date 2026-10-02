import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import { TRANSACTION_STATUSES } from '../../constants';
import PaymentScreenshotViewer from '../../components/PaymentScreenshotViewer';

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
  const [selectedIds, setSelectedIds]   = useState(new Set());
  const [bulkLoading, setBulkLoading]   = useState(false);
  const [bulkMessage, setBulkMessage]   = useState('');

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
      // A fresh page-0 load (filter change, or a reload after an approve/reject action)
      // replaces the loaded set entirely, so any prior bulk selection no longer corresponds
      // to what's on screen.
      setSelectedIds(new Set());
    } else {
      setLoadingMore(true);
    }
    setErrorMessage('');
    setBulkMessage('');

    return api.getAllTransactions(pg, PAGE_SIZE, status)
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

  // Infinite-scroll sentinel — (re)attaches whenever the sentinel enters the DOM
  // hasMore must be in deps: at mount hasMore=false so the sentinel isn't rendered
  // yet and sentinelRef.current is null. The effect re-runs when hasMore flips true
  // and the sentinel is in the DOM.
  useEffect(() => {
    if (!hasMore) return;
    const el = sentinelRef.current;
    if (!el) return;
    const obs = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting && !loadingRef.current) {
        loadPage(pageRef.current + 1, filterRef.current);
      }
    }, { threshold: 0 });
    obs.observe(el);
    return () => obs.disconnect();
  }, [hasMore, loadPage]);

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

  // ── Bulk approve ──────────────────────────────────────────────────────
  // Scoped to currently-loaded PENDING cards only — same "loaded, not everything that could
  // ever match" scope as every other infinite-scroll list in this app (see scrollUntilVisible
  // usage in e2e-browser tests); there is no existing "select across all pages" precedent here.
  const pendingLoaded = transactions.filter((tx) => tx.status === 'PENDING');
  const allPendingSelected = pendingLoaded.length > 0 && pendingLoaded.every((tx) => selectedIds.has(tx.id));

  const toggleSelected = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    setSelectedIds(allPendingSelected ? new Set() : new Set(pendingLoaded.map((tx) => tx.id)));
  };

  const handleBulkApprove = async () => {
    if (selectedIds.size === 0) return;
    setBulkLoading(true);
    setErrorMessage('');
    setBulkMessage('');
    try {
      const items = Array.from(selectedIds).map((id) => {
        const item = { id };
        const priceStr = priceOverrides[id];
        if (priceStr !== undefined && priceStr !== '') {
          item.newPrice = parseInt(priceStr, 10);
        }
        return item;
      });
      const res = await api.approveTransactionsBulk(items);
      const results = res.data ?? [];
      const failed = results.filter((r) => !r.approved);
      const succeededCount = results.length - failed.length;

      // Reload from page 0 BEFORE setting the summary message — loadPage clears both
      // message states as soon as it starts (same as every other reload), so setting the
      // message first would just get wiped out the instant the reload kicks off.
      pageRef.current = 0;
      await loadPage(0, filterRef.current);

      if (failed.length === 0) {
        setBulkMessage(`${succeededCount} dispatch${succeededCount !== 1 ? 'es' : ''} approved.`);
      } else {
        setErrorMessage(
          `${succeededCount} approved, ${failed.length} failed: ` +
          failed.map((f) => `#${f.id} (${f.error || 'error'})`).join('; ')
        );
      }
    } catch {
      setErrorMessage('Failed to approve selected transactions');
    } finally {
      setBulkLoading(false);
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
      {bulkMessage && (
        <div role="status" className="alert alert-success">{bulkMessage}</div>
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

      {pendingLoaded.length > 0 && (
        <div className="bulk-select-row">
          <label className="bulk-select-all">
            <input
              type="checkbox"
              checked={allPendingSelected}
              onChange={toggleSelectAll}
              disabled={bulkLoading}
              aria-label="Select all loaded pending transactions"
            />
            Select all loaded pending ({pendingLoaded.length})
          </label>

          {selectedIds.size > 0 && (
            <div className="bulk-action-bar">
              <span className="bulk-selected-count">{selectedIds.size} selected</span>
              <button type="button" className="btn btn-approve btn-sm"
                disabled={bulkLoading}
                onClick={handleBulkApprove}>
                {bulkLoading ? 'Approving…' : `✓ Approve Selected (${selectedIds.size})`}
              </button>
              <button type="button" className="btn btn-secondary btn-sm"
                disabled={bulkLoading}
                onClick={() => setSelectedIds(new Set())}>
                Clear
              </button>
            </div>
          )}
        </div>
      )}

      {transactions.length === 0 && !hasMore ? (
        <p className="empty-message">
          No {filter === 'ALL' ? '' : filter.toLowerCase()} transactions found.
        </p>
      ) : (
        <div className="transactions-list">
          {transactions.map((tx) => (
            <div key={tx.id} className={`transaction-card status-${tx.status.toLowerCase()}`}>
              <div className="tx-header">
                <div className="tx-header-left">
                  {tx.status === 'PENDING' && (
                    <input
                      type="checkbox"
                      className="tx-select-checkbox"
                      checked={selectedIds.has(tx.id)}
                      onChange={() => toggleSelected(tx.id)}
                      disabled={bulkLoading}
                      aria-label={`Select transaction #${tx.id} for bulk approval`}
                    />
                  )}
                  <span className="tx-id">#{tx.id}</span>
                </div>
                <span className={`tx-status badge-${tx.status.toLowerCase()}`}>{tx.status}</span>
              </div>

              <div className="tx-body">
                <div className="tx-fields">
                  <p><strong>Submitted by:</strong> {tx.submittedByFullName} ({tx.submittedByUsername})</p>
                  <p><strong>Medicine:</strong> {tx.medicineName} — {tx.medicineType === 'VIAL' ? `${tx.concentrationMgPerMl ?? tx.specification} mg/ml` : `${tx.specification} mg (10 Tablets)`}</p>
                  <p><strong>Pharma:</strong> {tx.pharmaName}</p>
                  <p><strong>Quantity:</strong> {Number(tx.quantity).toFixed(1)}</p>
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
                    disabled={actionLoading === tx.id || bulkLoading}
                    onClick={() => handleDecision(tx.id, true)}>
                    {actionLoading === tx.id ? '…' : '✓ Approve'}
                  </button>
                  <button type="button" className="btn btn-reject"
                    disabled={actionLoading === tx.id || bulkLoading}
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
