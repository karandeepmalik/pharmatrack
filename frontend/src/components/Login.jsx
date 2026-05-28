import React,{useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import * as api from '../api/api';
export default function Login(){
    const[form,setForm]=useState({username:'',password:''});
    const[error,setError]=useState('');
    const[loading,setLoading]=useState(false);
    const{login}=useAuth();
    const navigate=useNavigate();
    const handle=async e=>{
        e.preventDefault(); setError(''); setLoading(true);
        try{
            const{data}=await api.login(form);
            login({username:data.username,fullName:data.fullName,role:data.role},data.token);
            navigate(data.role==='ADMIN'?'/admin/dashboard':'/user/dashboard');
        }catch{ setError('Invalid username or password'); }
        finally{ setLoading(false); }
    };
    return(<div className="login-page"><div className="login-card">
        <h1 className="login-title">PharmaTrack</h1>
        <p className="login-subtitle">Inventory Management</p>
        {error&&<div role="alert" className="alert alert-error">{error}</div>}
        <form onSubmit={handle}>
            <div className="form-group"><label htmlFor="username">Username</label>
                <input id="username" type="text" value={form.username} onChange={e=>setForm(f=>({...f,username:e.target.value}))} placeholder="Enter your username" required/></div>
            <div className="form-group"><label htmlFor="password">Password</label>
                <input id="password" type="password" value={form.password} onChange={e=>setForm(f=>({...f,password:e.target.value}))} placeholder="Enter your password" required/></div>
            <button type="submit" disabled={loading} className="btn btn-primary btn-full">{loading?'Signing in…':'Sign In'}</button>
        </form>
    </div></div>);
}
