import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SubmitTransaction from '../../../pages/user/SubmitTransaction';
import * as api from '../../../api/api';

jest.mock('../../../api/api');

const mockInventory = [
  { pharmaId: 1, pharmaName: 'FIP Shield', medicineId: 1, medicineName: 'FIP Shield Vial',
    medicineType: 'VIAL', specification: 10, quantity: 50 },
  { pharmaId: 1, pharmaName: 'FIP Shield', medicineId: 2, medicineName: 'FIP Shield Tablet',
    medicineType: 'TABLET', specification: 50, quantity: 30 },
  { pharmaId: 2, pharmaName: 'MediCure', medicineId: 3, medicineName: 'MediCure Vial',
    medicineType: 'VIAL', specification: 20, quantity: 10 },
];

beforeEach(() => {
  jest.clearAllMocks();
  api.getAvailableInventory.mockResolvedValue({ data: mockInventory });
});

const renderPage = () => render(<SubmitTransaction />);

/** Fill the form to a valid state ready to submit */
const fillValidForm = async () => {
  renderPage();
  await waitFor(() => screen.getByLabelText(/pharma company/i));
  await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
  await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
  await userEvent.selectOptions(screen.getByLabelText(/specification/i), '10');
  await userEvent.type(screen.getByLabelText(/quantity/i), '5');
  await userEvent.type(screen.getByLabelText(/adjustment note/i),
    'Dispatched to clinic B for FIP treatment');
};

// ── Loading & render ───────────────────────────────────────────────────

describe('Initial render', () => {
  test('shows loading indicator while inventory is fetching', () => {
    api.getAvailableInventory.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText(/loading inventory/i)).toBeInTheDocument();
  });

  test('renders all form fields after inventory loads', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    expect(screen.getByLabelText(/medicine type/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/specification/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/quantity/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/adjustment note/i)).toBeInTheDocument();
  });

  test('submit button is disabled initially', async () => {
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /submit adjustment/i }));
    expect(screen.getByRole('button', { name: /submit adjustment/i })).toBeDisabled();
  });

  test('shows error if inventory fetch fails', async () => {
    api.getAvailableInventory.mockRejectedValue(new Error('Network error'));
    renderPage();
    await waitFor(() => screen.getByRole('alert'));
    expect(screen.getByRole('alert')).toHaveTextContent(/failed to load inventory/i);
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
    await waitFor(() => screen.getByLabelText(/specification/i));
    expect(screen.getByLabelText(/specification/i)).toBeDisabled();
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

    // Change pharma → type should reset
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '2');
    expect(screen.getByLabelText(/medicine type/i)).toHaveValue('');
  });

  test('spec select resets when type changes', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.selectOptions(screen.getByLabelText(/specification/i), '10');
    expect(screen.getByLabelText(/specification/i)).toHaveValue('10');

    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'TABLET');
    expect(screen.getByLabelText(/specification/i)).toHaveValue('');
  });

  test('shows max quantity hint when item is selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    await userEvent.selectOptions(screen.getByLabelText(/pharma company/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/medicine type/i), 'VIAL');
    await userEvent.selectOptions(screen.getByLabelText(/specification/i), '10');
    expect(screen.getByText(/max 50/i)).toBeInTheDocument();
  });
});

// ── Notes character counter ─────────────────────────────────────────────

describe('Notes field', () => {
  test('shows character counter', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/adjustment note/i));
    expect(screen.getByText(/0\/500/)).toBeInTheDocument();
  });

  test('counter updates as user types', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/adjustment note/i));
    await userEvent.type(screen.getByLabelText(/adjustment note/i), 'Hello');
    expect(screen.getByText(/5\/500/)).toBeInTheDocument();
  });
});

// ── Screenshot upload (via ScreenshotUpload component) ─────────────────

