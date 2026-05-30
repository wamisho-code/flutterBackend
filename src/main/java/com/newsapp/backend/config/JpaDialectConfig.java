package com.newsapp.backend.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class JpaDialectConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernateDialectCustomizer(DataSource dataSource) {
        return properties -> {
            try (Connection connection = dataSource.getConnection()) {
                String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
                if (product.contains("postgres")) {
                    properties.put(AvailableSettings.DIALECT, "org.hibernate.dialect.PostgreSQLDialect");
                } else if (product.contains("mysql") || product.contains("mariadb")) {
                    properties.put(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect");
                } else if (product.contains("h2")) {
                    properties.put(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect");
                }
            } catch (Exception ignored) {
                // Fall back to spring.jpa.database-platform from application.properties
            }
        };
    }
}
