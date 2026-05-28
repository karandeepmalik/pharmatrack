import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ProtectedRoute from '../../components/ProtectedRoute';
import { useAuth } from '../../context/AuthContext';

jest.mock('../../context/AuthContext');

const renderWithRoute = (element, initialPath = '/protected') =>
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/protected" element={element} />
        <Route path="/login" element={<div>Login Page</div>} />
        <Route path="/user/dashboard" element={<div>User Dashboard</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('ProtectedRoute — unauthenticated', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ user: null, isAdmin: false });
  });

  test('redirects to /login when user is not authenticated', () => {
    renderWithRoute(
      <ProtectedRoute>
        <div>Protected Content</div>
      </ProtectedRoute>
    );
    expect(screen.getByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  test('redirects admin-only route to /login when unauthenticated', () => {
    renderWithRoute(
      <ProtectedRoute adminOnly>
        <div>Admin Content</div>
      </ProtectedRoute>
    );
    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });
});

describe('ProtectedRoute — authenticated USER', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ user: { username: 'john.doe', role: 'USER' }, isAdmin: false });
  });

  test('renders children when user is authenticated and route is not admin-only', () => {
    renderWithRoute(
      <ProtectedRoute>
        <div>User Content</div>
      </ProtectedRoute>
    );
    expect(screen.getByText('User Content')).toBeInTheDocument();
  });

  test('redirects to /user/dashboard when non-admin accesses adminOnly route', () => {
    renderWithRoute(
      <ProtectedRoute adminOnly>
        <div>Admin Content</div>
      </ProtectedRoute>
    );
    expect(screen.getByText('User Dashboard')).toBeInTheDocument();
    expect(screen.queryByText('Admin Content')).not.toBeInTheDocument();
  });
});

describe('ProtectedRoute — authenticated ADMIN', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ user: { username: 'admin', role: 'ADMIN' }, isAdmin: true });
  });

  test('renders children for regular authenticated route', () => {
    renderWithRoute(
      <ProtectedRoute>
        <div>Admin Content</div>
      </ProtectedRoute>
    );
    expect(screen.getByText('Admin Content')).toBeInTheDocument();
  });

  test('renders children for adminOnly route when user is admin', () => {
    renderWithRoute(
      <ProtectedRoute adminOnly>
        <div>Admin Only Content</div>
      </ProtectedRoute>
    );
    expect(screen.getByText('Admin Only Content')).toBeInTheDocument();
  });
});
