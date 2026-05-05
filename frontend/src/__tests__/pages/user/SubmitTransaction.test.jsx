import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import SubmitTransaction from '../../../pages/user/SubmitTransaction';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const mockInventory = [
  { pharmaId: 1, pharmaName: 'FIP Shield', medicineId: 1, medicineName: 'FIP Shield Vial',
    medicineType: 'VIAL', specification: 10, quantity: 50, specUnit: 'mg/ml', price: 4000,
    inventoryType: 'REGULAR_MEDICINE_STOCK' },
  { pharmaId: 1, pharmaName: 'FIP Shield', medicineId: 2, medicineName: 'FIP Shield Tablet',
    medicineType: 'TABLET', specification: 50, quantity: 30, specUnit: 'mg (10 Tablets)', price: 8000,
    inventoryType: 'REGULAR_MEDICINE_STOCK' },
  { pharmaId: 2, pharmaName: 'MediCure', medicineId: 3, medicineName: 'MediCure Vial',
    medicineType: 'VIAL', specification: 20, quantity: 10, specUnit: 'mg/ml', price: 2000,
    inventoryType: 'REGULAR_MEDICINE_STOCK' },
];

beforeEach(() => {
  jest.clearAllMocks();
  api.getAvailableInventory.mockResolvedValue({ data: mockInventory });
});

const renderPage = () => render(<MemoryRouter><SubmitTransaction /></MemoryRouter>);

/** Attach a valid PNG screenshot to the upload input */
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

/** Fill the form to a fully valid state including mandatory screenshot */
const fillValidForm = async () => {
  renderPage();
  await waitFor(() => screen.getByLabelText(/pharma company/i));
  await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
  await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
  await userEvent.selectOptions(screen.getByLabelText(/volume \(ml\)|specification/i), '10');
  await userEvent.type(screen.getByLabelText(/quantity/i), '5');
  await userEvent.type(screen.getByLabelText(/medicine movement note/i),
    'Dispatched to clinic B for FIP treatment');
  await attachScreenshot();
};

// ── Loading & render ───────────────────────────────────────────────────

describe('Initial render', () => {
  test('shows loading indicator while inventory is fetching', () => {
    api.getAvailableInventory.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText(/loading stock/i)).toBeInTheDocument();
  });

  test('renders page heading as Submit Medicine Movement', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('heading', { name: /submit medicine movement/i }));
    expect(screen.getByRole('heading', { name: /submit medicine movement/i })).toBeInTheDocument();
  });

  test('renders back button linking to user dashboard', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('link', { name: /← back/i }));
    expect(screen.getByRole('link', { name: /← back/i })).toHaveAttribute('href', '/user/dashboard');
  });

  test('renders all form fields after inventory loads', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    expect(screen.getByLabelText(/medicine type/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/volume \(ml\)|specification/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/quantity/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/medicine movement note/i)).toBeInTheDocument();
  });

  test('submit button is disabled initially', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /submit medicine movement/i }));
    expect(screen.getByRole('button', { name: /submit medicine movement/i })).toBeDisabled();
  });

  test('shows error if inventory fetch fails', async () => {
    api.getAvailableInventory.mockRejectedValue(new Error('Network error'));
    renderPage();
    await waitFor(() => screen.getByRole('alert'));
    expect(screen.getByRole('alert')).toHaveTextContent(/failed to load stock/i);
  });
});

// ── Cascading selects ───────────────────────────────────────────────────

describe('Cascading select behaviour', () => {
  test('type select is disabled until pharma is selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine type/i));
    expect(screen.getByLabelText(/medicine type/i)).toBeDisabled();
  });

  test('spec select is disabled until type is selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/volume \(ml\)|specification/i));
    expect(screen.getByLabelText(/volume \(ml\)|specification/i)).toBeDisabled();
  });

  test('quantity input is disabled until spec is selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/quantity/i));
    expect(screen.getByLabelText(/quantity/i)).toBeDisabled();
  });

  test('type select resets when pharma changes', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    expect(screen.getByLabelText(/medicine type/i)).toHaveValue('VIAL');

    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '2');
    expect(screen.getByLabelText(/medicine type/i)).toHaveValue('');
  });

  test('spec select resets when type changes', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.selectOptions(screen.getByLabelText(/volume \(ml\)|specification/i), '10');

    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'TABLET');
    expect(screen.getByLabelText(/volume \(ml\)|specification/i)).toHaveValue('');
  });

  test('shows max quantity hint when item is selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.selectOptions(screen.getByLabelText(/volume \(ml\)|specification/i), '10');
    expect(screen.getByText(/max 50/i)).toBeInTheDocument();
  });

  test('tablet spec label shows mg (10 Tablets)', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'TABLET');
    expect(screen.getByText(/specification.*mg \(10 tablets\)/i)).toBeInTheDocument();
  });
});

