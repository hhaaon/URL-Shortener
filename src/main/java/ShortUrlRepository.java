import java.util.Optional;

public interface ShortUrlRepository {
    void save(ShortUrl shortUrl);
    Optional<ShortUrl> findByShortCode(String shortCode);
}