/** A message box. Errors are announced to screen readers immediately. */
export default function Alert({ kind = 'error', children }) {
  if (!children) return null;
  return (
    <div className={`alert ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}
