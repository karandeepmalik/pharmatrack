import React,{useState,useEffect} from 'react';
import {Link} from 'react-router-dom';
import * as api from '../../api/api';
export default function MyTransactions(){
    const[txs,setTxs]=useState([]);
    const[loading,setLoading]=useState(true);
    const[error,setError]=useState(null);
    const[filter,setFilter]=useState('ALL');
    useEffect(()=>{
        api.getMyTransactions()
            .then(r=>setTxs(Array.isArray(r.data)?r.data:[]))
            .catch(()=>setError('Failed to load transactions. Please try again.'))
            .finally(()=>setLoading(false));
    },[]);
    const filtered=filter==='ALL'?txs:txs.filter(t=>t.status===filter);
    if(loading) return <div className="loading">Loading…</div>;
    return(<div className="page">
        <div className="page-header">
            <h1>Medicine Dispatch History</h1>
            <Link to="/user/dashboard" className="btn btn-secondary">← Back</Link>
        </div>
        {error&&<div role="alert" className="alert alert-error">{error}</div>}
        <div className="filter-tabs" role="group">
            {['ALL','PENDING','APPROVED','REJECTED'].map(s=>(
                <button key={s} type="button" className={`filter-tab ${filter===s?'active':''}`} onClick={()=>setFilter(s)}>{s}</button>
            ))}
        </div>
        {filtered.length===0?<p className="empty-message">No transactions found.</p>:(
            <div className="transactions-list">{filtered.map(tx=>{
                const status=tx.status??'UNKNOWN';
                const specLabel=tx.medicineType==='VIAL'
                    ?`${tx.concentrationMgPerMl??tx.specification??'?'} mg/ml`
                    :`${tx.specification??'?'} mg`;
                return(
                <div key={tx.id} className={`transaction-card status-${status.toLowerCase()}`}>
                    <div className="tx-header"><span>#{tx.id}</span><span className={`badge badge-${status.toLowerCase()}`}>{status}</span></div>
                    <p><strong>Medicine:</strong> {tx.medicineName??'Unknown'} ({tx.medicineType??'Unknown'}, {specLabel})</p>
                    <p><strong>Quantity:</strong> {tx.quantity}</p>
                    <p><strong>Note:</strong> {tx.notes}</p>
                    <p><strong>Submitted:</strong> {tx.submittedAt?new Date(tx.submittedAt).toLocaleString():'—'}</p>
                    {tx.screenshots?.length>0&&<p><strong>Payment screenshot:</strong> attached ✓</p>}
                </div>
                );
            })}</div>
        )}
    </div>);
}
