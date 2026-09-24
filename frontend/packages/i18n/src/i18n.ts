import i18next, { type i18n as I18nInstance } from "i18next";
import { IcuFormat } from "./icu-format.js";
import {
  NAMESPACES,
  RESOURCES,
  fallbackChain,
  resolveLocale,
  type SupportedLocale,
} from "./locales.js";

export type { I18nInstance };

/**
 * Creates an isolated i18next instance with ICU MessageFormat and the Restio fallback chain.
 * Each app (or shared device user session) owns its instance; switching user switches locale.
 */
export async function createI18n(
  requestedLocale: string | null | undefined,
): Promise<I18nInstance> {
  const locale = resolveLocale(requestedLocale);
  const instance = i18next.createInstance();
  await instance.use(new IcuFormat()).init({
    lng: locale,
    fallbackLng: (code: string) => fallbackChain(resolveLocale(code)),
    supportedLngs: Object.keys(RESOURCES),
    ns: [...NAMESPACES],
    defaultNS: "common",
    resources: RESOURCES,
    interpolation: { escapeValue: false },
    returnNull: false,
    // A key missing everywhere must never reach the screen raw.
    parseMissingKeyHandler: () => "…",
  });
  return instance;
}

export async function changeLocale(
  instance: I18nInstance,
  requested: string,
): Promise<SupportedLocale> {
  const locale = resolveLocale(requested);
  await instance.changeLanguage(locale);
  return locale;
}
