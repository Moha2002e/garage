package com.garage.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PlateUtil {

    private static final Pattern SIV = Pattern.compile("^([A-Z]{2})([0-9]{3})([A-Z]{2})$");

    private PlateUtil() {
    }

    /**
     * Normalizes a French SIV plate to AA-123-AA (upper case, dashes).
     *
     * @return the normalized plate, or null when the input is not a valid SIV plate
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        var m = SIV.matcher(compact);
        return m.matches() ? m.group(1) + "-" + m.group(2) + "-" + m.group(3) : null;
    }
}
