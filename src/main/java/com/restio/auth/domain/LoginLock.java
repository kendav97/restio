package com.restio.auth.domain;

import java.time.Duration;

/** Lockout policy: after {@code maxFailedAttempts} consecutive failures, lock for a while. */
public record LoginLock(int maxFailedAttempts, Duration lockDuration) {}
