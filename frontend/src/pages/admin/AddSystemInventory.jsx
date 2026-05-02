import React, {useState, useEffect} from 'react';
import * as api from '../../api/api';

const specLabel = (type, spec) => type === 'VIAL' ? `${spec} mg/ml` : `${spec} mg`;

export default function AddSystemInventory() {
    const [medicines, setMedicines] = useState([]);
    const [systemInv, setSystemInv] = useState([]);
    const [form, setForm]           = useState({medicineId: '', quantity: ''});
    const [msg, setMsg]             = useState('');
    const [err, setErr]             = useState('');

    useEffect(() => {
        api.getMedicines().then(r => setMedicines(r.data));
        api.getSystemInventory().then(r => setSystemInv(r.data));
    }, []);

    const selectedSysInv = systemInv.find(s => String(s.medicineId) === form.medicineId);

    const handle = async e => {
        e.preventDefault();
        setMsg(''); setErr('');
        try {
            await api.addSystemInventory({
                medicineId: Number(form.medicineId),
                quantity:   Number(form.quantity),
            });
            setMsg('System inventory updated successfully.');
            setForm({medicineId: '', quantity: ''});
            api.getSystemInventory().then(r => setSystemInv(r.data));
        } catch(ex) {
            setErr(ex.response?.data?.message || 'Failed to update system inventory');
        }
    };

    return (
        <div className="page">
            <h1>Add System Inventory</h1>
            <p className="page-subtitle">Add new stock to the system inventory pool.</p>

            {msg && <div role="alert" className="alert alert-success">{msg}</div>}
            {err && <div role="alert" className="alert alert-error">{err}</div>}

            <div className="form-card">
                <form onSubmit={handle}>
                    <div className="form-group">
                        <label>Medicine</label>
                        <select value={form.medicineId}
                            onChange={e => setForm(f => ({...f, medicineId: e.target.value, quantity: ''}))} required>
                            <option value="">-- Select Medicine --</option>
                            {medicines.map(m => (
                                <option key={m.id} value={m.id}>
                                    {m.name} – {m.type} {specLabel(m.type, m.specification)}
                                </option>
                            ))}
                        </select>
                    </div>

                    {form.medicineId && (
                        <div className="availability-badge">
                            Current system stock: <strong>{selectedSysInv?.quantity ?? 0}</strong> units
                        </div>
                    )}

                    <div className="form-group">
                        <label>Quantity to Add</label>
                        <input type="number" min="1" value={form.quantity}
                            disabled={!form.medicineId}
                            onChange={e => setForm(f => ({...f, quantity: e.target.value}))} required />
                    </div>

                    <button type="submit" className="btn btn-primary"
                        disabled={!form.medicineId || !form.quantity}>
                        Add to System Inventory
                    </button>
                </form>
            </div>

            {systemInv.length > 0 && (
                <div className="form-section" style={{marginTop: '2rem'}}>
                    <h2>Current System Inventory</h2>
                    <div className="table-wrapper">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>Medicine</th>
                                    <th>Type</th>
                                    <th>Specification</th>
                                    <th>Pharma</th>
                                    <th>In Stock</th>
                                </tr>
                            </thead>
                            <tbody>
                                {systemInv.map(i => (
                                    <tr key={i.id}>
                                        <td>{i.medicineName}</td>
                                        <td>{i.medicineType}</td>
                                        <td>{i.specification} {i.specUnit}</td>
                                        <td>{i.pharmaName}</td>
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
