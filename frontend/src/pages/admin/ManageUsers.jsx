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
                                    <th>Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {users.map(u => (
                                    <tr key={u.id}>
                                        <td>{u.username}</td>
                                        <td>{u.fullName}</td>
                                        <td>{u.email}</td>
                                        <td><span className={`role-badge role-${u.role.toLowerCase()}`}>{u.role}</span></td>
                                        <td><span className={`status-badge ${u.active ? 'active' : 'inactive'}`}>{u.active ? 'Active' : 'Inactive'}</span></td>
                                        <td>
                                            <button
                                                type="button"
                                                className={`btn btn-sm ${u.active ? 'btn-reject' : 'btn-approve'}`}
                                                disabled={togglingId === u.id}
                                                onClick={() => handleToggle(u.id)}>
                                                {togglingId === u.id ? '…' : u.active ? 'Deactivate' : 'Activate'}
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </div>
    );
}
