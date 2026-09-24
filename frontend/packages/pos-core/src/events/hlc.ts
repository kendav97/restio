/**
 * Hybrid logical clock: physical time + counter + node id.
 *
 * Encoded as a fixed-width string (`<15 digits ms>-<5 digits counter>-<nodeId>`) so that
 * lexicographic order equals causal order, which lets SQLite sort by it directly.
 */
export interface Hlc {
  readonly millis: number;
  readonly counter: number;
  readonly nodeId: string;
}

const MAX_COUNTER = 99_999;
/** Remote clocks further ahead than this are rejected instead of dragging ours forward. */
const MAX_DRIFT_MS = 60_000;

export function encodeHlc(hlc: Hlc): string {
  return `${String(hlc.millis).padStart(15, "0")}-${String(hlc.counter).padStart(5, "0")}-${hlc.nodeId}`;
}

export function decodeHlc(value: string): Hlc {
  const match = /^(\d{15})-(\d{5})-(.+)$/.exec(value);
  if (!match) throw new Error(`Invalid HLC: ${value}`);
  return { millis: Number(match[1]), counter: Number(match[2]), nodeId: match[3]! };
}

export class HlcClock {
  private last: Hlc;

  constructor(
    private readonly nodeId: string,
    private readonly now: () => number = Date.now,
    initial?: string,
  ) {
    this.last = initial ? decodeHlc(initial) : { millis: 0, counter: 0, nodeId };
  }

  /** Timestamp for a local event. Never goes backwards even if the wall clock does. */
  tick(): string {
    const physical = this.now();
    this.last =
      physical > this.last.millis
        ? { millis: physical, counter: 0, nodeId: this.nodeId }
        : {
            millis: this.last.millis,
            counter: this.nextCounter(this.last.counter),
            nodeId: this.nodeId,
          };
    return encodeHlc(this.last);
  }

  /** Merges a timestamp received from another device so later local events sort after it. */
  receive(remote: string): string {
    const other = decodeHlc(remote);
    const physical = this.now();
    if (other.millis - physical > MAX_DRIFT_MS) {
      throw new Error(`Remote clock ${remote} is too far ahead`);
    }
    const millis = Math.max(physical, this.last.millis, other.millis);
    let counter = 0;
    if (millis === this.last.millis && millis === other.millis) {
      counter = this.nextCounter(Math.max(this.last.counter, other.counter));
    } else if (millis === this.last.millis) {
      counter = this.nextCounter(this.last.counter);
    } else if (millis === other.millis) {
      counter = this.nextCounter(other.counter);
    }
    this.last = { millis, counter, nodeId: this.nodeId };
    return encodeHlc(this.last);
  }

  private nextCounter(counter: number): number {
    if (counter >= MAX_COUNTER) throw new Error("HLC counter overflow");
    return counter + 1;
  }
}
