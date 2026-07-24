import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ReduceSystemInventory from '../../../pages/admin/ReduceSystemInventory';

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/admin/reduce-system-inventory']}>
      <Routes>
        <Route path="/admin/reduce-system-inventory" element={<ReduceSystemInventory />} />
        <Route path="/admin/dashboard" element={<div>Admin Dashboard Page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('ReduceSystemInventory — redirect', () => {
  test('redirects to /admin/dashboard', () => {
    renderPage();
    expect(screen.getByText('Admin Dashboard Page')).toBeInTheDocument();
  });

  test('does not render any content of its own', () => {
    renderPage();
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
