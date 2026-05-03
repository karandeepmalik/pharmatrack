import { Navigate } from 'react-router-dom';
// Replaced by ModifyInventory — retained for any lingering bookmarks
export default function AddInventory() { return <Navigate to="/admin/modify-inventory" replace />; }
