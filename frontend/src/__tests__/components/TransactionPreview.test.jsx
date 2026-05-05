import React from 'react';
import { render, screen } from '@testing-library/react';
import TransactionPreview from '../../components/TransactionPreview';

const item = {
  medicineName: 'FIP Shield Vial',
  medicineType: 'VIAL',
  specification: 10,
};

describe('TransactionPreview component', () => {
  test('renders nothing when item is null', () => {
    const { container } = render(
      <TransactionPreview item={null} quantity="5" notes="Valid note" screenshotCount={0} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  test('renders nothing when quantity is empty', () => {
    const { container } = render(
      <TransactionPreview item={item} quantity="" notes="Valid note" screenshotCount={0} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  test('renders nothing when notes are too short', () => {
    const { container } = render(
      <TransactionPreview item={item} quantity="5" notes="Hi" screenshotCount={0} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  test('renders preview region when all required props provided', () => {
    render(
      <TransactionPreview item={item} quantity="5" notes="Valid note here" screenshotCount={0} />
    );
    expect(screen.getByRole('region', { name: /submission preview/i })).toBeInTheDocument();
  });

  test('shows medicine name, type, and specification', () => {
    render(
      <TransactionPreview item={item} quantity="5" notes="Valid note here" screenshotCount={0} />
    );
    expect(screen.getByText(/FIP Shield Vial/)).toBeInTheDocument();
    expect(screen.getByText(/VIAL/)).toBeInTheDocument();
    expect(screen.getByText(/10/)).toBeInTheDocument();
  });

  test('shows quantity', () => {
    render(
      <TransactionPreview item={item} quantity="7" notes="Valid note here" screenshotCount={0} />
    );
    expect(screen.getByText(/7/)).toBeInTheDocument();
  });

  test('shows notes text', () => {
    render(
      <TransactionPreview item={item} quantity="5" notes="Dispatched to clinic B" screenshotCount={0} />
    );
    expect(screen.getByText(/Dispatched to clinic B/)).toBeInTheDocument();
  });

  test('shows screenshot count when screenshots are attached', () => {
    render(
      <TransactionPreview item={item} quantity="5" notes="Valid note here" screenshotCount={2} />
    );
    expect(screen.getByText(/2 attached/i)).toBeInTheDocument();
  });

  test('does not show screenshot line when screenshotCount is 0', () => {
    render(
      <TransactionPreview item={item} quantity="5" notes="Valid note here" screenshotCount={0} />
    );
    expect(screen.queryByText(/Payment Screenshot/)).not.toBeInTheDocument();
  });

  test('renders at exact minimum notes length (5 chars)', () => {
    render(
      <TransactionPreview item={item} quantity="1" notes="Hello" screenshotCount={0} />
    );
    expect(screen.getByRole('region')).toBeInTheDocument();
  });
});
