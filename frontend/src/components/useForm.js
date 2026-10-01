import { useState } from 'react';
import { useLanguage } from '../i18n/LanguageContext';

/**
 * Form state for every page: the values, the backend's field errors, a general message,
 * and whether it's submitting. bind('email') connects an input to the "email" value.
 */
export function useForm(initialValues) {
  const { t } = useLanguage();
  const [values, setValues] = useState(initialValues);
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function bind(name) {
    return {
      id: name,
      name,
      value: values[name],
      onChange: (event) => setValues((current) => ({ ...current, [name]: event.target.value })),
    };
  }

  /** Runs the request. On failure, shows the errors and returns undefined. */
  async function submit(action) {
    setSubmitting(true);
    setErrors({});
    setMessage('');
    try {
      return await action(values);
    } catch (error) {
      const fieldErrors = error.fieldErrors ?? {};
      setErrors(fieldErrors);
      setMessage(Object.keys(fieldErrors).length > 0 ? t('form.checkFields') : error.message);
      return undefined;
    } finally {
      setSubmitting(false);
    }
  }

  return { values, setValues, errors, message, setMessage, submitting, bind, submit };
}
