import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AddInventory from '../../../pages/admin/AddInventory';

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/admin/add-inventory']}>
      <Routes>
        <Route path="/admin/add-inventory" element={<AddInventory />} />
        <Route path="/admin/modify-inventory" element={<div>Modify Inventory Page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('AddInventory — redirect', () => {
  test('redirects to /admin/modify-inventory', () => {
    renderPage();
    expect(screen.getByText('Modify Inventory Page')).toBeInTheDocument();
  });

  test('does not render any content of its own', () => {
    renderPage();
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
