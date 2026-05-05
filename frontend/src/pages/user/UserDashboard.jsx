import React from 'react';
import {Link} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
export default function UserDashboard(){
    const{user,logout}=useAuth();
    return(<div className="page"><div className="page-header"><h1>Dashboard</h1><button onClick={logout} className="btn btn-secondary">Sign Out</button></div>
        <p>Welcome, {user?.fullName}</p>
        <div className="nav-cards">
            <Link to="/user/submit" className="nav-card"><h2>📋 Submit Medicine Dispatch</h2><p>Request a stock dispatch</p></Link>
            <Link to="/user/transactions" className="nav-card"><h2>📊 Medicine Dispatch History</h2><p>View your submission history</p></Link>
        </div>
    </div>);
}
