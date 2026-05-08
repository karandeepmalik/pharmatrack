import React from 'react';
import {BrowserRouter,Routes,Route,Navigate} from 'react-router-dom';
import {AuthProvider} from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './components/Login';
import AdminDashboard from './pages/admin/AdminDashboard';
import ModifyInventory from './pages/admin/ModifyInventory';
import ApproveTransactions from './pages/admin/ApproveTransactions';
import AdminInventory from './pages/admin/AdminInventory';
import ManageUsers from './pages/admin/ManageUsers';
import ViewReports from './pages/admin/ViewReports';
import ViewPastTransactions from './pages/admin/ViewPastTransactions';
import ManageMedicines from './pages/admin/ManageMedicines';
import AdminEditDispatch from './pages/admin/AdminEditDispatch';
import UserDashboard from './pages/user/UserDashboard';
import SubmitTransaction from './pages/user/SubmitTransaction';
import MyTransactions from './pages/user/MyTransactions';
import './styles/index.css';
export default function App(){
    return(<AuthProvider><BrowserRouter><Routes>
        <Route path="/login" element={<Login/>}/>
        <Route path="/admin/dashboard" element={<ProtectedRoute adminOnly><AdminDashboard/></ProtectedRoute>}/>
        <Route path="/admin/modify-inventory" element={<ProtectedRoute adminOnly><ModifyInventory/></ProtectedRoute>}/>
        <Route path="/admin/transactions" element={<ProtectedRoute adminOnly><ApproveTransactions/></ProtectedRoute>}/>
        <Route path="/admin/inventory" element={<ProtectedRoute adminOnly><AdminInventory/></ProtectedRoute>}/>
        <Route path="/admin/users" element={<ProtectedRoute adminOnly><ManageUsers/></ProtectedRoute>}/>
        <Route path="/admin/reports" element={<ProtectedRoute adminOnly><ViewReports/></ProtectedRoute>}/>
        <Route path="/admin/past-transactions" element={<ProtectedRoute adminOnly><ViewPastTransactions/></ProtectedRoute>}/>
        <Route path="/admin/medicines" element={<ProtectedRoute adminOnly><ManageMedicines/></ProtectedRoute>}/>
        <Route path="/admin/dispatch-records" element={<ProtectedRoute adminOnly><AdminEditDispatch/></ProtectedRoute>}/>
        <Route path="/user/dashboard" element={<ProtectedRoute><UserDashboard/></ProtectedRoute>}/>
        <Route path="/user/submit" element={<ProtectedRoute><SubmitTransaction/></ProtectedRoute>}/>
        <Route path="/user/transactions" element={<ProtectedRoute><MyTransactions/></ProtectedRoute>}/>
        <Route path="/" element={<Navigate to="/login" replace/>}/>
        <Route path="*" element={<Navigate to="/login" replace/>}/>
    </Routes></BrowserRouter></AuthProvider>);
}
