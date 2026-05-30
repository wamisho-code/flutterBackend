package com.newsapp.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("prod")
public class RailwayDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(RailwayDataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource() {
        // 1. Check if standard SPRING_DATASOURCE_URL is configured
        String springJdbcUrl = env("SPRING_DATASOURCE_URL");
        if (!isBlank(springJdbcUrl)) {
            log.info("Connecting to database using SPRING_DATASOURCE_URL");
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(springJdbcUrl);
            dataSource.setUsername(env("SPRING_DATASOURCE_USERNAME"));
            dataSource.setPassword(env("SPRING_DATASOURCE_PASSWORD"));
            dataSource.setConnectionTimeout(30_000);
            return dataSource;
        }

        // 2. Check for Render / Heroku / generic PostgreSQL: DATABASE_URL
        String databaseUrl = env("DATABASE_URL");
        DbUrlParts parsedPg = parseDbUrl(databaseUrl);
        if (parsedPg != null && "postgresql".equals(parsedPg.driver())) {
            String jdbcUrl = "jdbc:postgresql://" + parsedPg.host() + ":" + parsedPg.port() + "/" + parsedPg.database()
                    + "?sslmode=require";
            log.info("Connecting to PostgreSQL at {}:{}/{}", parsedPg.host(), parsedPg.port(), parsedPg.database());
            
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setJdbcUrl(jdbcUrl);
            dataSource.setUsername(parsedPg.username());
            dataSource.setPassword(parsedPg.password() != null ? parsedPg.password() : "");
            dataSource.setConnectionTimeout(30_000);
            return dataSource;
        }

        // 3. Check for Railway / generic MySQL: MYSQLHOST or MYSQL_URL
        String host = env("MYSQLHOST");
        String port = env("MYSQLPORT");
        String database = env("MYSQLDATABASE");
        String username = env("MYSQLUSER");
        String password = env("MYSQLPASSWORD");

        if (isBlank(host)) {
            DbUrlParts parsedMysql = parseDbUrl(env("MYSQL_URL"));
            if (parsedMysql != null && "mysql".equals(parsedMysql.driver())) {
                host = parsedMysql.host();
                port = parsedMysql.port();
                database = parsedMysql.database();
                username = parsedMysql.username();
                password = parsedMysql.password();
            }
        }

        if (isBlank(host)) {
            // Also check if DATABASE_URL was parsed as mysql
            if (parsedPg != null && "mysql".equals(parsedPg.driver())) {
                host = parsedPg.host();
                port = parsedPg.port();
                database = parsedPg.database();
                username = parsedPg.username();
                password = parsedPg.password();
            }
        }

        if (isBlank(host)) {
            throw new IllegalStateException(
                    "Database not configured in production environment. " +
                    "Set DATABASE_URL (PostgreSQL/MySQL), MYSQL_URL/MYSQLHOST (MySQL), or SPRING_DATASOURCE_URL.");
        }

        if (isBlank(port)) {
            port = "3306";
        }
        if (isBlank(database)) {
            database = "railway";
        }

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        log.info("Connecting to MySQL at {}:{}/{}", host, port, database);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password != null ? password : "");
        dataSource.setConnectionTimeout(30_000);
        return dataSource;
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value != null ? value.trim() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static DbUrlParts parseDbUrl(String url) {
        if (isBlank(url)) {
            return null;
        }

        String scheme;
        String defaultPort;
        String driver;
        String replacedUrl;

        if (url.startsWith("mysql://")) {
            scheme = "mysql://";
            driver = "mysql";
            defaultPort = "3306";
            replacedUrl = url.replace("mysql://", "http://");
        } else if (url.startsWith("postgres://")) {
            scheme = "postgres://";
            driver = "postgresql";
            defaultPort = "5432";
            replacedUrl = url.replace("postgres://", "http://");
        } else if (url.startsWith("postgresql://")) {
            scheme = "postgresql://";
            driver = "postgresql";
            defaultPort = "5432";
            replacedUrl = url.replace("postgresql://", "http://");
        } else {
            return null;
        }

        try {
            URI uri = URI.create(replacedUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : Integer.parseInt(defaultPort);
            String database = uri.getPath() != null && uri.getPath().length() > 1
                    ? uri.getPath().substring(1)
                    : "";

            String userInfo = uri.getUserInfo();
            String username = null;
            String password = null;
            if (userInfo != null && !userInfo.isBlank()) {
                int separator = userInfo.indexOf(':');
                if (separator >= 0) {
                    username = decode(userInfo.substring(0, separator));
                    password = decode(userInfo.substring(separator + 1));
                } else {
                    username = decode(userInfo);
                }
            }

            return new DbUrlParts(driver, host, String.valueOf(port), database, username, password);
        } catch (Exception ex) {
            log.warn("Could not parse database URL: {}", ex.getMessage());
            return null;
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record DbUrlParts(
            String driver,
            String host,
            String port,
            String database,
            String username,
            String password
    ) {
    }
}
