import { useCallback, useRef, useState } from 'react';
import ConfirmDialog from './ConfirmDialog';
import { useLanguage } from '../i18n/LanguageContext';

/**
 * Promise-based replacement for window.confirm(): `if (!(await confirm(message))) return;`.
 * Render the returned `dialog` once, anywhere in the page.
 */
export function useConfirm() {
  const { t } = useLanguage();
  const [message, setMessage] = useState(null);
  const resolver = useRef(null);

  const confirm = useCallback((text) => {
    return new Promise((resolve) => {
      resolver.current = resolve;
      setMessage(text);
    });
  }, []);

  function settle(result) {
    setMessage(null);
    resolver.current?.(result);
    resolver.current = null;
  }

  const dialog = (
    <ConfirmDialog
      open={message !== null}
      message={message}
      danger
      confirmLabel={t('common.confirm')}
      cancelLabel={t('common.cancel')}
      onConfirm={() => settle(true)}
      onCancel={() => settle(false)}
    />
  );

  return { confirm, dialog };
}
