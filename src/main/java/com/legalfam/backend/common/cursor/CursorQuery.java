package com.legalfam.backend.common.cursor;

public record CursorQuery(Integer cursor, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static CursorQuery of(Integer cursor, int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Cursor size must be between 1 and " + MAX_SIZE);
        }
        if (cursor != null && cursor < 0) {
            throw new IllegalArgumentException("Cursor is invalid");
        }
        return new CursorQuery(cursor, size);
    }

    public int offset() {
        return cursor == null ? 0 : cursor;
    }
}
