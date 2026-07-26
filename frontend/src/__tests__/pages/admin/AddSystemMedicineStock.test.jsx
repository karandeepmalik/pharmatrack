import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AddSystemMedicineStock from '../../../pages/admin/AddSystemMedicineStock';

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/admin/add-system-medicine-stock']}>
      <Routes>
        <Route path="/admin/add-system-medicine-stock" element={<AddSystemMedicineStock />} />
        <Route path="/admin/dashboard" element={<div>Admin Dashboard Page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('AddSystemMedicineStock — redirect', () => {
  test('redirects to /admin/dashboard', () => {
    renderPage();
    expect(screen.getByText('Admin Dashboard Page')).toBeInTheDocument();
  });

  test('does not render any content of its own', () => {
    renderPage();
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
