package com.weshape.backend.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Electricity table checks")
class ElectricityTableTest {

    private static String rows(int n) {
        StringBuilder b = new StringBuilder("country,year,low,typical,high\n");
        int k = 0;
        for (char a = 'A'; a <= 'Z' && k < n; a++) {
            for (char c = 'A'; c <= 'Z' && k < n; c++, k++) {
                b.append(a).append(c).append(",2025,100,110,120\n");
            }
        }
        return b.toString();
    }

    @Test
    @DisplayName("200 countries are enough; 199 are not")
    void count() {
        assertThat(ElectricityTable.parse("# version: v1\n" + rows(200)).countries()).hasSize(200);
        assertThat(ElectricityTable.parse("# version: v1\n" + rows(200)).version()).isEqualTo("v1");
        assertThat(ElectricityTable.parse(rows(200)).version()).isEqualTo("unknown");
        assertThatThrownBy(() -> ElectricityTable.parse(rows(199))).hasMessageContaining("199");
    }

    @Test
    @DisplayName("each line is checked: header, code, numbers, order, limits, year, duplicates")
    void lines() {
        String ok = rows(200);
        String body = ok.substring(ok.indexOf('\n') + 1);
        assertThatThrownBy(() -> ElectricityTable.parse("land,year,low,typical,high\n" + body)).hasMessageContaining("header");
        for (String bad : new String[] {"rou,2025,1,2,3", "R1,2025,100,110,120", "ZZ,2025,100,110", "ZZ,x,100,110,120",
                "ZZ,2025,120,110,100", "ZZ,2025,9,110,120", "ZZ,2025,100,110,1401", "ZZ,2014,100,110,120",
                "AA,2025,100,110,120"}) {
            assertThatThrownBy(() -> ElectricityTable.parse(ok + bad + "\n")).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(ElectricityTable.parse(ok + "ZZ,2025,10,110,1400\n").countries()).hasSize(201);
    }

    @Test
    @DisplayName("the copy shipped with the app passes its own checks")
    void bundled() {
        assertThat(Co2.countries()).isEqualTo(212);
        assertThat(Co2.electricityVersion()).startsWith("owid-energy-data");
    }
}
