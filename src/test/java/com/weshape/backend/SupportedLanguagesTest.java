package com.weshape.backend;

import com.weshape.backend.i18n.SupportedLanguages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedLanguagesTest {

    @Test
    void fiftyNineLanguagesEnglishFirst() {
        assertThat(SupportedLanguages.CODES).hasSize(59).startsWith("en").doesNotHaveDuplicates();
    }

    @Test
    void countriesMapToTheirLanguage() {
        assertThat(SupportedLanguages.forCountry("HU")).isEqualTo("hu");
        assertThat(SupportedLanguages.forCountry("md")).isEqualTo("ro");
        assertThat(SupportedLanguages.forCountry("AT")).isEqualTo("de");
        assertThat(SupportedLanguages.forCountry("BR")).isEqualTo("pt");
        assertThat(SupportedLanguages.forCountry("TW")).isEqualTo("zh");
        assertThat(SupportedLanguages.forCountry("SE")).isEqualTo("sv");
        assertThat(SupportedLanguages.forCountry("DK")).isEqualTo("da");
        assertThat(SupportedLanguages.forCountry("CY")).isEqualTo("el");
        assertThat(SupportedLanguages.forCountry("JP")).isEqualTo("ja");
        assertThat(SupportedLanguages.forCountry("KR")).isEqualTo("ko");
        assertThat(SupportedLanguages.forCountry("IN")).isEqualTo("hi");
        assertThat(SupportedLanguages.forCountry("AE")).isEqualTo("ar");
        assertThat(SupportedLanguages.forCountry("VN")).isEqualTo("vi");
        assertThat(SupportedLanguages.forCountry("NO")).isEqualTo("nb");
        assertThat(SupportedLanguages.forCountry("CZ")).isEqualTo("cs");
        assertThat(SupportedLanguages.forCountry("UA")).isEqualTo("uk");
        assertThat(SupportedLanguages.forCountry("BA")).isEqualTo("bs");
        assertThat(SupportedLanguages.forCountry("ME")).isEqualTo("sr");
        assertThat(SupportedLanguages.forCountry("KE")).isEqualTo("sw");
        assertThat(SupportedLanguages.forCountry("PK")).isEqualTo("ur");
        assertThat(SupportedLanguages.forCountry("IE")).isEqualTo("en");
        assertThat(SupportedLanguages.forCountry("NG")).isEqualTo("en");
        assertThat(SupportedLanguages.forCountry(null)).isEqualTo("en");
    }

    @Test
    void everyMappedLanguageIsSupported() {
        for (String country : java.util.Locale.getISOCountries()) {
            assertThat(SupportedLanguages.isSupported(SupportedLanguages.forCountry(country))).isTrue();
        }
    }
}
