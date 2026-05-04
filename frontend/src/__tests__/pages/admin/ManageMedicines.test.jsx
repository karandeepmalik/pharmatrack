import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ManageMedicines from '../../../pages/admin/ManageMedicines';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const mockCompanies = [
  { id: 1, name: 'Shield FX', description: 'FIP treatment supplier' },
  { id: 2, name: 'MediCure', description: null },
];

const mockMedicines = [
  { id: 1, name: 'Shield FX Vial 10 ml', type: 'VIAL', specification: 10.0,
    concentrationMgPerMl: 20.0, price: 4000, pharmaCompany: { id: 1, name: 'Shield FX' } },
  { id: 2, name: 'Shield FX Tablet 25 mg', type: 'TABLET', specification: 25.0,
    concentrationMgPerMl: null, price: 4000, pharmaCompany: { id: 1, name: 'Shield FX' } },
];

beforeEach(() => {
  jest.clearAllMocks();
  api.getMedicines.mockResolvedValue({ data: mockMedicines });
  api.getPharmaCompanies.mockResolvedValue({ data: mockCompanies });
});

const renderPage = () =>
  render(<MemoryRouter><ManageMedicines /></MemoryRouter>);

// ── Initial render ────────────────────────────────────────────────────────

describe('ManageMedicines — render', () => {
  test('renders page heading', async () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /manage medicines/i })).toBeInTheDocument();
  });

  test('renders Back link to admin dashboard', async () => {
    renderPage();
    expect(screen.getByRole('link', { name: /← back/i })).toHaveAttribute('href', '/admin/dashboard');
  });

  test('renders Add Pharma Company section heading', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /add pharma company/i })).toBeInTheDocument();
  });

  test('renders pharma company name input', () => {
    renderPage();
    expect(screen.getByLabelText(/company name/i)).toBeInTheDocument();
  });

  test('renders pharma company description input', () => {
    renderPage();
    expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
  });

  test('renders Add Medicine section heading', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: /add medicine/i })).toBeInTheDocument();
  });

  test('renders medicine form fields', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    expect(screen.getByLabelText(/medicine name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/medicine type/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/specification/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/price/i)).toBeInTheDocument();
  });

  test('renders existing medicines table', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('heading', { name: /existing medicines/i }));
    expect(screen.getByRole('heading', { name: /existing medicines/i })).toBeInTheDocument();
  });

  test('shows medicine names in table', async () => {
    renderPage();
    await waitFor(() => screen.getByText('Shield FX Vial 10 ml'));
    expect(screen.getByText('Shield FX Vial 10 ml')).toBeInTheDocument();
    expect(screen.getByText('Shield FX Tablet 25 mg')).toBeInTheDocument();
  });
});

// ── Add Pharma Company ────────────────────────────────────────────────────

describe('ManageMedicines — add pharma company', () => {
  test('Add Pharma Company button disabled when name is empty', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /add pharma company/i })).toBeDisabled();
  });

  test('Add Pharma Company button enabled when name is filled', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText(/company name/i), 'New Co');
    expect(screen.getByRole('button', { name: /add pharma company/i })).not.toBeDisabled();
  });

  test('submits pharma company and shows success message', async () => {
    api.createPharmaCompany.mockResolvedValue({ data: { id: 3, name: 'New Co' } });
    api.getMedicines.mockResolvedValue({ data: [] });
    api.getPharmaCompanies.mockResolvedValue({ data: [] });

    renderPage();
    await userEvent.type(screen.getByLabelText(/company name/i), 'New Co');
    await userEvent.type(screen.getByLabelText(/description/i), 'A new pharma');
    await userEvent.click(screen.getByRole('button', { name: /add pharma company/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/new co.*created successfully/i)
    );
    expect(api.createPharmaCompany).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'New Co', description: 'A new pharma' })
    );
  });

  test('shows error message when API fails', async () => {
    api.createPharmaCompany.mockRejectedValue({
      response: { data: { message: 'Name already exists' } },
    });
    renderPage();
    await userEvent.type(screen.getByLabelText(/company name/i), 'Duplicate');
    await userEvent.click(screen.getByRole('button', { name: /add pharma company/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/name already exists/i)
    );
  });
});

// ── Add Medicine ──────────────────────────────────────────────────────────

describe('ManageMedicines — add medicine', () => {
  const fillMedicineForm = async () => {
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.type(screen.getByLabelText(/medicine name/i), 'Shield FX Vial 10 ml');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.type(screen.getByLabelText(/specification/i), '10');
    await userEvent.type(screen.getByLabelText(/price/i), '4000');
  };

  test('Add Medicine button disabled until all required fields filled', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /add medicine/i }));
    expect(screen.getByRole('button', { name: /add medicine/i })).toBeDisabled();
  });

  test('concentration field appears when VIAL type selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine type/i));
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    expect(screen.getByLabelText(/concentration/i)).toBeInTheDocument();
  });

  test('concentration field does not appear for TABLET type', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine type/i));
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'TABLET');
    expect(screen.queryByLabelText(/concentration/i)).not.toBeInTheDocument();
  });

  test('submits medicine and shows success message', async () => {
    api.createMedicine.mockResolvedValue({ data: { id: 3, name: 'Shield FX Vial 10 ml' } });
    api.getMedicines.mockResolvedValue({ data: [] });
    api.getPharmaCompanies.mockResolvedValue({ data: mockCompanies });

    renderPage();
    await fillMedicineForm();
    await userEvent.click(screen.getByRole('button', { name: /add medicine/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/shield fx vial 10 ml.*created successfully/i)
    );
    expect(api.createMedicine).toHaveBeenCalledWith(
      expect.objectContaining({
        pharmaCompanyId: 1,
        name: 'Shield FX Vial 10 ml',
        type: 'VIAL',
        specification: 10,
        price: 4000,
      })
    );
  });

  test('shows error message when API fails', async () => {
    api.createMedicine.mockRejectedValue({
      response: { data: { message: 'Pharma company not found' } },
    });
    renderPage();
    await fillMedicineForm();
    await userEvent.click(screen.getByRole('button', { name: /add medicine/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/pharma company not found/i)
    );
  });
});
