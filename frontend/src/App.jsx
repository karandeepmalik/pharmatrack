import React from 'react';
import {BrowserRouter,Routes,Route,Navigate} from 'react-router-dom';
import {AuthProvider} from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './components/Login';
import AdminDashboard from './pages/admin/AdminDashboard';
import AddInventory from './pages/admin/AddInventory';
import ApproveTransactions from './pages/admin/ApproveTransactions';
import UserDashboard from './pages/user/UserDashboard';
import SubmitTransaction from './pages/user/SubmitTransaction';
import MyTransactions from './pages/user/MyTransactions';
import './styles/index.css';
export default function App(){
    return(<AuthProvider><BrowserRouter><Routes>
        <Route path="/login" element={<Login/>}/>
        <Route path="/admin/dashboard" element={<ProtectedRoute adminOnly><AdminDashboard/></ProtectedRoute>}/>
        <Route path="/admin/add-inventory" element={<ProtectedRoute adminOnly><AddInventory/></ProtectedRoute>}/>
        <Route path="/admin/transactions" element={<ProtectedRoute adminOnly><ApproveTransactions/></ProtectedRoute>}/>
        <Route path="/user/dashboard" element={<ProtectedRoute><UserDashboard/></ProtectedRoute>}/>
        <Route path="/user/submit" element={<ProtectedRoute><SubmitTransaction/></ProtectedRoute>}/>
        <Route path="/user/transactions" element={<ProtectedRoute><MyTransactions/></ProtectedRoute>}/>
        <Route path="/" element={<Navigate to="/login" replace/>}/>
        <Route path="*" element={<Navigate to="/login" replace/>}/>
    </Routes></BrowserRouter></AuthProvider>);
}
