import type { PosUser } from "@restio/pos-core";
import bcrypt from "bcryptjs";

/** Where the authenticator looks users up: pos-core's `PosUserStore`. */
export interface PosUserLookup {
  get(id: number): PosUser | undefined;
}

/**
 * Checks an employee's PIN against the downloaded hash, without network. The employee picks
 * their name first, so only one BCrypt comparison runs (each one takes a noticeable fraction of
 * a second on a phone).
 */
export class PinAuthenticator {
  constructor(
    private readonly users: PosUserLookup,
    private readonly matches: (pin: string, hash: string) => Promise<boolean> = (pin, hash) =>
      bcrypt.compare(pin, hash),
  ) {}

  /** @returns the user if the PIN is theirs, otherwise `null` */
  async authenticate(userId: number, pin: string): Promise<PosUser | null> {
    const user = this.users.get(userId);
    if (!user || !/^\d{4,6}$/.test(pin)) return null;
    return (await this.matches(pin, user.pinHash)) ? user : null;
  }
}