describe('Screenshot upload section', () => {
  test('renders upload input labelled as optional', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/upload payment screenshot/i));
    expect(screen.getByText(/optional/i)).toBeInTheDocument();
  });

  test('shows preview after valid PNG file selected', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/upload payment screenshot/i));

    const file = new File(['png'], 'pay.png', { type: 'image/png' });
    const readerMock = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,x' };
    jest.spyOn(global, 'FileReader').mockImplementation(() => readerMock);

    fireEvent.change(screen.getByLabelText(/upload payment screenshot/i),
      { target: { files: [file] } });
    readerMock.onloadend();

    await waitFor(() =>
      expect(screen.getByAltText(/payment screenshot preview/i)).toBeInTheDocument()
    );
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

    const file = new File(['x'], 'pay.png', { type: 'image/png' });
    const readerMock = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,x' };
    jest.spyOn(global, 'FileReader').mockImplementation(() => readerMock);
    fireEvent.change(screen.getByLabelText(/upload payment screenshot/i),
      { target: { files: [file] } });
    readerMock.onloadend();

    await waitFor(() => screen.getByAltText(/payment screenshot preview/i));
    await userEvent.click(screen.getByRole('button', { name: /remove screenshot/i }));
    await waitFor(() =>
      expect(screen.queryByAltText(/payment screenshot preview/i)).not.toBeInTheDocument()
    );
  });
});

// ── Preview card (via TransactionPreview component) ────────────────────

describe('Preview card', () => {
  test('does not show preview before form is complete', async () => {
    renderPage();
    await waitFor(() => screen.getByLabelText(/pharma company/i));
    expect(screen.queryByRole('region', { name: /submission preview/i }))
      .not.toBeInTheDocument();
  });

  test('shows preview once all required fields are filled', async () => {
    await fillValidForm();
    await waitFor(() =>
      expect(screen.getByRole('region', { name: /submission preview/i })).toBeInTheDocument()
    );
  });

  test('preview shows medicine name', async () => {
    await fillValidForm();
    await waitFor(() => screen.getByRole('region', { name: /submission preview/i }));
    expect(screen.getByText(/FIP Shield Vial/)).toBeInTheDocument();
  });

  test('preview shows screenshot filename when file attached', async () => {
    await fillValidForm();
    const file = new File(['x'], 'proof.png', { type: 'image/png' });
    const readerMock = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,x' };
    jest.spyOn(global, 'FileReader').mockImplementation(() => readerMock);
    fireEvent.change(screen.getByLabelText(/upload payment screenshot/i),
      { target: { files: [file] } });
    readerMock.onloadend();
    await waitFor(() => expect(screen.getByText(/proof\.png/)).toBeInTheDocument());
  });
});

// ── Submit ─────────────────────────────────────────────────────────────

describe('Form submission', () => {
  test('submit button enables when form is valid', async () => {
    await fillValidForm();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /submit adjustment/i })).not.toBeDisabled()
    );
  });

  test('calls submitTransaction with correct params', async () => {
    api.submitTransaction.mockResolvedValue({ data: { id: 1, status: 'PENDING' } });
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /submit adjustment/i }));
    await waitFor(() =>
      expect(api.submitTransaction).toHaveBeenCalledWith(
        expect.objectContaining({ medicineId: 1, quantity: 5, notes: expect.any(String) })
      )
    );
  });

  test('calls submitTransaction with screenshotFile when file selected', async () => {
    api.submitTransaction.mockResolvedValue({ data: { id: 1, status: 'PENDING' } });
    await fillValidForm();

    const file = new File(['x'], 'pay.png', { type: 'image/png' });
    const readerMock = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,x' };
    jest.spyOn(global, 'FileReader').mockImplementation(() => readerMock);
    fireEvent.change(screen.getByLabelText(/upload payment screenshot/i),
      { target: { files: [file] } });
    readerMock.onloadend();

    await userEvent.click(screen.getByRole('button', { name: /submit adjustment/i }));
    await waitFor(() =>
      expect(api.submitTransaction).toHaveBeenCalledWith(
        expect.objectContaining({ screenshotFile: file })
      )
    );
  });

  test('shows success message after submission', async () => {
    api.submitTransaction.mockResolvedValue({ data: { id: 1, status: 'PENDING' } });
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /submit adjustment/i }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/submitted successfully/i)
    );
  });

  test('shows API error message on failure', async () => {
    api.submitTransaction.mockRejectedValue({
      response: { data: { message: 'Insufficient inventory: requested 5 but only 3 available' } },
    });
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /submit adjustment/i }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/insufficient inventory/i)
    );
  });

  test('resets form fields after successful submission', async () => {
    api.submitTransaction.mockResolvedValue({ data: { id: 1, status: 'PENDING' } });
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /submit adjustment/i }));
    await waitFor(() => screen.getByRole('alert'));
    expect(screen.getByLabelText(/pharma company/i)).toHaveValue('');
  });
});
