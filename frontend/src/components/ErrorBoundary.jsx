import React from 'react';
import { trackEvent } from '../telemetry';

// Wraps the whole app (see index.js) so an uncaught render error anywhere in the tree shows a
// recoverable message instead of leaving the user staring at a blank white page with no way
// back short of guessing the URL. React error boundaries must be class components — there is
// no hook equivalent for getDerivedStateFromError/componentDidCatch.
export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error) {
    // Best-effort visibility into Cloud Logging — trackEvent already swallows its own failures,
    // so this can never itself throw and compound the original error.
    trackEvent('react_error_boundary_triggered', window.location.pathname, {
      message: error?.message || 'Unknown error',
    });
  }

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="page">
          <div className="page-header">
            <h1>Something went wrong</h1>
          </div>
          <div role="alert" className="alert alert-error">
            An unexpected error occurred. Please reload the page and try again.
          </div>
          <button type="button" className="btn btn-primary" onClick={this.handleReload}>
            Reload Page
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
