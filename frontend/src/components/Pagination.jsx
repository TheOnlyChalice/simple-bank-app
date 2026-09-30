/** Previous/next paging for a PageResponse. Hidden when there's only one page. */
export default function Pagination({ page, totalPages, first, last, onChange }) {
  if (totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button type="button" className="button small secondary" disabled={first} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span className="muted">Page {page + 1} of {totalPages}</span>
      <button type="button" className="button small secondary" disabled={last} onClick={() => onChange(page + 1)}>
        Next
      </button>
    </div>
  );
}
