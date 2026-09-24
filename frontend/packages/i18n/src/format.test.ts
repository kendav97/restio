import { describe, expect, it } from "vitest";
import { formatDateTime, formatMoney } from "./format.js";

// Intl uses non-breaking spaces; normalise them to compare.
const plain = (s: string) => s.replace(/\s/g, " ");

describe("formatMoney", () => {
  it("usa la moneda del local y el formato del usuario", () => {
    expect(plain(formatMoney(123456, "ARS", "es-AR"))).toBe("$ 1.234,56");
    expect(plain(formatMoney(123456, "ARS", "en"))).toBe("ARS 1,234.56");
    expect(plain(formatMoney(1050, "EUR", "es-ES"))).toBe("10,50 €");
  });

  it("respeta monedas sin decimales", () => {
    expect(plain(formatMoney(1500, "JPY", "en"))).toBe("¥1,500");
  });

  it("rechaza importes que no son enteros", () => {
    expect(() => formatMoney(10.5, "EUR", "es")).toThrow();
  });
});

describe("formatDateTime", () => {
  it("formatea en la zona horaria del local", () => {
    // Exact separators vary with the ICU data shipped by each runtime; check day and time only.
    const options = {
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    } as const;

    const buenosAires = formatDateTime(
      "2026-09-24T02:30:00Z",
      "es-AR",
      "America/Argentina/Buenos_Aires",
      options,
    );
    const madrid = formatDateTime("2026-09-24T02:30:00Z", "es-ES", "Europe/Madrid", options);

    expect(buenosAires).toMatch(/^23\D.*23:30$/);
    expect(madrid).toMatch(/^24\D.*04:30$/);
  });
});
