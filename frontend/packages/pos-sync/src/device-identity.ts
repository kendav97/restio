/**
 * What a device receives when it is linked to a restaurant. `deviceToken` is a secret: it lives in
 * the platform keystore, never in SQLite.
 */
export interface DeviceIdentity {
  /** Cloud identifier, used to log in as the device. */
  readonly deviceId: number;
  /** Identifier recorded in every event (`DomainEvent.deviceId`). */
  readonly deviceUuid: string;
  /** Short code of the device's ticket series (D01, D02...). Never reused. */
  readonly deviceCode: string;
  readonly restaurantId: number;
  readonly name: string;
  readonly deviceToken: string;
}

const QR_SCHEME = "restio://pair";

/** Content of the QR shown by the backoffice or a linked device. */
export function pairingQrContent(code: string): string {
  return `${QR_SCHEME}?code=${encodeURIComponent(code)}`;
}

/**
 * Extracts the pairing code from a scanned QR or from what the manager typed by hand.
 *
 * @returns the code, or `null` if the text is neither
 */
export function parsePairingCode(text: string): string | null {
  const trimmed = text.trim();
  if (trimmed.startsWith(QR_SCHEME)) {
    const query = trimmed.slice(QR_SCHEME.length).replace(/^\?/, "");
    const code = new URLSearchParams(query).get("code");
    return code && isCode(code) ? code : null;
  }
  return isCode(trimmed) ? trimmed : null;
}

function isCode(text: string): boolean {
  return /^[A-Za-z0-9]{4}-?[A-Za-z0-9]{4}$/.test(text);
}
