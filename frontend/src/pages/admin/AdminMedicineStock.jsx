import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';

const specDisplay = (item) =>
    item.medicineType === 'VIAL'
        ? `${item.concentrationMgPerMl ?? item.specification} mg/ml`
        : `${item.specification} ${item.specUnit}`;

function StockTable({ items }) {
    if (items.length === 0) {
        return <p className="empty-message">No stock records found.</p>;
    }
    return (
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
                            <td><span className="qty-badge">{Number(item.quantity).toFixed(1)}</span></td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

export default function AdminInventory() {
    const [inventory, setInventory] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [specFilter, setSpecFilter] = useState('');
    const [userFilter, setUserFilter] = useState('');

    useEffect(() => {
        api.getAdminInventory()
            .then(r => setInventory(r.data))
            .catch(() => setError('Failed to load stock'))
            .finally(() => setLoading(false));
    }, []);

    const nonZero = inventory.filter(item => item.quantity > 0);

    const regularItems = nonZero.filter(i => i.inventoryType === 'REGULAR_MEDICINE_STOCK');
    const adminItems   = nonZero.filter(i => i.inventoryType === 'ADMIN_MEDICINE_STOCK');

    const specOptions = [...new Set(nonZero.map(i => i.medicineName))].sort();
    const userOptions = [...new Set(regularItems.map(i => i.username))].sort();

    const applyFilters = (list) =>
        list
            .filter(item => !specFilter || item.medicineName === specFilter)
            .filter(item => !userFilter || item.username === userFilter)
            .sort((a, b) =>
                a.medicineName.localeCompare(b.medicineName) ||
                a.username.localeCompare(b.username));

    if (loading) return <div className="loading">Loading inventory…</div>;

    return (
        <div className="page">
            <div className="page-header">
                <h1>View Available Medicine Stock</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error && <div role="alert" className="alert alert-error">{error}</div>}

            <div className="form-card">
                <div className="form-row">
                    <div className="form-group">
                        <label htmlFor="spec-filter-select">Filter by Medicine</label>
                        <select
                            id="spec-filter-select"
                            value={specFilter}
                            onChange={e => setSpecFilter(e.target.value)}>
                            <option value="">All Medicines</option>
                            {specOptions.map(s => (
                                <option key={s} value={s}>{s}</option>
                            ))}
                        </select>
                    </div>
                    <div className="form-group">
                        <label htmlFor="user-filter-select">Filter by User</label>
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

            <section style={{ marginBottom: '2rem' }}>
                <h2>Regular Stock (User Allocations)</h2>
                <StockTable items={applyFilters(regularItems)} />
            </section>

            <section>
                <h2>Admin Stock (System Stock)</h2>
                <StockTable items={applyFilters(adminItems)} />
            </section>
        </div>
    );
}
