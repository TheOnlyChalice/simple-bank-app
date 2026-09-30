import { Link } from 'react-router';

export default function NotFoundPage() {
  return (
    <div className="narrow stack">
      <h1>Page not found</h1>
      <p className="muted">The address may be mistyped, or the page may have moved.</p>
      <Link to="/" className="button">Go to the home page</Link>
    </div>
  );
}
