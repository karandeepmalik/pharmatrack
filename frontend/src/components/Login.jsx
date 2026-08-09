import React,{useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import * as api from '../api/api';

// A failed login call can fail for reasons that have nothing to do with the credentials being
// wrong — a Cloud Run cold start, a transient network blip, the rate limiter — and reporting all
// of those as "Invalid username or password" is misleading (the user did nothing wrong) and was
// a real, reported bug. Only an actual 401 from the login endpoint means bad credentials.
function loginErrorMessage(err){
    const status = err.response?.status;
    if(status===401) return 'Invalid username or password';
    if(status===429) return err.response?.data || 'Too many failed login attempts. Please try again in a few minutes.';
    if(status===undefined) return 'Unable to reach the server. Please check your connection and try again.';
    return 'Something went wrong while signing in. Please try again.';
}

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
        }catch(err){ setError(loginErrorMessage(err)); }
        finally{ setLoading(false); }
    };
    return(<div className="login-page"><div className="login-card">
        <h1 className="login-title">PharmaTrack</h1>
        <p className="login-subtitle">MedicineStock Management</p>
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
