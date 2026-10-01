import { Link } from 'react-router';
import { useLanguage } from '../i18n/LanguageContext';

export default function NotFoundPage() {
  const { t } = useLanguage();
  return (
    <div className="narrow stack">
      <h1>{t('notFound.title')}</h1>
      <p className="muted">{t('notFound.body')}</p>
      <Link to="/" className="button">{t('notFound.home')}</Link>
    </div>
  );
}

