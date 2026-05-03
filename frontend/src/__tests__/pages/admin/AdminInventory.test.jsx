import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminInventory from '../../../pages/admin/AdminInventory';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const makeItem = (overrides = {}) => ({
  id: 1,
  username: 'john.doe',
  medicineName: 'Shield FX Vial 10 ml',
  medicineType: 'VIAL',
  specification: 10.0,
  specUnit: 'mg/ml',
  price: 4000,
  pharmaName: 'Shield FX',
  quantity: 50,
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <AdminInventory />
    </MemoryRouter>
  );

beforeEach(() => jest.clearAllMocks());

describe('AdminInventory — render', () => {
  test('shows inventory items', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [makeItem()] });
    renderPage();
    await waitFor(() => expect(screen.getByText('Shield FX Vial 10 ml')).toBeInTheDocument());
    expect(screen.getByText('john.doe')).toBeInTheDocument();
  });

  test('shows empty message when no inventory', async () => {
    api.getAdminInventory.mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/no inventory records found/i)).toBeInTheDocument()
    );
  });

  test('shows error alert when fetch fails', async () => {
    api.getAdminInventory.mockRejectedValue(new Error('Network error'));
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/failed to load inventory/i)
    );
  });
});

describe('AdminInventory — zero quantity filtering', () => {
  test('hides items with quantity 0', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [
        makeItem({ id: 1, medicineName: 'Shield FX Vial 10 ml', quantity: 10 }),
        makeItem({ id: 2, medicineName: 'Shield FX Tablet 25 mg', quantity: 0 }),
      ],
    });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Shield FX Vial 10 ml')).toBeInTheDocument()
    );
    expect(screen.queryByText('Shield FX Tablet 25 mg')).not.toBeInTheDocument();
  });

  test('shows empty message when all items have quantity 0', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [makeItem({ quantity: 0 })],
    });
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/no inventory records found/i)).toBeInTheDocument()
    );
  });

  test('shows items with positive quantity', async () => {
    api.getAdminInventory.mockResolvedValue({
      data: [makeItem({ quantity: 5 })],
    });
    renderPage();
    await waitFor(() => expect(screen.getByText(/shield fx vial 10 ml/i)).toBeInTheDocument());
    expect(screen.getByText('5')).toBeInTheDocument();
  });
});
