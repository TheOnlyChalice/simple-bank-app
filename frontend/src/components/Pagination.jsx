import { useLanguage } from '../i18n/LanguageContext';

/** Previous/next paging for a PageResponse. Hidden when there's only one page. */
export default function Pagination({ page, totalPages, first, last, onChange }) {
  const { t } = useLanguage();
  if (totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button type="button" className="button small secondary" disabled={first} onClick={() => onChange(page - 1)}>
        {t('pagination.previous')}
      </button>
      <span className="muted">{t('pagination.pageOf', { page: page + 1, total: totalPages })}</span>
      <button type="button" className="button small secondary" disabled={last} onClick={() => onChange(page + 1)}>
        {t('pagination.next')}
      </button>
    </div>
  );
}

