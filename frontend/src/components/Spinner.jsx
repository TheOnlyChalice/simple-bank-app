/** A small inline loading indicator, used wherever pages show "Loading…" text. */
export default function Spinner({ label }) {
  return (
    <p className="loading-row">
      <span className="spinner" aria-hidden="true" />
      {label}
    </p>
  );
}
