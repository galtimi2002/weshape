package com.weshape.backend;

import com.weshape.backend.i18n.SupportedLanguages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedLanguagesTest {

    @Test
    void fifteenLanguagesEnglishFirst() {
        assertThat(SupportedLanguages.CODES).hasSize(15).startsWith("en").doesNotHaveDuplicates();
    }

    @Test
    void countriesMapToTheirLanguage() {
        assertThat(SupportedLanguages.forCountry("HU")).isEqualTo("hu");
        assertThat(SupportedLanguages.forCountry("md")).isEqualTo("ro");
        assertThat(SupportedLanguages.forCountry("AT")).isEqualTo("de");
        assertThat(SupportedLanguages.forCountry("BR")).isEqualTo("pt");
        assertThat(SupportedLanguages.forCountry("TW")).isEqualTo("zh");
        assertThat(SupportedLanguages.forCountry("SE")).isEqualTo("en");
        assertThat(SupportedLanguages.forCountry(null)).isEqualTo("en");
    }

    @Test
    void everyMappedLanguageIsSupported() {
        for (String country : java.util.Locale.getISOCountries()) {
            assertThat(SupportedLanguages.isSupported(SupportedLanguages.forCountry(country))).isTrue();
        }
    }
}
