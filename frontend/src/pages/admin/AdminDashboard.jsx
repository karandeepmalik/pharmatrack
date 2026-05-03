import React from 'react';
import {Link} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
export default function AdminDashboard(){
    const{user,logout}=useAuth();
    return(<div className="page"><div className="page-header"><h1>Admin Dashboard</h1><button onClick={logout} className="btn btn-secondary">Sign Out</button></div>
        <p>Welcome, {user?.fullName}</p>
        <div className="nav-cards">
            <Link to="/admin/transactions" className="nav-card"><h2>✅ Review Adjustments</h2><p>Approve or reject pending requests</p></Link>
            <Link to="/admin/inventory" className="nav-card"><h2>📋 View Available Inventory</h2><p>See all user stock levels</p></Link>
            <Link to="/admin/modify-inventory" className="nav-card"><h2>🔄 Modify Inventory</h2><p>Add or reduce user inventory</p></Link>
            <Link to="/admin/users" className="nav-card"><h2>👥 Manage Users</h2><p>Add users, manage access and passwords</p></Link>
        </div>
    </div>);
}
