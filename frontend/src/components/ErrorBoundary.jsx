import { Component } from 'react';

/** Catches render errors in the page tree so one broken page doesn't blank the whole app. */
export default class ErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('Unhandled error in page:', error, info);
  }

  render() {
    if (!this.state.error) return this.props.children;
    // Intentionally not translated: this is a last-resort fallback that must work
    // even if a context provider above it is what failed.
    return (
      <div className="narrow stack" style={{ padding: '2.5rem 1.25rem' }}>
        <h1>Something went wrong</h1>
        <p className="muted">Try reloading the page. If this keeps happening, contact support.</p>
        <button type="button" className="button" onClick={() => window.location.reload()}>Reload</button>
      </div>
    );
  }
}
