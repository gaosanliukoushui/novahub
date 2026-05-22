package com.novahub.web.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component("dynamicDb")
@RequiredArgsConstructor
public class DynamicDataSourceHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Override
    public Health health() {
        try {
            if (dataSource instanceof DynamicRoutingDataSource drds) {
                DataSource primary = drds.getDataSource("master");
                if (primary == null) {
                    return Health.down().withDetail("error", "dynamic-datasource can not find primary datasource").build();
                }
                try (Connection conn = primary.getConnection()) {
                    boolean valid = conn.isValid(3);
                    if (valid) {
                        return Health.up()
                                .withDetail("database", conn.getCatalog())
                                .withDetail("master", "OK")
                                .build();
                    }
                }
                return Health.down().withDetail("error", "Connection is not valid").build();
            }
            try (Connection conn = dataSource.getConnection()) {
                boolean valid = conn.isValid(3);
                if (valid) {
                    return Health.up()
                            .withDetail("database", conn.getCatalog())
                            .build();
                }
            }
            return Health.down().withDetail("error", "Connection is not valid").build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
