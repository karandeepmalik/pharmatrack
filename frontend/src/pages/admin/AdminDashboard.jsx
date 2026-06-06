import React from 'react';
import {Link} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
export default function AdminDashboard(){
    const{user,logout}=useAuth();
    return(<div className="page"><div className="page-header"><h1>Admin Dashboard</h1><button onClick={logout} className="btn btn-secondary">Sign Out</button></div>
        <p>Welcome, {user?.fullName}</p>
        <div className="nav-cards">
            <Link to="/admin/transactions" className="nav-card"><h2>✅ Review Adjustments</h2><p>Approve or reject pending requests</p></Link>
            <Link to="/admin/past-transactions" className="nav-card"><h2>🕐 View Past Medicine Dispatches</h2><p>Browse transaction history by date range</p></Link>
            <Link to="/admin/inventory" className="nav-card"><h2>📋 View Available Medicine Stock</h2><p>See all user stock levels</p></Link>
            <Link to="/admin/reports" className="nav-card"><h2>📊 View Reports</h2><p>Inventory, valuation and sales reports</p></Link>
            <Link to="/admin/modify-inventory" className="nav-card"><h2>🔄 Modify Medicine Stock</h2><p>Add or reduce user medicine stock</p></Link>
            <Link to="/admin/users" className="nav-card"><h2>👥 Manage Users</h2><p>Add users, manage access and passwords</p></Link>
            <Link to="/admin/medicines" className="nav-card"><h2>💊 Manage Medicines</h2><p>Add pharma companies and medicines</p></Link>
            <Link to="/admin/dispatch-records" className="nav-card"><h2>✏️ Modify or Delete a Medicine Dispatch Record</h2><p>Search, edit notes or delete past dispatch records</p></Link>
            <Link to="/admin/inventory-adjustments" className="nav-card"><h2>📝 View Stock Modifications</h2><p>Browse and delete medicine stock adjustment records</p></Link>
        </div>
    </div>);
}
