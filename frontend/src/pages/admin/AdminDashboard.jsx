import React from 'react';
import {Link} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
export default function AdminDashboard(){
    const{user,logout}=useAuth();
    return(<div className="page"><div className="page-header"><h1>Admin Dashboard</h1><button onClick={logout} className="btn btn-secondary">Sign Out</button></div>
        <p>Welcome, {user?.fullName}</p>
        <div className="nav-cards">
            <Link to="/admin/add-inventory" className="nav-card"><h2>📦 Add Inventory</h2><p>Assign stock to users</p></Link>
            <Link to="/admin/transactions" className="nav-card"><h2>✅ Review Adjustments</h2><p>Approve or reject pending requests</p></Link>
        </div>
    </div>);
}
