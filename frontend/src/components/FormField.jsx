/**
 * A labeled input (or select) with an optional hint and the backend's error message
 * for this field. The error is linked to the input for screen readers.
 */
export default function FormField({ label, hint, error, as: Control = 'input', children, ...controlProps }) {
  const id = controlProps.id;
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <Control aria-describedby={describedBy} aria-invalid={error ? true : undefined} {...controlProps}>
        {children}
      </Control>
      {hint && <span className="hint" id={hintId}>{hint}</span>}
      {error && <span className="error" id={errorId}>{error}</span>}
    </div>
  );
}
