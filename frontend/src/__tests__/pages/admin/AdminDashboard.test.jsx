import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import AdminDashboard from '../../../pages/admin/AdminDashboard';
import { useAuth } from '../../../context/AuthContext';

jest.mock('../../../context/AuthContext');

const mockLogout = jest.fn();

const renderPage = (fullName = 'Admin User') => {
  useAuth.mockReturnValue({
    user: { username: 'admin', fullName, role: 'ADMIN' },
    logout: mockLogout,
  });
  return render(
    <MemoryRouter>
      <AdminDashboard />
    </MemoryRouter>
  );
};

beforeEach(() => jest.clearAllMocks());

// ── Render ────────────────────────────────────────────────────────────────

describe('AdminDashboard — render', () => {
  test('renders Admin Dashboard heading', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /admin dashboard/i })).toBeInTheDocument();
  });

  test('shows welcome message with user full name', () => {
    renderPage('Jane Smith');
    expect(screen.getByText(/welcome, jane smith/i)).toBeInTheDocument();
  });

  test('renders Sign Out button', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument();
  });
});

// ── Navigation cards ──────────────────────────────────────────────────────

describe('AdminDashboard — navigation cards', () => {
  test('has link to approve transactions', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /review adjustments/i }))
      .toHaveAttribute('href', '/admin/transactions');
  });

  test('has link to view past transactions', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /view past medicine dispatches/i }))
      .toHaveAttribute('href', '/admin/past-transactions');
  });

  test('has link to view medicine stock', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /view available medicine stock/i }))
      .toHaveAttribute('href', '/admin/inventory');
  });

  test('has link to view reports', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /view reports/i }))
      .toHaveAttribute('href', '/admin/reports');
  });

  test('has link to modify medicine stock', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /modify medicine stock/i }))
      .toHaveAttribute('href', '/admin/modify-inventory');
  });

  test('has link to manage users', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /manage users/i }))
      .toHaveAttribute('href', '/admin/users');
  });

  test('has link to manage medicines', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /manage medicines/i }))
      .toHaveAttribute('href', '/admin/medicines');
  });

  test('has link to modify or delete dispatch records', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /modify or delete a medicine dispatch record/i }))
      .toHaveAttribute('href', '/admin/dispatch-records');
  });

  test('has link to medicine stock modifications history', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /medicine stock modifications history/i }))
      .toHaveAttribute('href', '/admin/inventory-adjustments');
  });
});

// ── Sign out ──────────────────────────────────────────────────────────────

describe('AdminDashboard — sign out', () => {
  test('calls logout when Sign Out is clicked', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /sign out/i }));
    expect(mockLogout).toHaveBeenCalledTimes(1);
  });
});
