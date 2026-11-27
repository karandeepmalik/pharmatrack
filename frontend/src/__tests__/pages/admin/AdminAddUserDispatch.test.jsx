import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import AdminAddUserDispatch from '../../../pages/admin/AdminAddUserDispatch';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const USERS = [
  { id: 2, username: 'john.doe', fullName: 'John Doe', role: 'USER', active: true },
  { id: 3, username: 'jane.smith', fullName: 'Jane Smith', role: 'USER', active: true },
  { id: 1, username: 'admin', fullName: 'Admin', role: 'ADMIN', active: true },
];

const johnStock = [
  { pharmaId: 1, pharmaName: 'Shield FX', medicineId: 1, medicineName: 'Shield FX Vial',
    medicineType: 'VIAL', specification: 10, quantity: 50, specUnit: 'mg/ml', price: 4000,
    medicineStockType: 'REGULAR_MEDICINE_STOCK' },
];

const janeStock = [
  { pharmaId: 2, pharmaName: 'MediCure', medicineId: 2, medicineName: 'MediCure Tablet',
    medicineType: 'TABLET', specification: 25, quantity: 15, specUnit: 'mg (10 Tablets)', price: 1500,
    medicineStockType: 'REGULAR_MEDICINE_STOCK' },
];

beforeEach(() => {
  jest.clearAllMocks();
  sessionStorage.clear();
  api.getUsers.mockResolvedValue({ data: USERS });
  api.getAvailableMedicineStockForUser.mockImplementation((userId) =>
    Promise.resolve({ data: String(userId) === '2' ? johnStock : janeStock })
  );
});

const renderPage = () => render(<MemoryRouter><AdminAddUserDispatch /></MemoryRouter>);

const attachScreenshot = async (filename = 'pay.png') => {
  const file = new File(['png'], filename, { type: 'image/png' });
  const readerMock = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,x' };
  jest.spyOn(global, 'FileReader').mockImplementation(() => readerMock);
  fireEvent.change(screen.getByLabelText(/upload payment screenshot/i),
    { target: { files: [file] } });
  readerMock.onloadend();
  await waitFor(() => screen.getByAltText(/payment screenshot 1 preview/i));
  return file;
};

describe('AdminAddUserDispatch — render', () => {
  test('renders page heading and back link to admin dashboard', async () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /add user dispatches/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /← back/i })).toHaveAttribute('href', '/admin/dashboard');
  });

  test('renders a User selector populated from getUsers, excluding ADMIN role', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('option', { name: /john doe \(john\.doe\)/i }));
    expect(screen.getByRole('option', { name: /jane smith \(jane\.smith\)/i })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /admin \(admin\)/i })).not.toBeInTheDocument();
  });

  test('shows a placeholder and no dispatch form before a user is selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/^user$/i));
    expect(screen.getByText(/select a user above/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/pharma company/i)).not.toBeInTheDocument();
  });
});

describe('AdminAddUserDispatch — selecting a user', () => {
  test('selecting a user fetches that user\'s available stock via the admin endpoint', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/^user$/i));
    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), '2');

    await waitFor(() => expect(api.getAvailableMedicineStockForUser).toHaveBeenCalledWith('2'));
    await waitFor(() => screen.getByLabelText(/pharma company/i));
  });

  test('shows the selected user\'s name in the form heading', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/^user$/i));
    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), '2');
    await waitFor(() => screen.getByRole('heading', { name: /adding a dispatch for john doe/i }));
  });

  test('switching to a different user refetches stock and resets any in-progress selections', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/^user$/i));
    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), '2');
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    expect(screen.getByLabelText(/pharma company/i)).toHaveValue('1');

    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), '3');

    await waitFor(() => expect(api.getAvailableMedicineStockForUser).toHaveBeenCalledWith('3'));
    await waitFor(() => screen.getByRole('heading', { name: /adding a dispatch for jane smith/i }));
    // Remounted form (key={selectedUserId}) — pharma selection must not have survived the switch.
    expect(screen.getByLabelText(/pharma company/i)).toHaveValue('');
  });
});

describe('AdminAddUserDispatch — submitting on behalf of a user', () => {
  test('a completed submission calls submitTransactionForUser with the selected userId and form payload', async () => {
    api.submitTransactionForUser.mockResolvedValue({ data: {} });
    renderPage();
    await waitFor(() => screen.getByLabelText(/^user$/i));
    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), '2');
    await waitFor(() => screen.getByLabelText(/pharma company/i));

    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.selectOptions(screen.getByLabelText(/volume \(ml\)|specification/i), '10');
    await userEvent.type(screen.getByLabelText(/quantity/i), '5');
    await userEvent.type(screen.getByLabelText(/medicine dispatch note/i),
      'Dispatched to clinic B for FIP treatment');
    await attachScreenshot();

    await userEvent.click(screen.getByRole('button', { name: /add dispatch/i }));

    await waitFor(() => expect(api.submitTransactionForUser).toHaveBeenCalledWith(
      '2',
      expect.objectContaining({ medicineId: 1, quantity: 5, notes: 'Dispatched to clinic B for FIP treatment' })
    ));
  });

  test('shows a success message naming the target user after submission', async () => {
    api.submitTransactionForUser.mockResolvedValue({ data: {} });
    renderPage();
    await waitFor(() => screen.getByLabelText(/^user$/i));
    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), '2');
    await waitFor(() => screen.getByLabelText(/pharma company/i));

    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.selectOptions(screen.getByLabelText(/volume \(ml\)|specification/i), '10');
    await userEvent.type(screen.getByLabelText(/quantity/i), '5');
    await userEvent.type(screen.getByLabelText(/medicine dispatch note/i),
      'Dispatched to clinic B for FIP treatment');
    await attachScreenshot();
    await userEvent.click(screen.getByRole('button', { name: /add dispatch/i }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/john doe/i));
  });

  test('shows the API error message when submission fails', async () => {
    api.submitTransactionForUser.mockRejectedValue({
      response: { data: { message: 'Insufficient medicine stock available' } },
    });
    renderPage();
    await waitFor(() => screen.getByLabelText(/^user$/i));
    await userEvent.selectOptions(screen.getByLabelText(/^user$/i), '2');
    await waitFor(() => screen.getByLabelText(/pharma company/i));

    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.selectOptions(screen.getByLabelText(/volume \(ml\)|specification/i), '10');
    await userEvent.type(screen.getByLabelText(/quantity/i), '5');
    await userEvent.type(screen.getByLabelText(/medicine dispatch note/i),
      'Dispatched to clinic B for FIP treatment');
    await attachScreenshot();
    await userEvent.click(screen.getByRole('button', { name: /add dispatch/i }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/insufficient medicine stock available/i));
  });
});
