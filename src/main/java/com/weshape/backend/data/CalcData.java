package com.weshape.backend.data;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.weshape.backend.calc.Co2;
import com.weshape.backend.calc.ElectricityTable;
import com.weshape.backend.wind.RangeSource;
import com.weshape.backend.wind.RangeSources;

/**
 * Keeps the calculation data current without a new release. At start and every 6 hours it reads the
 * tables the "Refresh calculation data" workflow uploads to R2 (now: {@value #ELECTRICITY_KEY}),
 * checks them, and puts them in use. A missing, unreadable or failing file changes nothing: the table
 * in use stays (at first the copy shipped with the app).
 */
@Component
public class CalcData {

    private static final Logger log = LoggerFactory.getLogger(CalcData.class);

    public static final String ELECTRICITY_KEY = "data/electricity-co2.csv";
    static final long REFRESH_MS = 6 * 60 * 60 * 1000L;
    static final long RETRY_MS = 5 * 60 * 1000L;

    /** What a refresh did. */
    public enum Outcome { UPDATED, UNCHANGED, NOT_IN_R2, REJECTED, UNREADABLE, NO_SOURCE }

    private final RangeSource source;

    @Autowired
    public CalcData(
            @Value("${weshape.wind.r2.account-id:}") String accountId,
            @Value("${weshape.wind.r2.bucket:}") String bucket,
            @Value("${weshape.wind.r2.access-key-id:}") String accessKey,
            @Value("${weshape.wind.r2.secret-access-key:}") String secretKey,
            @Value("${weshape.wind.local-dir:}") String localDir) {
        this(RangeSources.configured(accountId, bucket, accessKey, secretKey, localDir));
        if (source != null) {
            Thread.ofVirtual().name("calc-data").start(this::loop);
        }
    }

    /** For tests: no background thread; call {@link #refreshNow()}. */
    CalcData(RangeSource source) {
        this.source = source;
    }

    /** Reads the tables from R2 once and puts a checked newer one in use. */
    public Outcome refreshNow() {
        if (source == null) {
            return Outcome.NO_SOURCE;
        }
        String text;
        try {
            text = new String(source.readAll(ELECTRICITY_KEY), StandardCharsets.UTF_8);
        } catch (FileNotFoundException e) {
            log.debug("Calculation data: no {} in R2, using the copy in the app", ELECTRICITY_KEY);
            return Outcome.NOT_IN_R2;
        } catch (IOException | RuntimeException e) {
            log.warn("Calculation data: {} unreadable from {}: {}", ELECTRICITY_KEY, source.describe(), e.getMessage());
            return Outcome.UNREADABLE;
        }
        ElectricityTable table;
        try {
            table = ElectricityTable.parse(text);
        } catch (IllegalArgumentException e) {
            log.warn("Calculation data: {} in R2 rejected, keeping {}: {}", ELECTRICITY_KEY, Co2.electricityVersion(),
                    e.getMessage());
            return Outcome.REJECTED;
        }
        if (table.version().equals(Co2.electricityVersion())) {
            return Outcome.UNCHANGED;
        }
        Co2.useElectricity(table);
        log.info("Calculation data: electricity CO2 {} ({} countries) from {}", table.version(),
                table.countries().size(), source.describe());
        return Outcome.UPDATED;
    }

    private void loop() {
        while (true) {
            Outcome o = refreshNow();
            try {
                Thread.sleep(o == Outcome.UNREADABLE ? RETRY_MS : REFRESH_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
