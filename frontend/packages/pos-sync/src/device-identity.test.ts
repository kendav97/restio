import { describe, expect, it } from "vitest";
import { pairingQrContent, parsePairingCode } from "./device-identity.js";

describe("parsePairingCode", () => {
  it("lee el código del QR generado", () => {
    expect(parsePairingCode(pairingQrContent("ABCD-EF23"))).toBe("ABCD-EF23");
  });

  it("acepta el código tecleado, con o sin guion", () => {
    expect(parsePairingCode(" abcd-ef23 ")).toBe("abcd-ef23");
    expect(parsePairingCode("ABCDEF23")).toBe("ABCDEF23");
  });

  it("rechaza textos que no son un código", () => {
    expect(parsePairingCode("https://example.com")).toBeNull();
    expect(parsePairingCode("restio://pair?code=nope")).toBeNull();
    expect(parsePairingCode("")).toBeNull();
  });
});
