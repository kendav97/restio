/**
 * Formats an amount held in minor units (cents) in the restaurant's currency.
 * The currency comes from the restaurant, not from the user's locale.
 */
export function formatMoney(amountMinor: number, currency: string, locale: string): string {
  if (!Number.isSafeInteger(amountMinor))
    throw new Error(`Amount must be an integer in minor units: ${amountMinor}`);
  const formatter = new Intl.NumberFormat(locale, { style: "currency", currency });
  const digits = formatter.resolvedOptions().maximumFractionDigits ?? 2;
  return formatter.format(amountMinor / 10 ** digits);
}

export function formatNumber(
  value: number,
  locale: string,
  options?: Intl.NumberFormatOptions,
): string {
  return new Intl.NumberFormat(locale, options).format(value);
}

/** Date and time in the restaurant's time zone, whatever the device's zone is. */
export function formatDateTime(
  value: Date | string,
  locale: string,
  timeZone: string,
  options: Intl.DateTimeFormatOptions = { dateStyle: "short", timeStyle: "short" },
): string {
  return new Intl.DateTimeFormat(locale, { ...options, timeZone }).format(
    typeof value === "string" ? new Date(value) : value,
  );
}
