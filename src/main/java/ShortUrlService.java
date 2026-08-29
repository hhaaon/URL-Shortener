import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class ShortUrlService {
    private final ShortCodeGenerator shortCodeGenerator;
    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlCache shortUrlCache;
    private static final int MAX_ATTEMPTS = 5;

    public ShortUrlService(ShortCodeGenerator shortCodeGenerator, ShortUrlRepository shortUrlRepository, ShortUrlCache shortUrlCache) {
        this.shortCodeGenerator = shortCodeGenerator;
        this.shortUrlRepository = shortUrlRepository;
        this.shortUrlCache = shortUrlCache;
    }

    public ShortUrl createShortUrl(String originalUrl) {
        validateOriginalUrl(originalUrl);

        for (int i = 0; i < MAX_ATTEMPTS; i++){
            try {
                String shortCode = shortCodeGenerator.generate();
                ShortUrl shortUrl = new ShortUrl(shortCode, originalUrl);

                shortUrlRepository.save(shortUrl);

                return shortUrl;
            } catch (ShortCodeCollisionException e) {
                // Generate a new candidate on the next attempt.
            }
        }

        throw new IllegalStateException(
                "Failed to generate a unique short code after " + MAX_ATTEMPTS + " attempts"
        );
    }

    public ShortUrl findByShortCode(String shortCode) {
        try {
            CacheLookupResult result =
                    shortUrlCache.findByShortCode(shortCode);

            if (result instanceof CacheLookupResult.Hit hit) {
                return hit.shortUrl();
            }

            if (result instanceof CacheLookupResult.NegativeHit) {
                throw new ShortUrlNotFoundException(shortCode);
            }

            if (result instanceof CacheLookupResult.Miss) {
                // now query PostgreSQL
            }

        } catch (CacheUnavailableException e) {
            // Cache is optional for this read.
            // Continue to PostgreSQL.
        }

        Optional<ShortUrl> found =
                shortUrlRepository.findByShortCode(shortCode);

        if (found.isEmpty()){
            try {
                shortUrlCache.putNotFound(shortCode);
            } catch (CacheUnavailableException e) {
                //still return the correct 404
            }

            throw new ShortUrlNotFoundException(shortCode);
        }

        ShortUrl shortUrl = found.get();

        try {
            shortUrlCache.put(shortUrl);
        } catch (CacheUnavailableException e) {
            // PostgreSQL already gave us authoritative data.
            // Cache population failure must not fail the request.
        }

        return shortUrl;
    }


    private void validateOriginalUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }

        final URI uri;

        try {
            uri = new URI(originalUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL syntax", e);
        }

        String scheme = uri.getScheme();

        if (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "URL must use http or https"
            );
        }

        if (uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "URL must have a host"
            );
        }
    }
}
