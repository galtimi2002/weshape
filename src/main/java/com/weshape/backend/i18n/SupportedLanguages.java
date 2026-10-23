package com.weshape.backend.i18n;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The languages the app is translated into, and the language a country starts
 * with. Kept in step with {@code lib/core/i18n/languages.dart} in the app.
 *
 * Country to language is only a starting suggestion at registration; whatever
 * the person picks afterwards is what gets stored and used.
 */
public final class SupportedLanguages {

    public static final String DEFAULT = "en";

    /** ISO 639-1 codes, in the order the app lists them. */
    public static final List<String> CODES = List.of(
            "en", "hu", "ro", "de", "fr", "es", "it", "pt", "nl", "pl",
            "hr", "fi", "sv", "da", "el", "ru", "tr", "zh", "ja", "ko",
            "hi", "bn", "id", "vi", "ar");

    private static final Set<String> CODE_SET = Set.copyOf(CODES);

    private static final Map<String, String> BY_COUNTRY = new HashMap<>();

    static {
        map("hu", "HU");
        map("ro", "RO", "MD");
        map("de", "DE", "AT", "CH", "LI");
        map("fr", "FR", "MC", "LU", "SN", "CI", "ML", "BF", "NE", "TG", "BJ", "GN",
                "CD", "CG", "CM", "GA", "MG", "HT", "CF", "TD", "DJ", "KM", "BI");
        map("es", "ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO",
                "DO", "HN", "PY", "SV", "NI", "CR", "PA", "UY", "PR", "GQ");
        map("it", "IT", "SM", "VA");
        map("pt", "PT", "BR", "AO", "MZ", "CV", "GW", "ST", "TL");
        map("nl", "NL", "BE", "SR");
        map("pl", "PL");
        map("hr", "HR", "BA");
        map("fi", "FI");
        map("sv", "SE", "AX");
        map("da", "DK", "GL", "FO");
        map("el", "GR", "CY");
        map("ru", "RU", "BY", "KZ", "KG");
        map("tr", "TR");
        map("zh", "CN", "TW", "HK", "MO");
        map("ja", "JP");
        map("ko", "KR");
        map("hi", "IN");
        map("bn", "BD");
        map("id", "ID");
        map("vi", "VN");
        map("ar", "SA", "AE", "EG", "MA", "DZ", "TN", "JO", "QA", "KW", "OM", "BH",
                "LB", "IQ", "LY", "SY", "YE", "SD", "PS", "MR");
    }

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private SupportedLanguages() {
    }

    private static void map(String language, String... countries) {
        for (String country : countries) {
            BY_COUNTRY.put(country, language);
        }
    }

    public static boolean isSupported(String code) {
        return code != null && CODE_SET.contains(code);
    }

    /** The starting language for a country, English when it has none of its own here. */
    public static String forCountry(String country) {
        if (country == null) {
            return DEFAULT;
        }
        return BY_COUNTRY.getOrDefault(country.toUpperCase(Locale.ROOT), DEFAULT);
    }

    public static boolean isCountry(String code) {
        return code != null && code.length() == 2 && ISO_COUNTRIES.contains(code.toUpperCase(Locale.ROOT));
    }
}
