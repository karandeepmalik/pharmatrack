import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';

export default function AdminInventory() {
    const [inventory, setInventory] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        api.getAdminInventory()
            .then(r => setInventory(r.data))
            .catch(() => setError('Failed to load inventory'))
            .finally(() => setLoading(false));
    }, []);

    if (loading) return <div className="loading">Loading inventory…</div>;

    return (
        <div className="page">
            <div className="page-header">
                <h1>View Available Inventory</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error && <div role="alert" className="alert alert-error">{error}</div>}

            {inventory.length === 0 ? (
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
                            {inventory.map(item => (
                                <tr key={item.id}>
                                    <td>{item.username}</td>
                                    <td>{item.medicineName}</td>
                                    <td>{item.medicineType}</td>
                                    <td>{item.specification} {item.specUnit}</td>
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
