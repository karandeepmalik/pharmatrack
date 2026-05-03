import React, {useState, useEffect} from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';

const specLabel = (type, spec) => type === 'VIAL' ? `${spec} mg/ml` : `${spec} mg`;

export default function AddInventoryToUser() {
    const [users, setUsers]         = useState([]);
    const [medicines, setMedicines] = useState([]);
    const [systemInv, setSystemInv] = useState([]);
    const [form, setForm]           = useState({userId: '', medicineId: '', quantity: ''});
    const [msg, setMsg]             = useState('');
    const [err, setErr]             = useState('');

    useEffect(() => {
        api.getUsers().then(r => setUsers(r.data));
        api.getMedicines().then(r => setMedicines(r.data));
        api.getSystemInventory().then(r => setSystemInv(r.data));
    }, []);

    const selectedSysInv = systemInv.find(s => String(s.medicineId) === form.medicineId);
    const availableQty   = selectedSysInv?.quantity ?? 0;

    const handle = async e => {
        e.preventDefault();
        setMsg(''); setErr('');
        if (Number(form.quantity) > availableQty) {
            setErr(`Cannot allocate ${form.quantity}. Only ${availableQty} available in system inventory.`);
            return;
        }
        try {
            await api.allocateInventory({
                userId:     Number(form.userId),
                medicineId: Number(form.medicineId),
                quantity:   Number(form.quantity),
            });
            setMsg('Inventory allocated successfully.');
            setForm({userId: '', medicineId: '', quantity: ''});
            api.getSystemInventory().then(r => setSystemInv(r.data));
        } catch(ex) {
            setErr(ex.response?.data?.message || 'Failed to allocate inventory');
        }
    };

    return (
        <div className="page">
            <div className="page-header">
                <h1>Add Inventory to User</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>
            <p className="page-subtitle">Allocate stock from system inventory to a user.</p>

            {msg && <div role="alert" className="alert alert-success">{msg}</div>}
            {err && <div role="alert" className="alert alert-error">{err}</div>}

            <div className="form-card">
                <form onSubmit={handle}>
                    <div className="form-group">
                        <label>User</label>
                        <select value={form.userId}
                            onChange={e => setForm(f => ({...f, userId: e.target.value}))} required>
                            <option value="">-- Select User --</option>
                            {users.filter(u => u.role === 'USER' && u.username !== 'lostinventory' && u.active)
                                .map(u => <option key={u.id} value={u.id}>{u.fullName} ({u.username})</option>)}
                        </select>
                    </div>

                    <div className="form-group">
                        <label>Medicine</label>
                        <select value={form.medicineId}
                            onChange={e => setForm(f => ({...f, medicineId: e.target.value, quantity: ''}))} required>
                            <option value="">-- Select Medicine --</option>
                            {medicines.map(m => {
                                const sys = systemInv.find(s => s.medicineId === m.id);
                                const avail = sys?.quantity ?? 0;
                                return (
                                    <option key={m.id} value={m.id} disabled={avail === 0}>
                                        {m.name} – {m.type} {specLabel(m.type, m.specification)}
                                        {avail === 0 ? ' (out of stock)' : ` (${avail} available)`}
                                    </option>
                                );
                            })}
                        </select>
                    </div>

                    {form.medicineId && (
                        <div className="availability-badge">
                            System available: <strong>{availableQty}</strong> units
                        </div>
                    )}

                    <div className="form-group">
                        <label>Quantity {availableQty > 0 && `(max ${availableQty})`}</label>
                        <input type="number" min="1" max={availableQty || undefined}
                            value={form.quantity}
                            disabled={!form.medicineId || availableQty === 0}
                            onChange={e => setForm(f => ({...f, quantity: e.target.value}))} required />
                    </div>

                    <button type="submit" className="btn btn-primary"
                        disabled={!form.userId || !form.medicineId || !form.quantity || availableQty === 0}>
                        Allocate to User
                    </button>
                </form>
            </div>
        </div>
    );
}
