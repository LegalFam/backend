package com.legalfam.backend.common.cursor;

import java.util.List;
import java.util.function.Function;

public record CursorResult<T>(
        List<T> content,
        String nextCursor
) {

    public <R> CursorResult<R> map(Function<T, R> mapper) {
        return new CursorResult<>(
                content.stream().map(mapper).toList(),
                nextCursor
        );
    }
}
