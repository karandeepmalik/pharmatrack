import React, { createRef } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import ScreenshotUpload from '../../components/ScreenshotUpload';
import { SCREENSHOT_CONSTRAINTS } from '../../constants';

const defaultProps = {
  fileInputRef: createRef(),
  screenshotPreview: null,
  screenshotError: '',
  screenshotFile: null,
  onFileChange: jest.fn(),
  onRemove: jest.fn(),
};

const renderComponent = (overrides = {}) =>
  render(<ScreenshotUpload {...defaultProps} {...overrides} />);

describe('ScreenshotUpload component', () => {
  beforeEach(() => jest.clearAllMocks());

  // ── Renders ───────────────────────────────────────────────────────────
  test('renders file input with correct aria-label', () => {
    renderComponent();
    expect(screen.getByLabelText(/upload payment screenshot/i)).toBeInTheDocument();
  });

  test('renders "optional" label text', () => {
    renderComponent();
    expect(screen.getByText(/optional/i)).toBeInTheDocument();
  });

  test(`renders hint mentioning max size (${SCREENSHOT_CONSTRAINTS.MAX_LABEL})`, () => {
    renderComponent();
    expect(screen.getByText(new RegExp(SCREENSHOT_CONSTRAINTS.MAX_LABEL))).toBeInTheDocument();
  });

  test('file input has correct accept attribute', () => {
    renderComponent();
    const input = screen.getByLabelText(/upload payment screenshot/i);
    expect(input).toHaveAttribute('accept', SCREENSHOT_CONSTRAINTS.ACCEPT_ATTR);
  });

  // ── No preview ────────────────────────────────────────────────────────
  test('does not render preview img when screenshotPreview is null', () => {
    renderComponent();
    expect(screen.queryByAltText(/payment screenshot preview/i)).not.toBeInTheDocument();
  });

  test('does not render remove button when no preview', () => {
    renderComponent();
    expect(screen.queryByRole('button', { name: /remove screenshot/i }))
      .not.toBeInTheDocument();
  });

  // ── With preview ──────────────────────────────────────────────────────
  test('renders preview image when screenshotPreview is provided', () => {
    renderComponent({ screenshotPreview: 'data:image/png;base64,abc' });
    expect(screen.getByAltText(/payment screenshot preview/i)).toBeInTheDocument();
  });

  test('preview image src matches screenshotPreview prop', () => {
    const preview = 'data:image/png;base64,abc123';
    renderComponent({ screenshotPreview: preview });
    expect(screen.getByAltText(/payment screenshot preview/i))
      .toHaveAttribute('src', preview);
  });

  test('renders remove button when preview is present', () => {
    renderComponent({ screenshotPreview: 'data:image/png;base64,x' });
    expect(screen.getByRole('button', { name: /remove screenshot/i })).toBeInTheDocument();
  });

  test('calls onRemove when remove button is clicked', () => {
    const onRemove = jest.fn();
    renderComponent({ screenshotPreview: 'data:image/png;base64,x', onRemove });
    fireEvent.click(screen.getByRole('button', { name: /remove screenshot/i }));
    expect(onRemove).toHaveBeenCalledTimes(1);
  });

  test('renders filename when screenshotFile is provided', () => {
    const file = new File(['x'], 'proof.png', { type: 'image/png' });
    renderComponent({
      screenshotPreview: 'data:image/png;base64,x',
      screenshotFile: file,
    });
    expect(screen.getByText('proof.png')).toBeInTheDocument();
  });

  // ── Error state ───────────────────────────────────────────────────────
  test('renders error message with role="alert" when screenshotError set', () => {
    renderComponent({ screenshotError: 'Only PNG, JPEG images allowed.' });
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Only PNG, JPEG images allowed.');
  });

  test('does not render error when screenshotError is empty', () => {
    renderComponent({ screenshotError: '' });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  test('input has aria-describedby when error is present', () => {
    renderComponent({ screenshotError: 'Bad file' });
    const input = screen.getByLabelText(/upload payment screenshot/i);
    expect(input).toHaveAttribute('aria-describedby', 'screenshot-error');
  });

  test('input has no aria-describedby when no error', () => {
    renderComponent({ screenshotError: '' });
    const input = screen.getByLabelText(/upload payment screenshot/i);
    expect(input).not.toHaveAttribute('aria-describedby');
  });

  // ── Callbacks ─────────────────────────────────────────────────────────
  test('calls onFileChange when file is selected', () => {
    const onFileChange = jest.fn();
    renderComponent({ onFileChange });
    fireEvent.change(screen.getByLabelText(/upload payment screenshot/i), {
      target: { files: [new File(['x'], 'a.png', { type: 'image/png' })] },
    });
    expect(onFileChange).toHaveBeenCalledTimes(1);
  });
});
