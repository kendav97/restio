package com.restio.auth.application;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * One-time pairing codes: 8 characters from an alphabet without look-alikes (no 0/O, 1/I/L), shown
 * as {@code ABCD-EFGH} so they can also be typed by hand. About 40 bits of entropy, enough for a
 * code that lives ten minutes and is redeemed once.
 */
final class PairingCodes {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PairingCodes() {}

    static String generate() {
        StringBuilder code = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Hash of the code as typed or scanned: case, dashes and spaces do not matter. */
    static String hash(String code) {
        String normalized = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return SecretTokens.hash(normalized);
    }
}
