import { Navigate } from 'react-router-dom';
// System inventory removed — redirect to admin dashboard
export default function AddSystemInventory() { return <Navigate to="/admin/dashboard" replace />; }
