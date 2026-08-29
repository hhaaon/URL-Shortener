import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryShortUrlRepository implements ShortUrlRepository {
    private final Map<String, ShortUrl> storage = new HashMap<>();

    @Override
    public void save(ShortUrl shortUrl) {
        storage.put(shortUrl.getShortCode(), shortUrl);
    }

    @Override
    public Optional<ShortUrl> findByShortCode(String shortCode) {
        return Optional.ofNullable(storage.get(shortCode));
    }
}