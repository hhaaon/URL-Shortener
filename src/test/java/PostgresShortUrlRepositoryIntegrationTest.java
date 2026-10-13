import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PostgresShortUrlRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("url_shortener_test")
                    .withUsername("test_user")
                    .withPassword("test_password");

    private PostgresShortUrlRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource =
                DatabaseConfig.createDataSource(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(5432),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                );

        try (
                Connection connection =
                        dataSource.getConnection();

                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS short_urls (
                        short_code VARCHAR(6) PRIMARY KEY,
                        original_url TEXT NOT NULL
                    )
                    """);

            statement.execute(
                    "DELETE FROM short_urls"
            );
        }

        repository =
                new PostgresShortUrlRepository(
                        dataSource
                );
    }

    @Test
    void saveAndFindByShortCode_roundTripsThroughPostgres() {
        ShortUrl shortUrl =
                new ShortUrl(
                        "abc123",
                        "https://example.com"
                );

        repository.save(shortUrl);

        Optional<ShortUrl> found =
                repository.findByShortCode(
                        "abc123"
                );

        assertTrue(found.isPresent());

        assertEquals(
                "abc123",
                found.get().getShortCode()
        );

        assertEquals(
                "https://example.com",
                found.get().getOriginalUrl()
        );
    }

    @Test
    void findByShortCode_missingCode_returnsEmpty() {
        Optional<ShortUrl> found =
                repository.findByShortCode(
                        "ZZZZZZ"
                );

        assertTrue(found.isEmpty());
    }

    @Test
    void save_duplicateShortCode_translatesUniqueViolationToCollision() {
        repository.save(
                new ShortUrl(
                        "abc123",
                        "https://first.example.com"
                )
        );

        ShortCodeCollisionException exception =
                assertThrows(
                        ShortCodeCollisionException.class,
                        () -> repository.save(
                                new ShortUrl(
                                        "abc123",
                                        "https://second.example.com"
                                )
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("abc123")
        );
    }
}