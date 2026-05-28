import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import UserDashboard from '../../../pages/user/UserDashboard';
import { useAuth } from '../../../context/AuthContext';

jest.mock('../../../context/AuthContext');

const mockLogout = jest.fn();

const renderPage = (fullName = 'John Doe') => {
  useAuth.mockReturnValue({
    user: { username: 'john.doe', fullName, role: 'USER' },
    logout: mockLogout,
  });
  return render(
    <MemoryRouter>
      <UserDashboard />
    </MemoryRouter>
  );
};

beforeEach(() => jest.clearAllMocks());

// ── Render ────────────────────────────────────────────────────────────────

describe('UserDashboard — render', () => {
  test('renders Dashboard heading', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /dashboard/i })).toBeInTheDocument();
  });

  test('shows welcome message with user full name', () => {
    renderPage('Maria Garcia');
    expect(screen.getByText(/welcome, maria garcia/i)).toBeInTheDocument();
  });

  test('renders Sign Out button', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument();
  });
});

// ── Navigation cards ──────────────────────────────────────────────────────

describe('UserDashboard — navigation cards', () => {
  test('has link to submit medicine dispatch', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /submit medicine dispatch/i }))
      .toHaveAttribute('href', '/user/submit');
  });

  test('has link to dispatch history', () => {
    renderPage();
    expect(screen.getByRole('link', { name: /medicine dispatch history/i }))
      .toHaveAttribute('href', '/user/transactions');
  });
});

// ── Sign out ──────────────────────────────────────────────────────────────

describe('UserDashboard — sign out', () => {
  test('calls logout when Sign Out is clicked', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: /sign out/i }));
    expect(mockLogout).toHaveBeenCalledTimes(1);
  });
});
