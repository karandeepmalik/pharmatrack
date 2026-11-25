import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import MultiSelectDropdown from '../../components/MultiSelectDropdown';

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

export default function AdminMedicineStock() {
    const [medicineStock, setMedicineStock] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    // Empty array means "no filter" (every page's existing "ALL" convention, applied to the
    // multi-select equivalent).
    const [specFilter, setSpecFilter] = useState([]);
    const [userFilter, setUserFilter] = useState([]);

    useEffect(() => {
        api.getAdminMedicineStock()
            .then(r => setMedicineStock(r.data))
            .catch(() => setError('Failed to load stock'))
            .finally(() => setLoading(false));
    }, []);

    const nonZero = medicineStock.filter(item => item.quantity > 0);

    const regularItems = nonZero.filter(i => i.medicineStockType === 'REGULAR_MEDICINE_STOCK');
    const adminItems   = nonZero.filter(i => i.medicineStockType === 'ADMIN_MEDICINE_STOCK');

    const specOptions = [...new Set(nonZero.map(i => i.medicineName))].sort();
    const userOptions = [...new Set(regularItems.map(i => i.username))].sort();

    const applyFilters = (list) =>
        list
            .filter(item => specFilter.length === 0 || specFilter.includes(item.medicineName))
            .filter(item => userFilter.length === 0 || userFilter.includes(item.username))
            .sort((a, b) =>
                a.medicineName.localeCompare(b.medicineName) ||
                a.username.localeCompare(b.username));

    if (loading) return <div className="loading">Loading medicineStock…</div>;

    return (
        <div className="page">
            <div className="page-header">
                <h1>View Available Medicine Stock</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error && <div role="alert" className="alert alert-error">{error}</div>}

            <div className="form-card">
                <div className="form-row">
                    <MultiSelectDropdown
                        id="spec-filter-select"
                        label="Filter by Medicine"
                        allLabel="All Medicines"
                        options={specOptions.map(s => ({ value: s, label: s }))}
                        selected={specFilter}
                        onChange={setSpecFilter}
                    />
                    <MultiSelectDropdown
                        id="user-filter-select"
                        label="Filter by User"
                        allLabel="All Users"
                        options={userOptions.map(u => ({ value: u, label: u }))}
                        selected={userFilter}
                        onChange={setUserFilter}
                    />
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
