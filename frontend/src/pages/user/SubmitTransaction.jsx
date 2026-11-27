import React from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import DispatchForm from '../../components/DispatchForm';

// Persisted in sessionStorage (not component state) so the same key survives a retry even if
// the page gets reloaded/remounted in between — e.g. the user backgrounds the app mid-submit
// on a slow mobile connection, the response never arrives, and they resubmit once they're back.
// The backend treats a repeat of the same key as a no-op replay instead of a second dispatch.
const IDEMPOTENCY_KEY_STORAGE_KEY = 'pendingDispatchIdempotencyKey';

export default function SubmitTransaction() {
  return (
    <div className="page submit-transaction-page">
      <div className="page-header">
        <h1>Submit Medicine Dispatch</h1>
        <Link to="/user/dashboard" className="btn btn-secondary">← Back</Link>
      </div>

      <DispatchForm
        submitButtonLabel="Submit Medicine Dispatch"
        successMessage="Medicine dispatch submitted successfully and is pending admin approval."
        idempotencyStorageKey={IDEMPOTENCY_KEY_STORAGE_KEY}
        fetchStock={api.getAvailableMedicineStock}
        onSubmit={api.submitTransaction}
      />
    </div>
  );
}