// ── Notes character counter ─────────────────────────────────────────────

describe('Notes field', () => {
  test('shows character counter', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine movement note/i));
    expect(screen.getByText(/0\/500/)).toBeInTheDocument();
  });

  test('counter updates as user types', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/medicine movement note/i));
    await userEvent.type(screen.getByLabelText(/medicine movement note/i), 'Hello');
    expect(screen.getByText(/5\/500/)).toBeInTheDocument();
  });
});

// ── Screenshot upload — now mandatory ─────────────────────────────────

describe('Screenshot upload section — mandatory', () => {
  test('renders screenshot upload marked as required', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/upload payment screenshot/i));
    expect(screen.queryByText(/optional/i)).not.toBeInTheDocument();
  });

  test('submit button stays disabled when screenshot is missing even if other fields valid', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.selectOptions(screen.getByLabelText(/volume \(ml\)|specification/i), '10');
    await userEvent.type(screen.getByLabelText(/quantity/i), '5');
    await userEvent.type(screen.getByLabelText(/medicine movement note/i),
      'Dispatched to clinic B for FIP treatment');
    // No screenshot attached
    expect(screen.getByRole('button', { name: /submit medicine movement/i })).toBeDisabled();
  });

  test('shows preview after valid PNG file selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/upload payment screenshot/i));
    await attachScreenshot();
    expect(screen.getByAltText(/payment screenshot 1 preview/i)).toBeInTheDocument();
  });

  test('shows error for non-image file', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/upload payment screenshot/i));
    fireEvent.change(screen.getByLabelText(/upload payment screenshot/i),
      { target: { files: [new File(['x'], 'bad.pdf', { type: 'application/pdf' })] } });
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/PNG, JPEG/i));
  });

  test('remove button clears preview', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/upload payment screenshot/i));
    await attachScreenshot();
    await userEvent.click(screen.getByRole('button', { name: /remove screenshot 1/i }));
    await waitFor(() =>
      expect(screen.queryByRole('img')).not.toBeInTheDocument()
    );
  });
});

// ── Preview card ───────────────────────────────────────────��──────────

describe('Preview card', () => {
  test('does not show preview before form is complete', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    expect(screen.queryByRole('region', { name: /submission preview/i }))
      .not.toBeInTheDocument();
  });

  test('shows preview once all required fields are filled including screenshot', async () => {
    await fillValidForm();
    await waitFor(() =>
      expect(screen.getByRole('region', { name: /submission preview/i })).toBeInTheDocument()
    );
  });

  test('preview shows screenshot count when file attached', async () => {
    await fillValidForm();
    await waitFor(() => screen.getByText(/1 attached/i));
    expect(screen.getByText(/1 attached/i)).toBeInTheDocument();
  });
});

// ── Price override ─────────────────────────────────────────────────────

describe('Price override input', () => {
  test('price input appears after spec selected and is pre-filled with item price', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.selectOptions(screen.getByLabelText(/volume \(ml\)|specification/i), '10');

    const priceInput = screen.getByLabelText(/price per unit/i);
    expect(priceInput).toBeInTheDocument();
    expect(priceInput).toHaveValue(4000);
  });

  test('price input does not appear before spec is selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    // No spec selected yet
    expect(screen.queryByLabelText(/price per unit/i)).not.toBeInTheDocument();
  });

  test('submit call includes pricePerUnit when user changes price', async () => {
    api.submitTransaction.mockResolvedValue({ data: { id: 1, status: 'PENDING' } });
    await fillValidForm();

    const priceInput = screen.getByLabelText(/price per unit/i);
    await userEvent.clear(priceInput);
    await userEvent.type(priceInput, '3500');

    await userEvent.click(screen.getByRole('button', { name: /submit medicine movement/i }));
    await waitFor(() =>
      expect(api.submitTransaction).toHaveBeenCalledWith(
        expect.objectContaining({
          pricePerUnit: 3500,
        })
      )
    );
  });
});

