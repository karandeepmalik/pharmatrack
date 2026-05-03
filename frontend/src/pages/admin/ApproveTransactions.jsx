import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import { TRANSACTION_STATUSES } from '../../constants';

/**
 * Lightbox viewer for payment screenshots.
 * Extracted as a named component within this file for clarity.
 */
function PaymentScreenshotViewer({ screenshot, screenshotType, transactionId }) {
  const [open, setOpen] = useState(false);

  if (!screenshot) return <span className="no-screenshot">No screenshot</span>;

  const dataUri = `data:${screenshotType || 'image/png'};base64,${screenshot}`;

  return (
    <>
      <button
        type="button"
        className="screenshot-thumb-btn"
        onClick={() => setOpen(true)}
        aria-label={`View payment screenshot for transaction #${transactionId}`}
        title="Click to view full screenshot"
      >
        <img
          src={dataUri}
          alt={`Payment screenshot for transaction #${transactionId}`}
          className="screenshot-thumb-img"
        />
        <span className="screenshot-badge">🔍 View</span>
      </button>

      {open && (
        <div
          className="screenshot-lightbox-overlay"
          role="dialog" aria-modal="true"
          aria-label={`Payment screenshot for transaction #${transactionId}`}
          onClick={() => setOpen(false)}
        >
          <div className="screenshot-lightbox-content" onClick={(e) => e.stopPropagation()}>
            <div className="lightbox-header">
              <h3>Payment Screenshot — Transaction #{transactionId}</h3>
              <button type="button" className="lightbox-close"
                onClick={() => setOpen(false)} aria-label="Close screenshot viewer">
                ✕
              </button>
            </div>
            <img src={dataUri}
              alt={`Full payment screenshot for transaction #${transactionId}`}
              className="screenshot-full-img" />
            <div className="lightbox-footer">
              <a href={dataUri}
                download={`payment-screenshot-tx-${transactionId}.png`}
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

/**
 * Admin page for reviewing, approving and rejecting inventory adjustment requests.
 *
 * Fixes applied vs original:
 *  - handleApprove / handleReject merged into handleDecision (DRY)
 *  - errorMessage cleared on successful action
 *  - fetchTransactions wrapped in useCallback (stable reference)
 *  - filter constants imported from constants.js (no magic values)
 */
export default function ApproveTransactions() {
  const [transactions, setTransactions] = useState([]);
  const [filter, setFilter]             = useState('PENDING');
  const [loading, setLoading]           = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [actionLoading, setActionLoading] = useState(null);

  const fetchTransactions = useCallback(() => {
    setLoading(true);
    api.getAllTransactions()
      .then((r) => setTransactions(r.data))
      .catch(() => setErrorMessage('Failed to load transactions'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetchTransactions(); }, [fetchTransactions]);

  /** Single handler for both approve and reject — eliminates DRY violation. */
  const handleDecision = async (id, approved) => {
    setActionLoading(id);
    setErrorMessage(''); // clear stale error before new attempt
    try {
      await api.approveTransaction(id, { approved });
      fetchTransactions();
    } catch {
      setErrorMessage(`Failed to ${approved ? 'approve' : 'reject'} transaction`);
    } finally {
      setActionLoading(null);
    }
  };

  const filtered = filter === 'ALL'
    ? transactions
    : transactions.filter((t) => t.status === filter);

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

      {filtered.length === 0 ? (
        <p className="empty-message">
          No {filter === 'ALL' ? '' : filter.toLowerCase()} transactions found.
        </p>
      ) : (
        <div className="transactions-list">
          {filtered.map((tx) => (
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
                  <p><strong>Submitted:</strong>{' '}
                    {tx.submittedAt ? new Date(tx.submittedAt).toLocaleString() : '—'}
                  </p>
                  {tx.approvedByUsername && (
                    <p><strong>Reviewed by:</strong> {tx.approvedByUsername} on{' '}
                      {new Date(tx.approvedAt).toLocaleString()}
                    </p>
                  )}
                  <p><strong>Adjustment Note:</strong>{' '}
                    <span className="tx-notes">{tx.notes}</span>
                  </p>
                </div>

                <div className="tx-screenshot-col">
                  <p className="screenshot-label"><strong>Payment Screenshot:</strong></p>
                  <PaymentScreenshotViewer
                    screenshot={tx.paymentScreenshot}
                    screenshotType={tx.paymentScreenshotType}
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
    </div>
  );
}
