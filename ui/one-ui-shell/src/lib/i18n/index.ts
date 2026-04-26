/**
 * Impilo vNext — Internationalization (i18n) Module
 *
 * Supports 3 official languages of Zimbabwe:
 * - English (en) — default
 * - Shona (sn) — chiShona
 * - Ndebele (nd) — isiNdebele
 *
 * Usage:
 *   const { t, locale, setLocale } = useI18n();
 *   <span>{t('queue.title')}</span>
 *   <button onClick={() => setLocale('sn')}>Shona</button>
 */

import en from './locales/en.json';
import sn from './locales/sn.json';
import nd from './locales/nd.json';

export type Locale = 'en' | 'sn' | 'nd';

export const LOCALES: Record<Locale, { name: string; nativeName: string; dir: 'ltr' }> = {
  en: { name: 'English', nativeName: 'English', dir: 'ltr' },
  sn: { name: 'Shona', nativeName: 'chiShona', dir: 'ltr' },
  nd: { name: 'Ndebele', nativeName: 'isiNdebele', dir: 'ltr' },
};

const translations: Record<Locale, Record<string, unknown>> = { en, sn, nd };

/**
 * Get a nested translation value by dot-separated key path.
 * Falls back to English if the key is missing in the current locale.
 */
export function translate(locale: Locale, key: string): string {
  const value = getNestedValue(translations[locale], key);
  if (value !== undefined) return String(value);

  // Fallback to English
  if (locale !== 'en') {
    const fallback = getNestedValue(translations.en, key);
    if (fallback !== undefined) return String(fallback);
  }

  // Return the key itself as a last resort
  return key;
}

function getNestedValue(obj: Record<string, unknown>, path: string): unknown {
  const parts = path.split('.');
  let current: unknown = obj;
  for (const part of parts) {
    if (current === null || current === undefined || typeof current !== 'object') {
      return undefined;
    }
    current = (current as Record<string, unknown>)[part];
  }
  return current;
}

/**
 * Get the stored locale from localStorage, defaulting to 'en'.
 */
export function getStoredLocale(): Locale {
  if (typeof window === 'undefined') return 'en';
  const stored = localStorage.getItem('impilo-locale');
  if (stored && stored in LOCALES) return stored as Locale;
  return 'en';
}

/**
 * Store the locale preference.
 */
export function storeLocale(locale: Locale): void {
  if (typeof window !== 'undefined') {
    localStorage.setItem('impilo-locale', locale);
  }
}
