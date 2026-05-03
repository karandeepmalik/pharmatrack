import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';

const specLabel = (type, spec) =>
    type === 'VIAL' ? `${spec} mg/ml` : `${spec} mg (10 Tablets)`;

export default function ModifyInventory() {
    const [users, setUsers]         = useState([]);
    const [medicines, setMedicines] = useState([]);
    const [inventory, setInventory] = useState([]);
    const [form, setForm]           = useState({ userId: '', medicineId: '', adjustmentType: 'ADD', quantity: '', note: '' });
    const [msg, setMsg]             = useState('');
    const [err, setErr]             = useState('');
    const [submitting, setSubmitting] = useState(false);

    const reload = () =>
        api.getAdminInventory().then(r => setInventory(r.data));

    useEffect(() => {
        api.getUsers().then(r => setUsers(r.data));
        api.getMedicines().then(r => setMedicines(r.data));
        reload();
    }, []);

    const set = field => e => setForm(f => ({ ...f, [field]: e.target.value }));

    const currentQty = (() => {
        if (!form.userId || !form.medicineId) return null;
        const found = inventory.find(
            i => String(i.userId) === form.userId && String(i.medicineId) === form.medicineId
        );
        return found?.quantity ?? 0;
    })();

    const isValid =
        form.userId && form.medicineId && form.adjustmentType &&
        Number(form.quantity) >= 1 &&
        form.note.trim().length >= 5;

    const handle = async e => {
        e.preventDefault();
        setMsg(''); setErr(''); setSubmitting(true);
        try {
            await api.adjustInventory({
                userId:         Number(form.userId),
                medicineId:     Number(form.medicineId),
                adjustmentType: form.adjustmentType,
                quantity:       Number(form.quantity),
                note:           form.note.trim(),
            });
            setMsg(`Inventory ${form.adjustmentType === 'ADD' ? 'added' : 'reduced'} successfully.`);
            setForm(f => ({ ...f, quantity: '', note: '' }));
            reload();
        } catch (ex) {
            const status = ex.response?.status;
            if (status === 409) {
                setErr(`Insufficient stock. Current quantity: ${currentQty ?? 0}.`);
            } else {
                setErr(ex.response?.data?.message || 'Failed to modify inventory.');
            }
        } finally {
            setSubmitting(false);
        }
    };

    const selectedMedicine = medicines.find(m => String(m.id) === form.medicineId);

    return (
        <div className="page">
            <div className="page-header">
                <h1>Modify Inventory</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>
            <p className="page-subtitle">Add or reduce inventory for a user. A note explaining the reason is required.</p>

            {msg && <div role="alert" className="alert alert-success">{msg}</div>}
            {err && <div role="alert" className="alert alert-error">{err}</div>}

            <div className="form-card">
                <form onSubmit={handle}>
                    <div className="form-group">
                        <label htmlFor="user-select">User</label>
                        <select id="user-select" value={form.userId} onChange={set('userId')} required>
                            <option value="">-- Select User --</option>
                            {users.filter(u => u.role === 'USER' && u.active)
                                .map(u => <option key={u.id} value={u.id}>{u.fullName} ({u.username})</option>)}
                        </select>
                    </div>

                    <div className="form-group">
                        <label htmlFor="medicine-select">Medicine</label>
                        <select id="medicine-select" value={form.medicineId}
                            onChange={e => setForm(f => ({ ...f, medicineId: e.target.value, quantity: '' }))} required>
                            <option value="">-- Select Medicine --</option>
                            {medicines.map(m => (
                                <option key={m.id} value={m.id}>
                                    {m.name} – {specLabel(m.type, m.specification)} — Rs {m.price?.toLocaleString('en-IN')}
                                </option>
                            ))}
                        </select>
                    </div>

                    {form.userId && form.medicineId && (
                        <div className="availability-badge">
                            Current quantity for user: <strong>{currentQty}</strong> units
                            {selectedMedicine && (
                                <span style={{ marginLeft: '1rem' }}>
                                    Price: <strong>Rs {selectedMedicine.price?.toLocaleString('en-IN')}</strong>
                                </span>
                            )}
                        </div>
                    )}

                    <div className="form-group">
                        <label htmlFor="type-select">Adjustment Type</label>
                        <select id="type-select" value={form.adjustmentType} onChange={set('adjustmentType')} required>
                            <option value="ADD">Add Inventory</option>
                            <option value="REDUCE">Reduce Inventory</option>
                        </select>
                    </div>

                    <div className="form-group">
                        <label htmlFor="qty-input">
                            Quantity
                            {form.adjustmentType === 'REDUCE' && currentQty !== null &&
                                ` (max ${currentQty})`}
                        </label>
                        <input id="qty-input" type="number" min="1"
                            max={form.adjustmentType === 'REDUCE' && currentQty !== null ? currentQty : undefined}
                            value={form.quantity}
                            disabled={!form.medicineId}
                            onChange={set('quantity')}
                            required />
                    </div>

                    <div className="form-group">
                        <label htmlFor="note-input">
                            Reason for Modification <span className="required">*</span>
                        </label>
                        <textarea id="note-input" rows={3}
                            placeholder="e.g. Restocking for Ward 3 monthly supply"
                            value={form.note} onChange={set('note')}
                            maxLength={500} />
                        <small>{form.note.length}/500 characters (minimum 5)</small>
                    </div>

                    <button type="submit" className="btn btn-primary"
                        disabled={!isValid || submitting}>
                        {submitting ? 'Saving…' : `${form.adjustmentType === 'ADD' ? 'Add' : 'Reduce'} Inventory`}
                    </button>
                </form>
            </div>

            {inventory.length > 0 && (
                <div className="form-section" style={{ marginTop: '2rem' }}>
                    <h2>Current User Inventory</h2>
                    <div className="table-wrapper">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>User</th>
                                    <th>Medicine</th>
                                    <th>Type</th>
                                    <th>Specification</th>
                                    <th>Price</th>
                                    <th>Quantity</th>
                                </tr>
                            </thead>
                            <tbody>
                                {inventory.map(i => (
                                    <tr key={i.id}>
                                        <td>{i.username}</td>
                                        <td>{i.medicineName}</td>
                                        <td>{i.medicineType}</td>
                                        <td>{i.specification} {i.specUnit}</td>
                                        <td>Rs {i.price?.toLocaleString('en-IN')}</td>
                                        <td><span className="qty-badge">{i.quantity}</span></td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}
