import { Navigate } from 'react-router-dom';
// System medicineStock removed — redirect to admin dashboard
export default function AddSystemMedicineStock() { return <Navigate to="/admin/dashboard" replace />; }
