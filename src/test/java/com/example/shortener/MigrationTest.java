package com.example.shortener;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigrationTest {
    @Test void v2PreservesExistingLinkAndEnablesIt() throws Exception {
        String url = "jdbc:h2:mem:migration;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into links(code,target_url,created_at,clicks) values('legacy12','https://example.com',CURRENT_TIMESTAMP,7)");
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement();
             var rows = statement.executeQuery("select enabled,clicks,target_url from links where code='legacy12'")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isTrue();
            assertThat(rows.getLong(2)).isEqualTo(7); assertThat(rows.getString(3)).isEqualTo("https://example.com");
        }
    }
}
