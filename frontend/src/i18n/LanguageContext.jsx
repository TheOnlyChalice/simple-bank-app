import { createContext, useContext, useEffect, useState } from 'react';
import { translations } from './translations';

const STORAGE_KEY = 'simpleBank.language';
const LanguageContext = createContext(null);

function getInitialLanguage() {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved === 'en' || saved === 'es') return saved;
  return navigator.language?.toLowerCase().startsWith('es') ? 'es' : 'en';
}

/** English/Spanish text, persisted to localStorage. t(key, params) fills in {placeholders}. */
export function LanguageProvider({ children }) {
  const [language, setLanguage] = useState(getInitialLanguage);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, language);
    document.documentElement.lang = language;
  }, [language]);

  function t(key, params) {
    const text = translations[language]?.[key] ?? translations.en[key] ?? key;
    if (!params) return text;
    return Object.entries(params).reduce((result, [name, value]) => result.replaceAll(`{${name}}`, value), text);
  }

  return (
    <LanguageContext.Provider value={{ language, setLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  );
}

export function useLanguage() {
  return useContext(LanguageContext);
}
