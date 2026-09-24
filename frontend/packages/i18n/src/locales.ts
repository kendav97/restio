import en from "./locales/en/common.json";
import esAR from "./locales/es-AR/common.json";
import esES from "./locales/es-ES/common.json";
import esMX from "./locales/es-MX/common.json";
import es from "./locales/es/common.json";

/** Locales shipped in v1 (BCP 47). Regional variants only hold what differs from their base. */
export const SUPPORTED_LOCALES = ["es", "es-AR", "es-ES", "es-MX", "en"] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

/** Last resort for any missing key. */
export const FINAL_FALLBACK: SupportedLocale = "en";

export const NAMESPACES = ["common"] as const;
export type Namespace = (typeof NAMESPACES)[number];

export type Catalog = { [key: string]: string | Catalog };

export const RESOURCES: Record<SupportedLocale, Record<Namespace, Catalog>> = {
  es: { common: es },
  "es-AR": { common: esAR },
  "es-ES": { common: esES },
  "es-MX": { common: esMX },
  en: { common: en },
};

/**
 * Picks the closest supported locale: exact match, then its base language, then the final
 * fallback. `es-UY` becomes `es`, `fr-FR` becomes `en`.
 */
export function resolveLocale(requested: string | null | undefined): SupportedLocale {
  if (!requested) return FINAL_FALLBACK;
  const normalized = requested.replace("_", "-");
  const exact = SUPPORTED_LOCALES.find((l) => l.toLowerCase() === normalized.toLowerCase());
  if (exact) return exact;
  const base = normalized.split("-")[0]!.toLowerCase();
  return SUPPORTED_LOCALES.find((l) => l === base) ?? FINAL_FALLBACK;
}

/** Fallback chain for a locale, e.g. `es-AR` → `es` → `en`. */
export function fallbackChain(locale: SupportedLocale): SupportedLocale[] {
  const chain: SupportedLocale[] = [locale];
  const base = locale.split("-")[0] as SupportedLocale;
  if (base !== locale) chain.push(base);
  if (!chain.includes(FINAL_FALLBACK)) chain.push(FINAL_FALLBACK);
  return chain;
}
