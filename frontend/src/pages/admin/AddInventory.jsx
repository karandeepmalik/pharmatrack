import React,{useState,useEffect} from 'react';
import * as api from '../../api/api';
export default function AddInventory(){
    const[users,setUsers]=useState([]);
    const[medicines,setMedicines]=useState([]);
    const[form,setForm]=useState({userId:'',medicineId:'',quantity:''});
    const[msg,setMsg]=useState('');
    const[err,setErr]=useState('');
    useEffect(()=>{ api.getUsers().then(r=>setUsers(r.data)); api.getMedicines().then(r=>setMedicines(r.data)); },[]);
    const handle=async e=>{ e.preventDefault(); setMsg(''); setErr('');
        try{ await api.addInventory({userId:Number(form.userId),medicineId:Number(form.medicineId),quantity:Number(form.quantity)});
            setMsg('Inventory added successfully.'); setForm({userId:'',medicineId:'',quantity:''}); }
        catch(ex){ setErr(ex.response?.data?.message||'Failed to add inventory'); }
    };
    return(<div className="page"><h1>Add Inventory</h1>
        {msg&&<div role="alert" className="alert alert-success">{msg}</div>}
        {err&&<div role="alert" className="alert alert-error">{err}</div>}
        <form onSubmit={handle}>
            <div className="form-group"><label>User</label>
                <select value={form.userId} onChange={e=>setForm(f=>({...f,userId:e.target.value}))} required>
                    <option value="">-- Select User --</option>
                    {users.filter(u=>u.role==='USER').map(u=><option key={u.id} value={u.id}>{u.fullName} ({u.username})</option>)}
                </select></div>
            <div className="form-group"><label>Medicine</label>
                <select value={form.medicineId} onChange={e=>setForm(f=>({...f,medicineId:e.target.value}))} required>
                    <option value="">-- Select Medicine --</option>
                    {medicines.map(m=><option key={m.id} value={m.id}>{m.name} – {m.type} {m.specification}mg</option>)}
                </select></div>
            <div className="form-group"><label>Quantity</label>
                <input type="number" min="1" value={form.quantity} onChange={e=>setForm(f=>({...f,quantity:e.target.value}))} required/></div>
            <button type="submit" className="btn btn-primary">Add Inventory</button>
        </form>
    </div>);
}
