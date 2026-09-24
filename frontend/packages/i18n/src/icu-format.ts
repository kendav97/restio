import { IntlMessageFormat } from "intl-messageformat";

/**
 * i18next `i18nFormat` plugin that renders every message as ICU MessageFormat.
 *
 * Replaces i18next-icu, whose ESM build fails to construct IntlMessageFormat.
 * Compiled messages are cached per locale and source string.
 */
export class IcuFormat {
  readonly type = "i18nFormat" as const;
  private readonly cache = new Map<string, IntlMessageFormat>();

  init(): void {}

  parse(message: unknown, values: Record<string, unknown> | undefined, locale: string): unknown {
    if (typeof message !== "string") return message;
    const cacheKey = `${locale}\u0000${message}`;
    let compiled = this.cache.get(cacheKey);
    if (!compiled) {
      compiled = new IntlMessageFormat(message, locale);
      this.cache.set(cacheKey, compiled);
    }
    return compiled.format(values as Record<string, string | number | Date>);
  }

  addLookupKeys(finalKeys: string[]): string[] {
    return finalKeys;
  }
}
