import { Navigate } from 'react-router-dom';
// System medicineStock removed — redirect to admin dashboard
export default function ReduceSystemMedicineStock() { return <Navigate to="/admin/dashboard" replace />; }