// ── Inventory type selector ────────────────────────────────────────────

describe('Inventory type selector', () => {
  test('renders stock type select with Regular and Admin Medicine Stock options', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/stock type/i));
    const selector = screen.getByLabelText(/stock type/i);
    expect(selector).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /regular medicine stock/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /admin medicine stock/i })).toBeInTheDocument();
  });

  test('defaults to Regular Medicine Stock type', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/stock type/i));
    expect(screen.getByLabelText(/stock type/i)).toHaveValue('REGULAR_MEDICINE_STOCK');
  });

  test('stock type selector appears before pharma company selector', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/stock type/i));
    const invTypeSelect = screen.getByLabelText(/stock type/i);
    const pharmaSelect  = screen.getByLabelText(/pharma company/i);
    const position = invTypeSelect.compareDocumentPosition(pharmaSelect);
    // DOCUMENT_POSITION_FOLLOWING = 4, meaning pharmaSelect comes after invTypeSelect
    expect(position & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  test('changing stock type resets pharma, type, spec and quantity', async () => {
    const adminInventory = [
      { pharmaId: 1, pharmaName: 'FIP Shield', medicineId: 1, medicineName: 'FIP Shield Vial',
        medicineType: 'VIAL', specification: 10, quantity: 20, specUnit: 'mg/ml', price: 4000,
        inventoryType: 'ADMIN_MEDICINE_STOCK' },
    ];
    api.getAvailableInventory.mockResolvedValueOnce({ data: mockInventory });
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));

    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    expect(screen.getByLabelText(/medicine type/i)).toHaveValue('VIAL');

    // Switch to Admin Medicine Stock
    api.getAvailableInventory.mockResolvedValue({ data: adminInventory });
    await userEvent.selectOptions(screen.getByLabelText(/stock type/i), 'ADMIN_MEDICINE_STOCK');
    expect(screen.getByLabelText(/pharma company/i)).toHaveValue('');
  });

  test('submit call includes inventoryType', async () => {
    api.submitTransaction.mockResolvedValue({ data: { id: 1, status: 'PENDING' } });
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /submit medicine movement/i }));
    await waitFor(() =>
      expect(api.submitTransaction).toHaveBeenCalledWith(
        expect.objectContaining({
          inventoryType: 'REGULAR_MEDICINE_STOCK',
        })
      )
    );
  });
});

// ── Submit ─────────────────────────────────────────────────────────────

describe('Form submission', () => {
  test('submit button enables when form is fully valid with screenshot', async () => {
    await fillValidForm();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /submit medicine movement/i })).not.toBeDisabled()
    );
  });

  test('calls submitTransaction with correct params including screenshotFiles', async () => {
    api.submitTransaction.mockResolvedValue({ data: { id: 1, status: 'PENDING' } });
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /submit medicine movement/i }));
    await waitFor(() =>
      expect(api.submitTransaction).toHaveBeenCalledWith(
        expect.objectContaining({
          medicineId: 1,
          quantity: 5,
          notes: expect.any(String),
          screenshotFiles: expect.arrayContaining([expect.any(File)]),
        })
      )
    );
  });

  test('shows success message after submission', async () => {
    api.submitTransaction.mockResolvedValue({ data: { id: 1, status: 'PENDING' } });
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /submit medicine movement/i }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/submitted successfully/i)
    );
  });

  test('shows API error message on failure', async () => {
    api.submitTransaction.mockRejectedValue({
      response: { data: { message: 'Insufficient inventory: requested 5 but only 3 available' } },
    });
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /submit medicine movement/i }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/insufficient inventory/i)
    );
  });

  test('resets form fields after successful submission', async () => {
    api.submitTransaction.mockResolvedValue({ data: { id: 1, status: 'PENDING' } });
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /submit medicine movement/i }));
    await waitFor(() => screen.getByRole('alert'));
    expect(screen.getByLabelText(/pharma company/i)).toHaveValue('');
  });
});
