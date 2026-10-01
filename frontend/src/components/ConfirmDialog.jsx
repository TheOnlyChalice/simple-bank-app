/** A modal yes/cancel prompt, used instead of the browser's native confirm(). */
export default function ConfirmDialog({ open, message, confirmLabel, cancelLabel, danger, onConfirm, onCancel }) {
  if (!open) return null;
  return (
    <div className="modal-overlay" role="presentation" onClick={onCancel}>
      <div
        className="modal"
        role="alertdialog"
        aria-modal="true"
        aria-label={message}
        onClick={(event) => event.stopPropagation()}
      >
        <p>{message}</p>
        <div className="button-row">
          <button type="button" className={`button ${danger ? 'danger' : ''}`} onClick={onConfirm} autoFocus>
            {confirmLabel}
          </button>
          <button type="button" className="button secondary" onClick={onCancel}>{cancelLabel}</button>
        </div>
      </div>
    </div>
  );
}
