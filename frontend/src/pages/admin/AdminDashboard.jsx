import React from 'react';
import {Link} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
export default function AdminDashboard(){
    const{user,logout}=useAuth();
    return(<div className="page"><div className="page-header"><h1>Admin Dashboard</h1><button onClick={logout} className="btn btn-secondary">Sign Out</button></div>
        <p>Welcome, {user?.fullName}</p>
        <div className="nav-cards">
            <Link to="/admin/inventory" className="nav-card"><h2>📋 View Inventory</h2><p>See all user stock levels</p></Link>
            <Link to="/admin/add-system-inventory" className="nav-card"><h2>📦 Add Inventory</h2><p>Add stock to the system</p></Link>
            <Link to="/admin/add-inventory" className="nav-card"><h2>🔄 Add Inventory to User</h2><p>Allocate system stock to a user</p></Link>
            <Link to="/admin/transactions" className="nav-card"><h2>✅ Review Adjustments</h2><p>Approve or reject pending requests</p></Link>
            <Link to="/admin/users" className="nav-card"><h2>👥 Manage Users</h2><p>Add users and manage access</p></Link>
        </div>
    </div>);
}
