import java.util.Optional;

public interface ShortUrlCache {

    CacheLookupResult findByShortCode(String shortCode);

    void put(ShortUrl shortUrl);

    void putNotFound(String shortCode);
}
