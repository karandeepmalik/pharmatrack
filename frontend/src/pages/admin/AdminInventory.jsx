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

    useEffect(() => {
        api.getAdminInventory()
            .then(r => setInventory(r.data))
            .catch(() => setError('Failed to load inventory'))
            .finally(() => setLoading(false));
    }, []);

    const items = inventory
        .filter(item => item.quantity > 0)
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
                <h1>View Available Inventory</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error && <div role="alert" className="alert alert-error">{error}</div>}

            <div className="filter-tabs" role="group" aria-label="Sort inventory by">
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
                <p className="empty-message">No inventory records found.</p>
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
