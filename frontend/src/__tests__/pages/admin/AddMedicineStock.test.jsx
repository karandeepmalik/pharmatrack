import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AddMedicineStock from '../../../pages/admin/AddMedicineStock';

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/admin/add-medicine-stock']}>
      <Routes>
        <Route path="/admin/add-medicine-stock" element={<AddMedicineStock />} />
        <Route path="/admin/modify-medicine-stock" element={<div>Modify Medicine Stock Page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('AddMedicineStock — redirect', () => {
  test('redirects to /admin/modify-medicine-stock', () => {
    renderPage();
    expect(screen.getByText('Modify Medicine Stock Page')).toBeInTheDocument();
  });

  test('does not render any content of its own', () => {
    renderPage();
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
