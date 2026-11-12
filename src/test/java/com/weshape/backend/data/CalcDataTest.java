package com.weshape.backend.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.weshape.backend.calc.Co2;
import com.weshape.backend.calc.Range;
import com.weshape.backend.wind.FileRangeSource;

@DisplayName("Calculation data refreshed from R2")
class CalcDataTest {

    @TempDir
    Path dir;

    @AfterEach
    void bundledAgain() {
        Co2.useBundledElectricity();
    }

    /** A valid table: 200 made-up countries AA..HR plus Romania at 200 g. */
    static String table(String version, int countries, String extra) {
        StringBuilder b = new StringBuilder("# version: " + version + "\ncountry,year,low,typical,high\n");
        int n = 0;
        for (char a = 'A'; a <= 'Z' && n < countries; a++) {
            for (char c = 'A'; c <= 'Z' && n < countries; c++) {
                String code = "" + a + c;
                if (code.equals("RO")) continue;
                b.append(code).append(",2026,100,110,120\n");
                n++;
            }
        }
        return b.append("RO,2026,190,200,210\n").append(extra).toString();
    }

    private CalcData withFile(String text) throws Exception {
        Path f = dir.resolve(CalcData.ELECTRICITY_KEY);
        Files.createDirectories(f.getParent());
        Files.writeString(f, text, StandardCharsets.UTF_8);
        return new CalcData(new FileRangeSource(dir));
    }

    @Test
    @DisplayName("a newer table in R2 is put in use; the same version again changes nothing")
    void updated() throws Exception {
        assertThat(Co2.electricity("RO").gPerKwh().typical()).isEqualTo(251);
        CalcData data = withFile(table("2027-06", 210, ""));
        assertThat(data.refreshNow()).isEqualTo(CalcData.Outcome.UPDATED);
        assertThat(Co2.electricityVersion()).isEqualTo("2027-06");
        assertThat(Co2.electricity("RO").gPerKwh()).isEqualTo(new Range(190, 200, 210));
        assertThat(Co2.electricity("RO").year()).isEqualTo(2026);
        assertThat(data.refreshNow()).isEqualTo(CalcData.Outcome.UNCHANGED);
    }

    @Test
    @DisplayName("a broken table is rejected and the one in use stays")
    void rejected() throws Exception {
        String bundled = Co2.electricityVersion();
        assertThat(withFile(table("bad-few", 120, "")).refreshNow()).isEqualTo(CalcData.Outcome.REJECTED);
        assertThat(withFile(table("bad-zero", 210, "ZZ,2026,0,0,0\n")).refreshNow()).isEqualTo(CalcData.Outcome.REJECTED);
        assertThat(withFile(table("bad-twice", 210, "RO,2026,1,2,3\n")).refreshNow()).isEqualTo(CalcData.Outcome.REJECTED);
        assertThat(withFile("<html>error</html>").refreshNow()).isEqualTo(CalcData.Outcome.REJECTED);
        assertThat(Co2.electricityVersion()).isEqualTo(bundled);
        assertThat(Co2.electricity("RO").gPerKwh().typical()).isEqualTo(251);
    }

    @Test
    @DisplayName("nothing in R2 yet, or no R2 at all: the copy in the app")
    void notThere() {
        assertThat(new CalcData(new FileRangeSource(dir)).refreshNow()).isEqualTo(CalcData.Outcome.NOT_IN_R2);
        assertThat(new CalcData(null).refreshNow()).isEqualTo(CalcData.Outcome.NO_SOURCE);
        assertThat(Co2.electricityVersion()).isEqualTo("owid-energy-data f8de88a4 (2026-10-01)");
    }
}
