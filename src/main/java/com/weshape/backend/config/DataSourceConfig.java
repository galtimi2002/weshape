package com.weshape.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;

/**
 * Accepts either a JDBC URL or the {@code postgres://user:pass@host/db} form that Neon,
 * Render and Heroku hand out, so pasting the connection string straight from the provider
 * dashboard just works.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${spring.datasource.hikari.maximum-pool-size:5}")
    private int maxPoolSize;

    @Bean
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("weshape-pool");
        ds.setMaximumPoolSize(maxPoolSize);

        if (url.startsWith("jdbc:")) {
            ds.setJdbcUrl(url);
            if (!username.isBlank()) {
                ds.setUsername(username);
            }
            if (!password.isBlank()) {
                ds.setPassword(password);
            }
            return ds;
        }

        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            try {
                URI uri = new URI(url);
                String query = uri.getQuery() == null ? "sslmode=require" : uri.getQuery();
                String jdbcUrl = "jdbc:postgresql://" + uri.getHost()
                        + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                        + uri.getPath() + "?" + query;
                ds.setJdbcUrl(jdbcUrl);

                String userInfo = uri.getUserInfo();
                if (userInfo != null) {
                    String[] parts = userInfo.split(":", 2);
                    ds.setUsername(decode(parts[0]));
                    ds.setPassword(parts.length > 1 ? decode(parts[1]) : "");
                } else {
                    ds.setUsername(username);
                    ds.setPassword(password);
                }
                log.info("Converted provider-style DATABASE_URL to JDBC form for host {}", uri.getHost());
                return ds;
            } catch (URISyntaxException e) {
                throw new IllegalStateException("DATABASE_URL is not a valid connection string", e);
            }
        }

        throw new IllegalStateException(
                "DATABASE_URL must start with jdbc:postgresql://, postgres:// or postgresql://");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
