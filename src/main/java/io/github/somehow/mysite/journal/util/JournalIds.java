package io.github.somehow.mysite.journal.util;

import java.security.SecureRandom;

/** 与前端 nanoid 默认长度/字母表对齐的 21 位 id。 */
public final class JournalIds {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SIZE = 21;

    private JournalIds() {
    }

    public static String nanoid() {
        char[] buf = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            buf[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }
}
