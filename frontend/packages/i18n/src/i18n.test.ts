import { IntlMessageFormat } from "intl-messageformat";
import { describe, expect, it } from "vitest";
import { changeLocale, createI18n } from "./i18n.js";
import {
  RESOURCES,
  SUPPORTED_LOCALES,
  fallbackChain,
  resolveLocale,
  type Catalog,
} from "./locales.js";

function flatten(catalog: Catalog, prefix = ""): Map<string, string> {
  const out = new Map<string, string>();
  for (const [key, value] of Object.entries(catalog)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === "string") out.set(path, value);
    else for (const [k, v] of flatten(value, path)) out.set(k, v);
  }
  return out;
}

describe("locales", () => {
  it("resuelve al locale soportado más cercano", () => {
    expect(resolveLocale("es-AR")).toBe("es-AR");
    expect(resolveLocale("es_ar")).toBe("es-AR");
    expect(resolveLocale("es-UY")).toBe("es");
    expect(resolveLocale("fr-FR")).toBe("en");
    expect(resolveLocale(undefined)).toBe("en");
  });

  it("usa la cadena de respaldo es-AR → es → en", () => {
    expect(fallbackChain("es-AR")).toEqual(["es-AR", "es", "en"]);
    expect(fallbackChain("es")).toEqual(["es", "en"]);
    expect(fallbackChain("en")).toEqual(["en"]);
  });
});

describe("catálogos", () => {
  const es = flatten(RESOURCES.es.common);
  const en = flatten(RESOURCES.en.common);

  it("es y en tienen exactamente las mismas claves", () => {
    expect([...es.keys()].sort()).toEqual([...en.keys()].sort());
  });

  it("las variantes regionales solo redefinen claves existentes en su base", () => {
    for (const locale of SUPPORTED_LOCALES.filter((l) => l.includes("-"))) {
      for (const key of flatten(RESOURCES[locale].common).keys()) {
        expect(es.has(key), `${locale}: ${key}`).toBe(true);
      }
    }
  });

  it("todos los mensajes son ICU válido", () => {
    for (const locale of SUPPORTED_LOCALES) {
      for (const [key, message] of flatten(RESOURCES[locale].common)) {
        expect(() => new IntlMessageFormat(message, locale), `${locale}: ${key}`).not.toThrow();
      }
    }
  });
});

describe("createI18n", () => {
  it("aplica la terminología regional y hereda el resto de la base", async () => {
    const i18n = await createI18n("es-AR");

    expect(i18n.t("role.waiter")).toBe("Mozo");
    expect(i18n.t("product.soldOut")).toBe("Sin stock");
    expect(i18n.t("auth.pin.submit")).toBe("Entrar");
  });

  it("formatea plurales ICU", async () => {
    const i18n = await createI18n("es");

    expect(i18n.t("tables.open.guestsLabel", { count: 1 })).toBe("1 comensal");
    expect(i18n.t("tables.open.guestsLabel", { count: 4 })).toBe("4 comensales");
    expect(i18n.t("sync.pendingEvents", { count: 0 })).toBe("Todo sincronizado");
  });

  it("nunca muestra una clave inexistente en crudo", async () => {
    const i18n = await createI18n("es-AR");

    expect(i18n.t("does.not.exist")).toBe("…");
  });

  it("cambia de idioma al cambiar de usuario", async () => {
    const i18n = await createI18n("es-ES");
    expect(i18n.t("role.waiter")).toBe("Camarero");

    await changeLocale(i18n, "en-US");
    expect(i18n.t("role.waiter")).toBe("Waiter");
  });
});
