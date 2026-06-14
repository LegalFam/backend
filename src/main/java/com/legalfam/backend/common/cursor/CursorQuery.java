package com.legalfam.backend.common.cursor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record CursorQuery(String cursor, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static CursorQuery of(String cursor, int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Cursor size must be between 1 and " + MAX_SIZE);
        }
        if (cursor != null && !cursor.isBlank()) {
            decodeOffset(cursor);
        }
        return new CursorQuery(normalize(cursor), size);
    }

    public int offset() {
        if (cursor == null) {
            return 0;
        }
        return decodeOffset(cursor);
    }

    public static String nextCursor(int nextOffset) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Integer.toString(nextOffset).getBytes(StandardCharsets.UTF_8));
    }

    private static String normalize(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        return cursor.trim();
    }

    private static int decodeOffset(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            int offset = Integer.parseInt(decoded);
            if (offset < 0) {
                throw new IllegalArgumentException("Cursor is invalid");
            }
            return offset;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Cursor is invalid", ex);
        }
    }
}
