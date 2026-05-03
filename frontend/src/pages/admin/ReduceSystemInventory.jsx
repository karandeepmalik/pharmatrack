import React, {useState, useEffect} from 'react';
import * as api from '../../api/api';

const specLabel = (type, spec) => type === 'VIAL' ? `${spec} mg/ml` : `${spec} mg`;

export default function ReduceSystemInventory() {
    const [systemInv, setSystemInv] = useState([]);
    const [selected, setSelected]   = useState('');
    const [quantity, setQuantity]   = useState('');
    const [msg, setMsg]             = useState('');
    const [err, setErr]             = useState('');
    const [loading, setLoading]     = useState(false);

    const reload = () => api.getSystemInventory().then(r => setSystemInv(r.data));

    useEffect(() => { reload(); }, []);

    const selectedItem = systemInv.find(s => String(s.medicineId) === selected);

    const handleReduce = async e => {
        e.preventDefault();
        setMsg(''); setErr('');
        setLoading(true);
        try {
            await api.reduceSystemInventory(selected, {quantity: Number(quantity)});
            setMsg('System inventory reduced successfully.');
            setQuantity('');
            reload();
        } catch(ex) {
            const status = ex.response?.status;
            if (status === 409) {
                setErr(`Insufficient stock. Available: ${selectedItem?.quantity ?? 0} units.`);
            } else {
                setErr(ex.response?.data?.message || 'Failed to reduce system inventory.');
            }
        } finally { setLoading(false); }
    };

    const handleClear = async () => {
        if (!selected) return;
        if (!window.confirm(`Zero out all stock for ${selectedItem?.medicineName}?`)) return;
        setMsg(''); setErr('');
        setLoading(true);
        try {
            await api.clearSystemInventory(selected);
            setMsg('System inventory cleared to zero.');
            setQuantity('');
            reload();
        } catch(ex) {
            setErr(ex.response?.data?.message || 'Failed to clear system inventory.');
        } finally { setLoading(false); }
    };

    return (
        <div className="page">
            <h1>Modify System Inventory</h1>
            <p className="page-subtitle">Reduce stock or zero-out system inventory for a medicine.</p>

            {msg && <div role="alert" className="alert alert-success">{msg}</div>}
            {err && <div role="alert" className="alert alert-error">{err}</div>}

            <div className="form-card">
                <div className="form-group">
                    <label>Medicine</label>
                    <select value={selected}
                        onChange={e => { setSelected(e.target.value); setQuantity(''); setMsg(''); setErr(''); }} required>
                        <option value="">-- Select Medicine --</option>
                        {systemInv.map(s => (
                            <option key={s.medicineId} value={s.medicineId}>
                                {s.medicineName} – {s.medicineType} {specLabel(s.medicineType, s.specification)}
                            </option>
                        ))}
                    </select>
                </div>

                {selected && (
                    <div className="availability-badge">
                        Current system stock: <strong>{selectedItem?.quantity ?? 0}</strong> units
                    </div>
                )}

                <form onSubmit={handleReduce} style={{marginTop: '1rem'}}>
                    <div className="form-group">
                        <label>Quantity to Remove</label>
                        <input type="number" min="1" max={selectedItem?.quantity || undefined}
                            value={quantity} disabled={!selected || loading}
                            onChange={e => setQuantity(e.target.value)} required />
                    </div>
                    <div style={{display: 'flex', gap: '1rem', flexWrap: 'wrap'}}>
                        <button type="submit" className="btn btn-primary"
                            disabled={!selected || !quantity || loading}>
                            Reduce Stock
                        </button>
                        <button type="button" className="btn btn-secondary"
                            disabled={!selected || loading}
                            onClick={handleClear}>
                            Clear All Stock
                        </button>
                    </div>
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
