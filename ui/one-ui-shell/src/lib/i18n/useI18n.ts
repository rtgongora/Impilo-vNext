'use client';

import { useState, useCallback, useEffect } from 'react';
import { type Locale, translate, getStoredLocale, storeLocale, LOCALES } from './index';

/**
 * React hook for internationalization.
 *
 * @example
 * const { t, locale, setLocale, locales } = useI18n();
 * return <h1>{t('queue.title')}</h1>;
 */
export function useI18n() {
  const [locale, setLocaleState] = useState<Locale>('en');

  useEffect(() => {
    setLocaleState(getStoredLocale());
  }, []);

  const setLocale = useCallback((newLocale: Locale) => {
    setLocaleState(newLocale);
    storeLocale(newLocale);
    // Update the document lang attribute for accessibility
    if (typeof document !== 'undefined') {
      document.documentElement.lang = newLocale === 'en' ? 'en' : newLocale === 'sn' ? 'sn' : 'nd';
    }
  }, []);

  const t = useCallback(
    (key: string) => translate(locale, key),
    [locale]
  );

  return {
    t,
    locale,
    setLocale,
    locales: LOCALES,
  };
}
