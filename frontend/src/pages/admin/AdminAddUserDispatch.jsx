import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import DispatchForm from '../../components/DispatchForm';

// Scoped separately from the user-facing submit page's own storage key (pendingDispatchIdempotencyKey)
// — sessionStorage is already per-tab/per-browser-session so collision isn't realistic, but a
// distinct name keeps the two flows unambiguous if that ever changes. Suffixed per selected
// user so switching users mid-session (before submitting) can't pick up a stale key meant for
// a different target.
const IDEMPOTENCY_KEY_STORAGE_PREFIX = 'pendingAdminUserDispatchIdempotencyKey';

export default function AdminAddUserDispatch() {
  const [users, setUsers] = useState([]);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [selectedUserId, setSelectedUserId] = useState('');

  useEffect(() => {
    api.getUsers()
      .then((r) => setUsers((r.data || []).filter((u) => u.role !== 'ADMIN')))
      .catch(() => {})
      .finally(() => setLoadingUsers(false));
  }, []);

  // Stable per selected user — DispatchForm is remounted (via key={selectedUserId} below)
  // whenever the selection changes, so these only ever need to be correct for "whichever user
  // is currently selected", not track changes themselves.
  const fetchStock = useCallback(
    () => api.getAvailableMedicineStockForUser(selectedUserId),
    [selectedUserId]
  );
  const submitForSelectedUser = useCallback(
    (payload) => api.submitTransactionForUser(selectedUserId, payload),
    [selectedUserId]
  );

  const selectedUser = users.find((u) => String(u.id) === selectedUserId);

  return (
    <div className="page submit-transaction-page">
      <div className="page-header">
        <h1>Add User Dispatches</h1>
        <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
      </div>

      <div className="form-group">
        <label htmlFor="target-user-select">User</label>
        <select
          id="target-user-select"
          value={selectedUserId}
          disabled={loadingUsers}
          onChange={(e) => setSelectedUserId(e.target.value)}>
          <option value="">-- Select a User --</option>
          {users.map((u) => (
            <option key={u.id} value={String(u.id)}>
              {u.fullName} ({u.username})
            </option>
          ))}
        </select>
      </div>

      {selectedUser ? (
        <>
          <h2>Adding a dispatch for {selectedUser.fullName}</h2>
          <DispatchForm
            key={selectedUserId}
            submitButtonLabel="Add Dispatch"
            successMessage={`Medicine dispatch submitted successfully for ${selectedUser.fullName} and is pending admin approval.`}
            idempotencyStorageKey={`${IDEMPOTENCY_KEY_STORAGE_PREFIX}:${selectedUserId}`}
            fetchStock={fetchStock}
            onSubmit={submitForSelectedUser}
          />
        </>
      ) : (
        <p className="empty-message">Select a user above to add a dispatch on their behalf.</p>
      )}
    </div>
  );
}
