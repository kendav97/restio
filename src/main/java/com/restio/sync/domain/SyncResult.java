package com.restio.sync.domain;

/** Outcome of one uploaded event. */
public enum SyncResult {
    /** Stored for the first time. */
    APPLIED,
    /** Already stored: an earlier upload got through, the device can mark it synced. */
    DUPLICATE,
    /** Refused and not stored (for example, it belongs to another restaurant). */
    REJECTED
}
