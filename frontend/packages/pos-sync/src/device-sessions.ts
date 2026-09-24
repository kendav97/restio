import { ApiError } from "@restio/api-client";
import type { CloudTransport, DeviceSession } from "./cloud-transport.js";
import type { DeviceIdentity } from "./device-identity.js";

/**
 * Keeps the device's cloud session. Logs in lazily, renews before expiry and, when the cloud
 * answers 401 (token revoked or expired early), logs in again once and retries.
 */
export class DeviceSessions {
  private session: DeviceSession | null = null;

  constructor(
    private readonly transport: CloudTransport,
    readonly identity: () => DeviceIdentity,
    private readonly now: () => number = Date.now,
  ) {}

  async call<T>(work: (session: DeviceSession) => Promise<T>): Promise<T> {
    try {
      return await work(await this.current());
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
      this.session = null;
      return work(await this.current());
    }
  }

  private async current(): Promise<DeviceSession> {
    if (!this.session || this.session.expiresAt <= this.now()) {
      this.session = await this.transport.deviceLogin(this.identity());
    }
    return this.session;
  }
}
