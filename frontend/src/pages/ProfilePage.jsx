import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { users } from '../api/bank';
import Alert from '../components/Alert';
import FormField from '../components/FormField';
import { useForm } from '../components/useForm';

/** View and edit your own name, email, and address; or close your profile. */
export default function ProfilePage() {
  const { user, updateUser, logout } = useAuth();
  const navigate = useNavigate();
  const [saved, setSaved] = useState(false);
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
    setSaved(false);
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
      setSaved(true);
    }
  }

  async function handleDelete() {
    if (!window.confirm('Close your profile? This cannot be undone.')) return;
    setDeleting(true);
    setDeleteError('');
    try {
      await users.remove(user.userId);
      logout();
      navigate('/', { state: { notice: 'Your profile has been closed.' } });
    } catch (error) {
      setDeleteError(error.message);
      setDeleting(false);
    }
  }

  const { errors, bind } = form;

  return (
    <div className="narrow wide stack-lg">
      <h1>Profile</h1>

      <Alert kind="success">{saved ? 'Your profile has been updated.' : ''}</Alert>
      <Alert>{form.message}</Alert>

      <form className="panel stack" onSubmit={handleSubmit} noValidate>
        <FormField label="Name" autoComplete="name" error={errors.name} {...bind('name')} />
        <FormField label="Email" type="email" autoComplete="email" error={errors.email} {...bind('email')} />

        <fieldset className="stack">
          <legend>Address</legend>
          <FormField label="Street" autoComplete="street-address" error={errors['address.street']}
            {...bind('address.street')} />
          <div className="row">
            <FormField label="City" autoComplete="address-level2" error={errors['address.city']}
              {...bind('address.city')} />
            <FormField label="State" autoComplete="address-level1" maxLength={2} hint="2 letters, e.g. MD"
              error={errors['address.state']} {...bind('address.state')} />
            <FormField label="ZIP code" autoComplete="postal-code" inputMode="numeric"
              error={errors['address.zip']} {...bind('address.zip')} />
          </div>
        </fieldset>

        <button type="submit" className="button" disabled={form.submitting}>
          {form.submitting ? 'Saving…' : 'Save changes'}
        </button>
      </form>

      <section className="panel stack">
        <h2>Close profile</h2>
        <p className="muted">This permanently closes your profile. It cannot be undone, and all your accounts must be closed first.</p>
        <Alert>{deleteError}</Alert>
        <button type="button" className="button danger" onClick={handleDelete} disabled={deleting}>
          {deleting ? 'Closing…' : 'Close my profile'}
        </button>
      </section>
    </div>
  );
}
