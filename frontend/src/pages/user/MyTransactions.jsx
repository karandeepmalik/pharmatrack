import React,{useState,useEffect} from 'react';
import {Link} from 'react-router-dom';
import * as api from '../../api/api';
export default function MyTransactions(){
    const[txs,setTxs]=useState([]);
    const[loading,setLoading]=useState(true);
    const[filter,setFilter]=useState('ALL');
    useEffect(()=>{ api.getMyTransactions().then(r=>setTxs(r.data)).finally(()=>setLoading(false)); },[]);
    const filtered=filter==='ALL'?txs:txs.filter(t=>t.status===filter);
    if(loading) return <div className="loading">Loading…</div>;
    return(<div className="page">
        <div className="page-header">
            <h1>Transactions History</h1>
            <Link to="/user/dashboard" className="btn btn-secondary">← Back</Link>
        </div>
        <div className="filter-tabs" role="group">
            {['ALL','PENDING','APPROVED','REJECTED'].map(s=>(
                <button key={s} type="button" className={`filter-tab ${filter===s?'active':''}`} onClick={()=>setFilter(s)}>{s}</button>
            ))}
        </div>
        {filtered.length===0?<p className="empty-message">No transactions found.</p>:(
            <div className="transactions-list">{filtered.map(tx=>(
                <div key={tx.id} className={`transaction-card status-${tx.status.toLowerCase()}`}>
                    <div className="tx-header"><span>#{tx.id}</span><span className={`badge badge-${tx.status.toLowerCase()}`}>{tx.status}</span></div>
                    <p><strong>Medicine:</strong> {tx.medicineName} ({tx.medicineType}, {tx.specification} {tx.medicineType==='VIAL'?'mg/ml':'mg (10 Tablets)'})</p>
                    <p><strong>Quantity:</strong> {tx.quantity}</p>
                    <p><strong>Note:</strong> {tx.notes}</p>
                    <p><strong>Submitted:</strong> {new Date(tx.submittedAt).toLocaleString()}</p>
                    {tx.paymentScreenshot&&<p><strong>Payment screenshot:</strong> attached ✓</p>}
                </div>
            ))}</div>
        )}
    </div>);
}
