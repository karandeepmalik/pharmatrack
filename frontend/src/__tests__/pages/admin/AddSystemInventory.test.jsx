import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AddSystemInventory from '../../../pages/admin/AddSystemInventory';

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/admin/add-system-inventory']}>
      <Routes>
        <Route path="/admin/add-system-inventory" element={<AddSystemInventory />} />
        <Route path="/admin/dashboard" element={<div>Admin Dashboard Page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('AddSystemInventory — redirect', () => {
  test('redirects to /admin/dashboard', () => {
    renderPage();
    expect(screen.getByText('Admin Dashboard Page')).toBeInTheDocument();
  });

  test('does not render any content of its own', () => {
    renderPage();
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
