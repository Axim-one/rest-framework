package one.axim.framework.core.data;

import java.util.Locale;

public enum XDirection {
    ASC, DESC;

    public static XDirection fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Direction value must not be null. Use 'asc' or 'desc'.");
        }
        try {
            return XDirection.valueOf(value.toUpperCase(Locale.US));
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(
                    "Invalid value '%s' for orders given! Has to be either 'desc' or 'asc' (case insensitive).",
                    value), e);
        }
    }

    public static XDirection fromStringOrNull(String value) {

        try {
            return fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
