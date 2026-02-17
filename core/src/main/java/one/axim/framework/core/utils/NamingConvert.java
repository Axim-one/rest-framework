package one.axim.framework.core.utils;

/**
 * Created by dudgh on 2017. 5. 27..
 */
public class NamingConvert {

    public static String toUnderScoreName(String str) {
        if (str == null || str.isEmpty()) return str;

        return str.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                  .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                  .toLowerCase();
    }

    public static String toCamelCase(String value) {
        if (value == null || value.isEmpty()) return value;

        StringBuilder sb = new StringBuilder(value);

        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '_') {
                sb.deleteCharAt(i);
                if (i < sb.length()) {
                    sb.replace(i, i + 1, String.valueOf(Character.toUpperCase(sb.charAt(i))));
                }
            }
        }

        return sb.toString();
    }

    public static String toCamelCaseByClassName(String s) {

        String[] parts = s.split("_");
        StringBuilder camelCaseString = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                camelCaseString.append(toProperCase(part));
            }
        }
        return camelCaseString.toString();
    }

    static String toProperCase(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() +
                s.substring(1).toLowerCase();
    }
}