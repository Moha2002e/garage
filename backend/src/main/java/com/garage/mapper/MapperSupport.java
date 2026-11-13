package com.garage.mapper;

import org.mapstruct.Named;

/** Text helpers shared by the mappers (static: no Spring bean needed, usable from plain unit tests). */
public final class MapperSupport {

    private MapperSupport() {
    }

    @Named("trim")
    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    @Named("normalizeEmail")
    public static String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    /** Part reference: trimmed and upper-case, so "ab-1 " and "AB-1" are the same reference. */
    @Named("normalizeReference")
    public static String normalizeReference(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    @Named("blankToNull")
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** "First Last" or "Brand Model": two words joined by a space, trimmed. Not a mapping qualifier. */
    public static String join(String first, String second) {
        return (first + " " + second).trim();
    }
}
