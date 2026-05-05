import { renderHook, act } from '@testing-library/react';
import useScreenshot from '../../hooks/useScreenshot';
import { SCREENSHOT_CONSTRAINTS } from '../../constants';

const makePngFile = (name = 'pay.png', size = 100) =>
  new File(['x'.repeat(size)], name, { type: 'image/png' });

const makeInputEvent = (...files) => ({ target: { files } });

const mockFileReader = (result = 'data:image/png;base64,x') => {
  const reader = { readAsDataURL: jest.fn(), onloadend: null, result };
  jest.spyOn(global, 'FileReader').mockImplementation(() => reader);
  return reader;
};

describe('useScreenshot hook', () => {
  beforeEach(() => jest.clearAllMocks());

  // ── Initial state ────────────────────────────────────────────────────
  describe('initial state', () => {
    test('screenshots is empty array', () => {
      const { result } = renderHook(() => useScreenshot());
      expect(result.current.screenshots).toEqual([]);
    });
    test('hasAnyScreenshot is false', () => {
      const { result } = renderHook(() => useScreenshot());
      expect(result.current.hasAnyScreenshot).toBe(false);
    });
    test('hasAnyError is false', () => {
      const { result } = renderHook(() => useScreenshot());
      expect(result.current.hasAnyError).toBe(false);
    });
    test('canAddMore is true', () => {
      const { result } = renderHook(() => useScreenshot());
      expect(result.current.canAddMore).toBe(true);
    });
    test('fileInputRef is a ref object', () => {
      const { result } = renderHook(() => useScreenshot());
      expect(result.current.fileInputRef).toHaveProperty('current');
    });
  });

  // ── addScreenshot — valid file ─────────────────────────────────────────
  describe('addScreenshot — valid PNG', () => {
    test('adds screenshot entry with the file', () => {
      const { result } = renderHook(() => useScreenshot());
      const file = makePngFile();
      mockFileReader();

      act(() => result.current.addScreenshot(makeInputEvent(file)));

      expect(result.current.screenshots).toHaveLength(1);
      expect(result.current.screenshots[0].file).toBe(file);
      expect(result.current.screenshots[0].error).toBe('');
    });

    test('hasAnyScreenshot becomes true after adding a valid file', () => {
      const { result } = renderHook(() => useScreenshot());
      mockFileReader();

      act(() => result.current.addScreenshot(makeInputEvent(makePngFile())));

      expect(result.current.hasAnyScreenshot).toBe(true);
    });

    test('sets preview after FileReader loads', () => {
      const { result } = renderHook(() => useScreenshot());
      let capturedReader;
      jest.spyOn(global, 'FileReader').mockImplementation(() => {
        capturedReader = { readAsDataURL: jest.fn(), onloadend: null, result: 'data:image/png;base64,abc' };
        return capturedReader;
      });

      act(() => result.current.addScreenshot(makeInputEvent(makePngFile())));
      act(() => capturedReader.onloadend());

      expect(result.current.screenshots[0].preview).toBe('data:image/png;base64,abc');
    });
  });

  // ── addScreenshot — multiple files ────────────────────────────────────
  describe('addScreenshot — multiple files at once', () => {
    test('adds two files in a single event', () => {
      const { result } = renderHook(() => useScreenshot());
      const f1 = makePngFile('a.png');
      const f2 = makePngFile('b.png');
      mockFileReader();

      act(() => result.current.addScreenshot(makeInputEvent(f1, f2)));

      expect(result.current.screenshots).toHaveLength(2);
      expect(result.current.screenshots[0].file).toBe(f1);
      expect(result.current.screenshots[1].file).toBe(f2);
    });

    test('adding two files sequentially accumulates them', () => {
      const { result } = renderHook(() => useScreenshot());
      mockFileReader();

      act(() => result.current.addScreenshot(makeInputEvent(makePngFile('a.png'))));
      act(() => result.current.addScreenshot(makeInputEvent(makePngFile('b.png'))));

      expect(result.current.screenshots).toHaveLength(2);
    });
  });

  // ── addScreenshot — invalid type ──────────────────────────────────────
  describe('addScreenshot — invalid MIME type', () => {
    test.each(['application/pdf', 'text/plain', 'video/mp4'])(
      'sets error entry for %s',
      (mime) => {
        const { result } = renderHook(() => useScreenshot());
        act(() => result.current.addScreenshot(
          makeInputEvent(new File(['x'], 'bad', { type: mime }))
        ));
        expect(result.current.screenshots[0].error).toMatch(/PNG, JPEG/i);
        expect(result.current.screenshots[0].file).toBeNull();
      }
    );

    test('hasAnyError is true when any entry has an error', () => {
      const { result } = renderHook(() => useScreenshot());
      act(() => result.current.addScreenshot(
        makeInputEvent(new File(['x'], 'bad.pdf', { type: 'application/pdf' }))
      ));
      expect(result.current.hasAnyError).toBe(true);
    });
  });

  // ── addScreenshot — oversized file ────────────────────────────────────
  describe('addScreenshot — oversized file', () => {
    test('sets error when file exceeds MAX_BYTES', () => {
      const { result } = renderHook(() => useScreenshot());
      const big = makePngFile('big.png', 1);
      Object.defineProperty(big, 'size', { value: SCREENSHOT_CONSTRAINTS.MAX_BYTES + 1 });

      act(() => result.current.addScreenshot(makeInputEvent(big)));
      expect(result.current.screenshots[0].error).toMatch(/5 MB/i);
    });

    test('does not set file when oversized', () => {
      const { result } = renderHook(() => useScreenshot());
      const big = makePngFile('big.png', 1);
      Object.defineProperty(big, 'size', { value: SCREENSHOT_CONSTRAINTS.MAX_BYTES + 1 });

      act(() => result.current.addScreenshot(makeInputEvent(big)));
      expect(result.current.screenshots[0].file).toBeNull();
    });
  });

  // ── addScreenshot — empty event ────────────────────────────────────────
  test('no-ops when event has no files', () => {
    const { result } = renderHook(() => useScreenshot());
    act(() => result.current.addScreenshot({ target: { files: [] } }));
    expect(result.current.screenshots).toHaveLength(0);
  });

  // ── removeScreenshot ──────────────────────────────────────────────────
  describe('removeScreenshot', () => {
    test('removes entry at given index', () => {
      const { result } = renderHook(() => useScreenshot());
      mockFileReader();

      act(() => result.current.addScreenshot(makeInputEvent(makePngFile('a.png'))));
      act(() => result.current.addScreenshot(makeInputEvent(makePngFile('b.png'))));
      act(() => result.current.removeScreenshot(0));

      expect(result.current.screenshots).toHaveLength(1);
      expect(result.current.screenshots[0].file?.name).toBe('b.png');
    });

    test('hasAnyScreenshot becomes false after removing all', () => {
      const { result } = renderHook(() => useScreenshot());
      mockFileReader();

      act(() => result.current.addScreenshot(makeInputEvent(makePngFile())));
      act(() => result.current.removeScreenshot(0));

      expect(result.current.hasAnyScreenshot).toBe(false);
    });
  });

  // ── clearAll ──────────────────────────────────────────────────────────
  describe('clearAll', () => {
    test('clears all screenshots', () => {
      const { result } = renderHook(() => useScreenshot());
      mockFileReader();

      act(() => result.current.addScreenshot(makeInputEvent(makePngFile())));
      act(() => result.current.clearAll());

      expect(result.current.screenshots).toHaveLength(0);
    });

    test('clears input ref value', () => {
      const { result } = renderHook(() => useScreenshot());
      result.current.fileInputRef.current = { value: 'some-file.png' };
      act(() => result.current.clearAll());
      expect(result.current.fileInputRef.current.value).toBe('');
    });
  });
});
