package com.garage.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageUtil {

    private PageUtil() {
    }

    /** Page sizes are limited to 10 or 25; sorting is chosen by the server, never by the client. */
    public static Pageable of(int page, int size, Sort sort) {
        int safeSize = size == 25 ? 25 : 10;
        return PageRequest.of(Math.max(page, 0), safeSize, sort);
    }

    /** Page size between 1 and {@code maxSize} (default 10 when invalid); used by the history endpoints. */
    public static Pageable ofCapped(int page, int size, Sort sort, int maxSize) {
        int safeSize = size < 1 ? 10 : Math.min(size, maxSize);
        return PageRequest.of(Math.max(page, 0), safeSize, sort);
    }

    /** Escapes LIKE wildcards and lower-cases the term, ready to be wrapped in % %. */
    public static String likeTerm(String q) {
        String escaped = q.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
