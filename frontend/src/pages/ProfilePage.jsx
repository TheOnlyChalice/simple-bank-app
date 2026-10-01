import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useToast } from '../toast/ToastContext';
import { users } from '../api/bank';
import Alert from '../components/Alert';
import FeatureIcon from '../components/FeatureIcon';
import FormField from '../components/FormField';
import { useConfirm } from '../components/useConfirm';
import { useForm } from '../components/useForm';

/** View and edit your own name, email, and address; or close your profile. */
export default function ProfilePage() {
  const { user, updateUser, logout } = useAuth();
  const { t } = useLanguage();
  const { showToast } = useToast();
  const { confirm, dialog } = useConfirm();
  const navigate = useNavigate();
  const [deleteError, setDeleteError] = useState('');
  const [deleting, setDeleting] = useState(false);
  const form = useForm({
    name: user.name,
    email: user.email,
    'address.street': user.address?.street ?? '',
    'address.city': user.address?.city ?? '',
    'address.state': user.address?.state ?? '',
    'address.zip': user.address?.zip ?? '',
  });

  async function handleSubmit(event) {
    event.preventDefault();
    const updated = await form.submit((values) =>
      users.update(user.userId, {
        name: values.name,
        email: values.email,
        address: {
          street: values['address.street'],
          city: values['address.city'],
          state: values['address.state'],
          zip: values['address.zip'],
        },
      }),
    );
    if (updated) {
      updateUser(updated);
      showToast(t('profile.saved'));
    }
  }

  async function handleDelete() {
    if (!(await confirm(t('profile.closeConfirm')))) return;
    setDeleting(true);
    setDeleteError('');
    try {
      await users.remove(user.userId);
      logout();
      showToast(t('profile.closedNotice'));
      navigate('/');
    } catch (error) {
      setDeleteError(error.message);
      setDeleting(false);
    }
  }

  const { errors, bind } = form;

  return (
    <div className="split-layout">
      <div className="narrow wide stack-lg">
        {dialog}
        <h1>{t('profile.title')}</h1>

        <Alert>{form.message}</Alert>

        <form className="panel stack" onSubmit={handleSubmit} noValidate>
          <FormField label={t('profile.name')} autoComplete="name" error={errors.name} {...bind('name')} />
          <FormField label={t('profile.email')} type="email" autoComplete="email" error={errors.email} {...bind('email')} />

          <fieldset className="stack">
            <legend>{t('profile.address')}</legend>
            <FormField label={t('profile.street')} autoComplete="street-address" error={errors['address.street']}
              {...bind('address.street')} />
            <div className="row">
              <FormField label={t('profile.city')} autoComplete="address-level2" error={errors['address.city']}
                {...bind('address.city')} />
              <FormField label={t('profile.state')} autoComplete="address-level1" maxLength={2} hint={t('profile.stateHint')}
                error={errors['address.state']} {...bind('address.state')} />
              <FormField label={t('profile.zip')} autoComplete="postal-code" inputMode="numeric"
                error={errors['address.zip']} {...bind('address.zip')} />
            </div>
          </fieldset>

          <button type="submit" className="button" disabled={form.submitting}>
            {form.submitting ? t('profile.saving') : t('profile.save')}
          </button>
        </form>

        <section className="panel stack">
          <h2>{t('profile.closeTitle')}</h2>
          <p className="muted">{t('profile.closeBody')}</p>
          <Alert>{deleteError}</Alert>
          <button type="button" className="button danger" onClick={handleDelete} disabled={deleting}>
            {deleting ? t('profile.closing') : t('profile.closeSubmit')}
          </button>
        </section>
      </div>

      <div className="panel aside-panel">
        <FeatureIcon kind="profile" />
        <h2>{t('profile.aside.title')}</h2>
        <ul className="feature-list">
          <li>{t('profile.aside.point1')}</li>
          <li>{t('profile.aside.point2')}</li>
          <li>{t('profile.aside.point3')}</li>
        </ul>
      </div>
    </div>
  );
}
