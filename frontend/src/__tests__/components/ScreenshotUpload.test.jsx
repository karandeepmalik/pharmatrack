import React, { createRef } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import ScreenshotUpload from '../../components/ScreenshotUpload';
import { SCREENSHOT_CONSTRAINTS } from '../../constants';

const makeFile = (name = 'pay.png') => new File(['x'], name, { type: 'image/png' });

const defaultProps = {
  screenshots: [],
  canAddMore: true,
  fileInputRef: createRef(),
  onAdd: jest.fn(),
  onRemove: jest.fn(),
  required: false,
};

const renderComponent = (overrides = {}) =>
  render(<ScreenshotUpload {...defaultProps} {...overrides} />);

describe('ScreenshotUpload component', () => {
  beforeEach(() => jest.clearAllMocks());

  // ── Label / hint ─────────────────────────────────────────────────────
  describe('label and hint', () => {
    test('shows "(optional)" when not required', () => {
      renderComponent();
      expect(screen.getByText(/optional/i)).toBeInTheDocument();
    });

    test('shows required asterisk and hides optional when required prop is true', () => {
      renderComponent({ required: true });
      expect(screen.queryByText(/optional/i)).not.toBeInTheDocument();
      expect(screen.getByText('*')).toBeInTheDocument();
    });

    test(`renders hint mentioning max size (${SCREENSHOT_CONSTRAINTS.MAX_LABEL})`, () => {
      renderComponent();
      expect(screen.getByText(new RegExp(SCREENSHOT_CONSTRAINTS.MAX_LABEL))).toBeInTheDocument();
    });

    test('renders hint mentioning max count (5)', () => {
      renderComponent();
      expect(screen.getByText(/max 5/i)).toBeInTheDocument();
    });
  });

  // ── File input ────────────────────────────────────────────────────────
  describe('file input', () => {
    test('file input accessible via aria-label', () => {
      renderComponent();
      expect(screen.getByLabelText(/upload payment screenshot/i)).toBeInTheDocument();
    });

    test('file input has correct accept attribute', () => {
      renderComponent();
      expect(screen.getByLabelText(/upload payment screenshot/i))
        .toHaveAttribute('accept', SCREENSHOT_CONSTRAINTS.ACCEPT_ATTR);
    });

    test('file input has multiple attribute', () => {
      renderComponent();
      expect(screen.getByLabelText(/upload payment screenshot/i)).toHaveAttribute('multiple');
    });

    test('calls onAdd when file input changes', () => {
      const onAdd = jest.fn();
      renderComponent({ onAdd });
      fireEvent.change(screen.getByLabelText(/upload payment screenshot/i), {
        target: { files: [makeFile()] },
      });
      expect(onAdd).toHaveBeenCalledTimes(1);
    });
  });

  // ── Add button ────────────────────────────────────────────────────────
  describe('add button', () => {
    test('shows "Add Screenshot" button when canAddMore and no valid screenshots', () => {
      renderComponent({ canAddMore: true, screenshots: [] });
      expect(screen.getByRole('button', { name: /add payment screenshot/i })).toBeInTheDocument();
    });

    test('shows "Add Another Screenshot" label when canAddMore and one valid screenshot exists', () => {
      renderComponent({
        canAddMore: true,
        screenshots: [{ file: makeFile(), preview: 'data:image/png;base64,x', error: '' }],
      });
      expect(screen.getByRole('button', { name: /add another payment screenshot/i })).toBeInTheDocument();
    });

    test('does not show add button when canAddMore is false', () => {
      renderComponent({ canAddMore: false });
      expect(screen.queryByRole('button', { name: /add.*screenshot/i })).not.toBeInTheDocument();
    });
  });

  // ── Empty screenshots array ────────────────────────────────────────────
  describe('empty screenshots array', () => {
    test('renders no preview images', () => {
      renderComponent();
      expect(screen.queryByRole('img')).not.toBeInTheDocument();
    });

    test('renders no remove buttons', () => {
      renderComponent();
      expect(screen.queryByRole('button', { name: /remove screenshot/i })).not.toBeInTheDocument();
    });

    test('does not show count label', () => {
      renderComponent();
      expect(screen.queryByText(/screenshot.*attached/i)).not.toBeInTheDocument();
    });
  });

  // ── Valid screenshot entry ─────────────────────────────────────────────
  describe('single valid screenshot entry', () => {
    const validEntry = { file: makeFile('proof.png'), preview: 'data:image/png;base64,abc', error: '' };

    test('renders preview image with correct alt text', () => {
      renderComponent({ screenshots: [validEntry] });
      expect(screen.getByAltText(/payment screenshot 1 preview/i)).toBeInTheDocument();
    });

    test('preview image has correct src', () => {
      renderComponent({ screenshots: [validEntry] });
      expect(screen.getByAltText(/payment screenshot 1 preview/i))
        .toHaveAttribute('src', 'data:image/png;base64,abc');
    });

    test('renders filename', () => {
      renderComponent({ screenshots: [validEntry] });
      expect(screen.getByText('proof.png')).toBeInTheDocument();
    });

    test('renders remove button with index-specific aria-label', () => {
      renderComponent({ screenshots: [validEntry] });
      expect(screen.getByRole('button', { name: /remove screenshot 1/i })).toBeInTheDocument();
    });

    test('calls onRemove with index 0 when remove button clicked', () => {
      const onRemove = jest.fn();
      renderComponent({ screenshots: [validEntry], onRemove });
      fireEvent.click(screen.getByRole('button', { name: /remove screenshot 1/i }));
      expect(onRemove).toHaveBeenCalledWith(0);
    });

    test('shows singular count label', () => {
      renderComponent({ screenshots: [validEntry] });
      expect(screen.getByText(/1 screenshot attached/i)).toBeInTheDocument();
    });
  });

  // ── Error screenshot entry ─────────────────────────────────────────────
  describe('error screenshot entry', () => {
    const errorEntry = { file: null, preview: null, error: 'Only PNG, JPEG images allowed.' };

    test('renders error message with role="alert"', () => {
      renderComponent({ screenshots: [errorEntry] });
      expect(screen.getByRole('alert')).toHaveTextContent('Only PNG, JPEG images allowed.');
    });

    test('does not render preview image for error entry', () => {
      renderComponent({ screenshots: [errorEntry] });
      expect(screen.queryByRole('img')).not.toBeInTheDocument();
    });

    test('error entry does not contribute to attached count', () => {
      renderComponent({ screenshots: [errorEntry] });
      expect(screen.queryByText(/screenshot.*attached/i)).not.toBeInTheDocument();
    });
  });

  // ── Multiple entries ───────────────────────────────────────────────────
  describe('two valid screenshot entries', () => {
    const twoValid = [
      { file: makeFile('a.png'), preview: 'data:image/png;base64,a', error: '' },
      { file: makeFile('b.png'), preview: 'data:image/png;base64,b', error: '' },
    ];

    test('renders two preview images', () => {
      renderComponent({ screenshots: twoValid });
      expect(screen.getAllByRole('img')).toHaveLength(2);
    });

    test('renders two remove buttons', () => {
      renderComponent({ screenshots: twoValid });
      expect(screen.getAllByRole('button', { name: /remove screenshot \d/i })).toHaveLength(2);
    });

    test('calls onRemove with index 1 when second remove button clicked', () => {
      const onRemove = jest.fn();
      renderComponent({ screenshots: twoValid, onRemove });
      fireEvent.click(screen.getByRole('button', { name: /remove screenshot 2/i }));
      expect(onRemove).toHaveBeenCalledWith(1);
    });

    test('shows plural count label', () => {
      renderComponent({ screenshots: twoValid });
      expect(screen.getByText(/2 screenshots attached/i)).toBeInTheDocument();
    });
  });
});
