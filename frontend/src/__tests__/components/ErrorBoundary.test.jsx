import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ErrorBoundary from '../../components/ErrorBoundary';
import * as api from '../../api/api';

jest.mock('../../api/api');

function Bomb() {
  throw new Error('boom');
}

// React logs the caught error to console.error even when an ErrorBoundary handles it — expected
// noise for these tests, not a real failure.
let consoleErrorSpy;
beforeEach(() => {
  jest.clearAllMocks();
  api.postTelemetryEvent.mockResolvedValue({});
  consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
});
afterEach(() => {
  consoleErrorSpy.mockRestore();
});

describe('ErrorBoundary', () => {
  test('renders children normally when nothing throws', () => {
    render(
      <ErrorBoundary>
        <div>All good</div>
      </ErrorBoundary>
    );
    expect(screen.getByText('All good')).toBeInTheDocument();
  });

  test('renders a fallback message instead of crashing when a child throws during render', () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>
    );
    expect(screen.getByRole('heading', { name: /something went wrong/i })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(/unexpected error occurred/i);
  });

  test('reports the crash via telemetry so it is visible in Cloud Logging', () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>
    );
    expect(api.postTelemetryEvent).toHaveBeenCalledWith(
      'react_error_boundary_triggered',
      expect.any(String),
      expect.objectContaining({ message: 'boom' })
    );
  });

  test('the Reload Page button reloads the page', async () => {
    const reloadSpy = jest.fn();
    const originalLocation = window.location;
    delete window.location;
    window.location = { ...originalLocation, reload: reloadSpy };

    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>
    );
    await userEvent.click(screen.getByRole('button', { name: /reload page/i }));

    expect(reloadSpy).toHaveBeenCalledTimes(1);
    window.location = originalLocation;
  });
});
