import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';

const specDisplay = (item) =>
    item.medicineType === 'VIAL'
        ? `${item.concentrationMgPerMl ?? item.specification} mg/ml`
        : `${item.specification} ${item.specUnit}`;

export default function AdminInventory() {
    const [inventory, setInventory] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [sortBy, setSortBy] = useState('spec');
    const [specFilter, setSpecFilter] = useState('');
    const [userFilter, setUserFilter] = useState('');

    useEffect(() => {
        api.getAdminInventory()
            .then(r => setInventory(r.data))
            .catch(() => setError('Failed to load stock'))
            .finally(() => setLoading(false));
    }, []);

    const nonZero = inventory.filter(item => item.quantity > 0);

    const specOptions = [...new Set(nonZero.map(i => i.medicineName))].sort();
    const userOptions = [...new Set(nonZero.map(i => i.username))].sort();

    const items = nonZero
        .filter(item => !specFilter || item.medicineName === specFilter)
        .filter(item => !userFilter || item.username === userFilter)
        .slice()
        .sort((a, b) => {
            if (sortBy === 'spec') {
                return a.medicineName.localeCompare(b.medicineName)
                    || a.username.localeCompare(b.username);
            }
            return a.username.localeCompare(b.username)
                || a.medicineName.localeCompare(b.medicineName);
        });

    if (loading) return <div className="loading">Loading inventory…</div>;

    return (
        <div className="page">
            <div className="page-header">
                <h1>View Available Medicine Stock</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error && <div role="alert" className="alert alert-error">{error}</div>}

            <div className="form-card" style={{ marginBottom: '1rem' }}>
                <div className="form-row" style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
                    <div className="form-group" style={{ flex: 1 }}>
                        <label htmlFor="spec-filter-select">Medicine Specification</label>
                        <select
                            id="spec-filter-select"
                            value={specFilter}
                            onChange={e => setSpecFilter(e.target.value)}>
                            <option value="">All Specifications</option>
                            {specOptions.map(s => (
                                <option key={s} value={s}>{s}</option>
                            ))}
                        </select>
                    </div>
                    <div className="form-group" style={{ flex: 1 }}>
                        <label htmlFor="user-filter-select">Username</label>
                        <select
                            id="user-filter-select"
                            value={userFilter}
                            onChange={e => setUserFilter(e.target.value)}>
                            <option value="">All Users</option>
                            {userOptions.map(u => (
                                <option key={u} value={u}>{u}</option>
                            ))}
                        </select>
                    </div>
                </div>
            </div>

            <div className="filter-tabs" role="group" aria-label="Sort stock by">
                <button
                    type="button"
                    className={`filter-tab ${sortBy === 'spec' ? 'active' : ''}`}
                    onClick={() => setSortBy('spec')}>
                    By Spec
                </button>
                <button
                    type="button"
                    className={`filter-tab ${sortBy === 'user' ? 'active' : ''}`}
                    onClick={() => setSortBy('user')}>
                    By User
                </button>
            </div>

            {items.length === 0 ? (
                <p className="empty-message">No medicine stock records found.</p>
            ) : (
                <div className="table-wrapper">
                    <table className="data-table">
                        <thead>
                            <tr>
                                <th>User</th>
                                <th>Medicine</th>
                                <th>Type</th>
                                <th>Specification</th>
                                <th>Price</th>
                                <th>Pharma Company</th>
                                <th>Quantity</th>
                            </tr>
                        </thead>
                        <tbody>
                            {items.map(item => (
                                <tr key={item.id}>
                                    <td>{item.username}</td>
                                    <td>{item.medicineName}</td>
                                    <td>{item.medicineType}</td>
                                    <td>{specDisplay(item)}</td>
                                    <td>Rs {item.price?.toLocaleString('en-IN')}</td>
                                    <td>{item.pharmaName}</td>
                                    <td><span className="qty-badge">{item.quantity}</span></td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
