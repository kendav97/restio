import type { PosUser } from "@restio/pos-core";
import bcrypt from "bcryptjs";
import { describe, expect, it } from "vitest";
import { PinAuthenticator } from "./pin-authenticator.js";

// Same format Spring's BCryptPasswordEncoder produces ($2a$, cost 10 there; 4 here for speed).
const ANA: PosUser = {
  id: 2,
  displayName: "Ana",
  pinHash: bcrypt.hashSync("1234", 4),
  roles: ["WAITER"],
  permissions: [],
};

const lookup = { get: (id: number) => (id === ANA.id ? ANA : undefined) };

describe("PinAuthenticator", () => {
  const auth = new PinAuthenticator(lookup);

  it("acepta el PIN correcto sin red", async () => {
    await expect(auth.authenticate(2, "1234")).resolves.toEqual(ANA);
  });

  it("rechaza un PIN incorrecto o un usuario desconocido", async () => {
    await expect(auth.authenticate(2, "4321")).resolves.toBeNull();
    await expect(auth.authenticate(99, "1234")).resolves.toBeNull();
  });

  it("acepta hashes con prefijo $2a$, el que generan Spring y pgcrypto", async () => {
    const hash2a = bcrypt.hashSync("9999", 4).replace(/^\$2b\$/, "$2a$");
    const spring = new PinAuthenticator({ get: () => ({ ...ANA, pinHash: hash2a }) });

    expect(hash2a.startsWith("$2a$")).toBe(true);
    await expect(spring.authenticate(2, "9999")).resolves.not.toBeNull();
  });

  it("no compara hashes si el formato del PIN es inválido", async () => {
    let compared = false;
    const strict = new PinAuthenticator(lookup, () => {
      compared = true;
      return Promise.resolve(true);
    });

    await expect(strict.authenticate(2, "12a4")).resolves.toBeNull();
    expect(compared).toBe(false);
  });
});
