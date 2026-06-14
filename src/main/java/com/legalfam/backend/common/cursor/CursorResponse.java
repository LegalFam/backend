package com.legalfam.backend.common.cursor;

import java.util.List;

public record CursorResponse<T>(
        List<T> content,
        String nextCursor
) {

    public static <T> CursorResponse<T> from(CursorResult<T> cursorResult) {
        return new CursorResponse<>(
                cursorResult.content(),
                cursorResult.nextCursor()
        );
    }
}
