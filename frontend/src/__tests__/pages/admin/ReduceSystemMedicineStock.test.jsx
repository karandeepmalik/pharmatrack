import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ReduceSystemMedicineStock from '../../../pages/admin/ReduceSystemMedicineStock';

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/admin/reduce-system-medicine-stock']}>
      <Routes>
        <Route path="/admin/reduce-system-medicine-stock" element={<ReduceSystemMedicineStock />} />
        <Route path="/admin/dashboard" element={<div>Admin Dashboard Page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('ReduceSystemMedicineStock — redirect', () => {
  test('redirects to /admin/dashboard', () => {
    renderPage();
    expect(screen.getByText('Admin Dashboard Page')).toBeInTheDocument();
  });

  test('does not render any content of its own', () => {
    renderPage();
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
