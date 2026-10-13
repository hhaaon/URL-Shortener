import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class PostgresShortUrlRepository implements ShortUrlRepository{

    private final DataSource dataSource;

    public PostgresShortUrlRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(ShortUrl shortUrl) {
        String sql = """
                INSERT INTO short_urls (short_code, original_url)
                VALUES(?, ?)
                """;

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, shortUrl.getShortCode());
            statement.setString(2, shortUrl.getOriginalUrl());

            statement.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new ShortCodeCollisionException(
                        "Short code already exists: " + shortUrl.getShortCode()
                );
            }

            throw new RuntimeException("Failed to save short URL", e);
        }
    }

    @Override
    public Optional<ShortUrl> findByShortCode(String shortCode) {
        String sql = """
                SELECT short_code, original_url
                FROM short_urls
                WHERE short_code = ?
                """;

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, shortCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new ShortUrl(
                        resultSet.getString("short_code"),
                        resultSet.getString("original_url")
                )
                );
            }
        }

        catch (SQLException e) {
            throw new RuntimeException("Failed to find short URL", e);
        }

    }
}
