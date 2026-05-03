import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';

const EMPTY_FORM = { username: '', email: '', fullName: '', password: '', role: 'USER' };

export default function ManageUsers() {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [form, setForm] = useState(EMPTY_FORM);
    const [submitting, setSubmitting] = useState(false);
    const [togglingId, setTogglingId] = useState(null);
    const [deletingId, setDeletingId] = useState(null);

    // Password change state — keyed by user id
    const [pwForm, setPwForm] = useState({});   // { [id]: newPassword }
    const [pwSubmitting, setPwSubmitting] = useState(null);
    const [pwExpanded, setPwExpanded] = useState(null); // which row has pw form open

    const fetchUsers = useCallback(() => {
        api.getUsers()
            .then(r => setUsers(r.data))
            .catch(() => setError('Failed to load users'))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => { fetchUsers(); }, [fetchUsers]);

    const handleSubmit = async e => {
        e.preventDefault();
        setError(''); setSuccess(''); setSubmitting(true);
        try {
            await api.createUser(form);
            setSuccess(`User "${form.username}" created successfully.`);
            setForm(EMPTY_FORM);
            fetchUsers();
        } catch (ex) {
            setError(ex.response?.data?.message || 'Failed to create user');
        } finally {
            setSubmitting(false);
        }
    };

    const handleToggle = async (id) => {
        setError(''); setTogglingId(id);
        try {
            await api.toggleUser(id);
            fetchUsers();
        } catch {
            setError('Failed to update user status');
        } finally {
            setTogglingId(null);
        }
    };

    const handleDelete = async (id, username) => {
        if (!window.confirm(`Permanently delete user "${username}"? This will also remove all their inventory and transactions. This cannot be undone.`)) return;
        setError(''); setDeletingId(id);
        try {
            await api.deleteUser(id);
            setSuccess(`User "${username}" deleted successfully.`);
            fetchUsers();
        } catch (ex) {
            setError(ex.response?.data?.message || 'Failed to delete user');
        } finally {
            setDeletingId(null);
        }
    };

    const handlePasswordChange = async (userId, username) => {
        const newPassword = pwForm[userId] || '';
        if (newPassword.length < 8) {
            setError('Password must be at least 8 characters.');
            return;
        }
        setError(''); setPwSubmitting(userId);
        try {
            await api.adminChangePassword(userId, { newPassword });
            setSuccess(`Password for "${username}" updated successfully.`);
            setPwForm(f => ({ ...f, [userId]: '' }));
            setPwExpanded(null);
        } catch (ex) {
            setError(ex.response?.data?.message || 'Failed to update password.');
        } finally {
            setPwSubmitting(null);
        }
    };

    const set = field => e => setForm(f => ({ ...f, [field]: e.target.value }));

    return (
        <div className="page">
            <div className="page-header">
                <h1>Manage Users</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error   && <div role="alert" className="alert alert-error">{error}</div>}
            {success && <div role="alert" className="alert alert-success">{success}</div>}

            <section className="form-section">
                <h2>Add New User</h2>
                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="fullName">Full Name</label>
                        <input id="fullName" type="text" value={form.fullName} onChange={set('fullName')} required />
                    </div>
                    <div className="form-group">
                        <label htmlFor="username">Username</label>
                        <input id="username" type="text" value={form.username} onChange={set('username')} required minLength={3} />
                    </div>
                    <div className="form-group">
                        <label htmlFor="email">Email</label>
                        <input id="email" type="email" value={form.email} onChange={set('email')} required />
                    </div>
                    <div className="form-group">
                        <label htmlFor="password">Password</label>
                        <input id="password" type="password" value={form.password} onChange={set('password')} required minLength={8} />
                    </div>
                    <div className="form-group">
                        <label htmlFor="role">Role</label>
                        <select id="role" value={form.role} onChange={set('role')}>
                            <option value="USER">User</option>
                            <option value="ADMIN">Admin</option>
                        </select>
                    </div>
                    <button type="submit" className="btn btn-primary" disabled={submitting}>
                        {submitting ? 'Creating…' : 'Create User'}
                    </button>
                </form>
            </section>

            <section style={{ marginTop: '2rem' }}>
                <h2>All Users</h2>
                {loading ? <p>Loading…</p> : (
                    <div className="table-wrapper">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>Username</th>
                                    <th>Full Name</th>
                                    <th>Email</th>
                                    <th>Role</th>
                                    <th>Status</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {users.map(u => (
                                    <React.Fragment key={u.id}>
                                        <tr>
                                            <td>{u.username}</td>
                                            <td>{u.fullName}</td>
                                            <td>{u.email}</td>
                                            <td><span className={`role-badge role-${u.role.toLowerCase()}`}>{u.role}</span></td>
                                            <td><span className={`status-badge ${u.active ? 'active' : 'inactive'}`}>{u.active ? 'Active' : 'Inactive'}</span></td>
                                            <td style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                                                <button
                                                    type="button"
                                                    className={`btn btn-sm ${u.active ? 'btn-reject' : 'btn-approve'}`}
                                                    disabled={togglingId === u.id}
                                                    onClick={() => handleToggle(u.id)}>
                                                    {togglingId === u.id ? '…' : u.active ? 'Deactivate' : 'Activate'}
                                                </button>
                                                <button
                                                    type="button"
                                                    className="btn btn-sm btn-secondary"
                                                    onClick={() => setPwExpanded(pwExpanded === u.id ? null : u.id)}>
                                                    {pwExpanded === u.id ? 'Cancel' : 'Change Password'}
                                                </button>
                                                <button
                                                    type="button"
                                                    className="btn btn-sm btn-delete"
                                                    disabled={deletingId === u.id}
                                                    onClick={() => handleDelete(u.id, u.username)}>
                                                    {deletingId === u.id ? '…' : 'Delete'}
                                                </button>
                                            </td>
                                        </tr>
                                        {pwExpanded === u.id && (
                                            <tr>
                                                <td colSpan={6}>
                                                    <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', padding: '0.5rem 0' }}>
                                                        <input
                                                            type="password"
                                                            aria-label={`New password for ${u.username}`}
                                                            placeholder="New password (min 8 chars)"
                                                            value={pwForm[u.id] || ''}
                                                            minLength={8}
                                                            onChange={e => setPwForm(f => ({ ...f, [u.id]: e.target.value }))}
                                                            style={{ flex: 1 }}
                                                        />
                                                        <button
                                                            type="button"
                                                            className="btn btn-primary btn-sm"
                                                            disabled={pwSubmitting === u.id || (pwForm[u.id] || '').length < 8}
                                                            onClick={() => handlePasswordChange(u.id, u.username)}>
                                                            {pwSubmitting === u.id ? 'Saving…' : 'Set Password'}
                                                        </button>
                                                    </div>
                                                </td>
                                            </tr>
                                        )}
                                    </React.Fragment>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </div>
    );
}
