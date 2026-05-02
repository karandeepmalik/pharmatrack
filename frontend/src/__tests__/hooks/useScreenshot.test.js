import { renderHook, act } from '@testing-library/react';
import useScreenshot from '../../hooks/useScreenshot';
import { SCREENSHOT_CONSTRAINTS } from '../../constants';

const makePngFile = (name = 'pay.png', size = 100) =>
  new File(['x'.repeat(size)], name, { type: 'image/png' });

const makeInputEvent = (file) => ({ target: { files: [file] } });

describe('useScreenshot hook', () => {
  beforeEach(() => jest.clearAllMocks());

  // ── Initial state ────────────────────────────────────────────────────
  describe('initial state', () => {
    test('screenshotFile is null', () => {
      const { result } = renderHook(() => useScreenshot());
      expect(result.current.screenshotFile).toBeNull();
    });
    test('screenshotPreview is null', () => {
      const { result } = renderHook(() => useScreenshot());
      expect(result.current.screenshotPreview).toBeNull();
    });
    test('screenshotError is empty string', () => {
      const { result } = renderHook(() => useScreenshot());
      expect(result.current.screenshotError).toBe('');
    });
    test('fileInputRef is a ref object', () => {
      const { result } = renderHook(() => useScreenshot());
      expect(result.current.fileInputRef).toHaveProperty('current');
    });
  });

  // ── Valid file ────────────────────────────────────────────────────────
  describe('handleScreenshotChange — valid PNG', () => {
    test('sets screenshotFile on valid PNG', () => {
      const { result } = renderHook(() => useScreenshot());
      const file = makePngFile();
      const readerMock = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,x' };
      jest.spyOn(global, 'FileReader').mockImplementation(() => readerMock);

      act(() => result.current.handleScreenshotChange(makeInputEvent(file)));
      expect(result.current.screenshotFile).toBe(file);
    });

    test('clears screenshotError on valid file', () => {
      const { result } = renderHook(() => useScreenshot());
      // First set an error
      act(() => result.current.handleScreenshotChange(
        makeInputEvent(new File(['x'], 'bad.pdf', { type: 'application/pdf' }))
      ));
      expect(result.current.screenshotError).not.toBe('');

      // Then pick a valid file
      const readerMock = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,x' };
      jest.spyOn(global, 'FileReader').mockImplementation(() => readerMock);
      act(() => result.current.handleScreenshotChange(makeInputEvent(makePngFile())));
      expect(result.current.screenshotError).toBe('');
    });

    test('sets screenshotPreview after FileReader loads', () => {
      const { result } = renderHook(() => useScreenshot());
      let capturedReader;
      jest.spyOn(global, 'FileReader').mockImplementation(() => {
        capturedReader = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,abc' };
        return capturedReader;
      });

      act(() => result.current.handleScreenshotChange(makeInputEvent(makePngFile())));
      act(() => capturedReader.onloadend());

      expect(result.current.screenshotPreview).toBe('data:image/png;base64,abc');
    });
  });

  // ── Invalid type ──────────────────────────────────────────────────────
  describe('handleScreenshotChange — invalid MIME type', () => {
    test.each(['application/pdf', 'text/plain', 'video/mp4'])(
      'sets screenshotError for %s',
      (mime) => {
        const { result } = renderHook(() => useScreenshot());
        act(() => result.current.handleScreenshotChange(
          makeInputEvent(new File(['x'], 'bad', { type: mime }))
        ));
        expect(result.current.screenshotError).toMatch(/PNG, JPEG/i);
      }
    );

    test('does not set screenshotFile for invalid type', () => {
      const { result } = renderHook(() => useScreenshot());
      act(() => result.current.handleScreenshotChange(
        makeInputEvent(new File(['x'], 'bad.pdf', { type: 'application/pdf' }))
      ));
      expect(result.current.screenshotFile).toBeNull();
    });
  });

  // ── Oversized file ────────────────────────────────────────────────────
  describe('handleScreenshotChange — oversized file', () => {
    test('sets error when file exceeds MAX_BYTES', () => {
      const { result } = renderHook(() => useScreenshot());
      const big = makePngFile('big.png', 1);
      Object.defineProperty(big, 'size', { value: SCREENSHOT_CONSTRAINTS.MAX_BYTES + 1 });

      act(() => result.current.handleScreenshotChange(makeInputEvent(big)));
      expect(result.current.screenshotError).toMatch(/5 MB/i);
    });

    test('does not set screenshotFile when oversized', () => {
      const { result } = renderHook(() => useScreenshot());
      const big = makePngFile('big.png', 1);
      Object.defineProperty(big, 'size', { value: SCREENSHOT_CONSTRAINTS.MAX_BYTES + 1 });

      act(() => result.current.handleScreenshotChange(makeInputEvent(big)));
      expect(result.current.screenshotFile).toBeNull();
    });
  });

  // ── Empty event ────────────────────────────────────────────────────────
  test('no-ops when event has no files', () => {
    const { result } = renderHook(() => useScreenshot());
    act(() => result.current.handleScreenshotChange({ target: { files: [] } }));
    expect(result.current.screenshotFile).toBeNull();
    expect(result.current.screenshotError).toBe('');
  });

  // ── handleRemoveScreenshot ─────────────────────────────────────────────
  describe('handleRemoveScreenshot', () => {
    test('clears file, preview and error', () => {
      const { result } = renderHook(() => useScreenshot());
      const readerMock = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,x' };
      jest.spyOn(global, 'FileReader').mockImplementation(() => readerMock);

      act(() => result.current.handleScreenshotChange(makeInputEvent(makePngFile())));
      act(() => readerMock.onloadend());
      act(() => result.current.handleRemoveScreenshot());

      expect(result.current.screenshotFile).toBeNull();
      expect(result.current.screenshotPreview).toBeNull();
      expect(result.current.screenshotError).toBe('');
    });

    test('clears input ref value', () => {
      const { result } = renderHook(() => useScreenshot());
      // Manually assign a mock DOM element to the ref
      result.current.fileInputRef.current = { value: 'some-file.png' };
      act(() => result.current.handleRemoveScreenshot());
      expect(result.current.fileInputRef.current.value).toBe('');
    });
  });
});
