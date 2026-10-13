import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UrlShortenerHttpServerTest {

    @Test
    void createUrl_rateLimited_returns429WithRetryAfter()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "unused";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Repository should not be called when rate limited"
                );
            }

            @Override
            public java.util.Optional<ShortUrl> findByShortCode(
                    String shortCode
            ) {
                throw new AssertionError(
                        "Repository should not be called when rate limited"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache should not be called when rate limited"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache should not be called when rate limited"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Cache should not be called when rate limited"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        RateLimiter rateLimiter =
                key -> new RateLimitDecision.Rejected(3);

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9090,
                        "http://127.0.0.1:9092",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9090/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(
                    429,
                    response.statusCode()
            );

            assertEquals(
                    "3",
                    response.headers()
                            .firstValue("Retry-After")
                            .orElseThrow()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_rateLimiterUnavailable_returns503()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "unused";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Repository should not be called when limiter is unavailable"
                );
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Repository should not be called when limiter is unavailable"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache should not be called when limiter is unavailable"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache should not be called when limiter is unavailable"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Cache should not be called when limiter is unavailable"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        RateLimiter rateLimiter = key -> {
            throw new RateLimiterUnavailableException(
                    "Redis unavailable",
                    new RuntimeException("connection failed")
            );
        };

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9091,
                        "http://127.0.0.1:9092",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9091/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(
                    503,
                    response.statusCode()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_allowedAndValid_returns201()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "abc123";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                // success
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Lookup should not be called during creation"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache lookup should not be called during creation"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache put should not be called during creation"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be called during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        RateLimiter rateLimiter =
                key -> new RateLimitDecision.Allowed();

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9092,
                        "http://127.0.0.1:9092",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9092/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(
                    201,
                    response.statusCode()
            );

            assertTrue(
                    response.body().contains(
                            "http://127.0.0.1:9092/abc123"
                    )
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_invalidUrl_returns400()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "abc123";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Repository save should not be called for invalid URL"
                );
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Repository lookup should not be called during creation"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache lookup should not be called during creation"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache put should not be called during creation"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be called during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        RateLimiter rateLimiter =
                key -> new RateLimitDecision.Allowed();

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9093,
                        "http://127.0.0.1:9092",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9093/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"ftp://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(
                    400,
                    response.statusCode()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_noTrustedProxy_ignoresForwardedForForRateLimitKey()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "abc123";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                // success
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Lookup should not be called during creation"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache lookup should not be called during creation"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache put should not be called during creation"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be called during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        AtomicReference<String> capturedKey =
                new AtomicReference<>();

        RateLimiter rateLimiter = key -> {
            capturedKey.set(key);
            return new RateLimitDecision.Allowed();
        };

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9094,
                        "http://127.0.0.1:9094",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9094/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "X-Forwarded-For",
                                    "203.0.113.25"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(201, response.statusCode());

            assertEquals(
                    "127.0.0.1",
                    capturedKey.get()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_untrustedPeer_ignoresForwardedForForRateLimitKey()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "abc123";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                // success
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Lookup should not be called during creation"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache lookup should not be called during creation"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache put should not be called during creation"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be called during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        AtomicReference<String> capturedKey =
                new AtomicReference<>();

        RateLimiter rateLimiter = key -> {
            capturedKey.set(key);
            return new RateLimitDecision.Allowed();
        };

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9095,
                        "http://127.0.0.1:9095",
                        "192.0.2.10"
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9095/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "X-Forwarded-For",
                                    "203.0.113.25"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(201, response.statusCode());

            assertEquals(
                    "127.0.0.1",
                    capturedKey.get()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_trustedProxy_usesForwardedClientForRateLimitKey()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "abc123";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                // success
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Lookup should not be called during creation"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache lookup should not be called during creation"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache put should not be called during creation"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be called during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        AtomicReference<String> capturedKey =
                new AtomicReference<>();

        RateLimiter rateLimiter = key -> {
            capturedKey.set(key);
            return new RateLimitDecision.Allowed();
        };

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9096,
                        "http://127.0.0.1:9096",
                        "127.0.0.1"
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9096/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "X-Forwarded-For",
                                    "203.0.113.25"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(201, response.statusCode());

            assertEquals(
                    "203.0.113.25",
                    capturedKey.get()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_trustedProxy_missingForwardedFor_usesPeerForRateLimitKey()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "abc123";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                // success
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Lookup should not be called during creation"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache lookup should not be called during creation"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache put should not be called during creation"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be called during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        AtomicReference<String> capturedKey =
                new AtomicReference<>();

        RateLimiter rateLimiter = key -> {
            capturedKey.set(key);
            return new RateLimitDecision.Allowed();
        };

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9097,
                        "http://127.0.0.1:9097",
                        "127.0.0.1"
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9097/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(201, response.statusCode());

            assertEquals(
                    "127.0.0.1",
                    capturedKey.get()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_trustedProxy_multipleForwardedValues_usesFirstValueForRateLimitKey()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "abc123";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                // success
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Lookup should not be called during creation"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache lookup should not be called during creation"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache put should not be called during creation"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be called during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        AtomicReference<String> capturedKey =
                new AtomicReference<>();

        RateLimiter rateLimiter = key -> {
            capturedKey.set(key);
            return new RateLimitDecision.Allowed();
        };

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9098,
                        "http://127.0.0.1:9098",
                        "127.0.0.1"
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9098/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "X-Forwarded-For",
                                    "203.0.113.25, 198.51.100.8"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(201, response.statusCode());

            assertEquals(
                    "203.0.113.25",
                    capturedKey.get()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_trustedProxy_blankFirstForwardedValue_usesPeerForRateLimitKey()
            throws Exception {

        // Arrange
        ShortCodeGenerator generator = () -> "abc123";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                // success
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Lookup should not be called during creation"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Cache lookup should not be called during creation"
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Cache put should not be called during creation"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be called during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        AtomicReference<String> capturedKey =
                new AtomicReference<>();

        RateLimiter rateLimiter = key -> {
            capturedKey.set(key);
            return new RateLimitDecision.Allowed();
        };

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9099,
                        "http://127.0.0.1:9099",
                        "127.0.0.1"
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9099/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "X-Forwarded-For",
                                    ", 203.0.113.25"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            // Act
            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            // Assert
            assertEquals(201, response.statusCode());

            assertEquals(
                    "127.0.0.1",
                    capturedKey.get()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_jsonNull_returns400()
            throws Exception {

        ShortCodeGenerator generator =
                () -> "abc123";

        ShortUrlRepository repository =
                new ShortUrlRepository() {

                    @Override
                    public void save(ShortUrl shortUrl) {
                        throw new AssertionError(
                                "Repository should not be called"
                        );
                    }

                    @Override
                    public Optional<ShortUrl> findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Repository lookup should not be called"
                        );
                    }
                };

        ShortUrlCache cache =
                new ShortUrlCache() {

                    @Override
                    public CacheLookupResult findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Cache lookup should not be called"
                        );
                    }

                    @Override
                    public void put(ShortUrl shortUrl) {
                        throw new AssertionError(
                                "Cache put should not be called"
                        );
                    }

                    @Override
                    public void putNotFound(String shortCode) {
                        throw new AssertionError(
                                "Negative cache should not be called"
                        );
                    }
                };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        RateLimiter rateLimiter =
                key -> new RateLimitDecision.Allowed();

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9100,
                        "http://127.0.0.1:9100",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9100/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString("null")
                            )
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            assertEquals(
                    400,
                    response.statusCode()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_oversizedBody_returns413()
            throws Exception {

        ShortCodeGenerator generator =
                () -> "abc123";

        ShortUrlRepository repository =
                new ShortUrlRepository() {

                    @Override
                    public void save(ShortUrl shortUrl) {
                        throw new AssertionError(
                                "Repository should not be called for oversized request"
                        );
                    }

                    @Override
                    public Optional<ShortUrl> findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Repository lookup should not be called"
                        );
                    }
                };

        ShortUrlCache cache =
                new ShortUrlCache() {

                    @Override
                    public CacheLookupResult findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Cache lookup should not be called"
                        );
                    }

                    @Override
                    public void put(ShortUrl shortUrl) {
                        throw new AssertionError(
                                "Cache put should not be called"
                        );
                    }

                    @Override
                    public void putNotFound(String shortCode) {
                        throw new AssertionError(
                                "Negative cache should not be called"
                        );
                    }
                };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        RateLimiter rateLimiter =
                key -> new RateLimitDecision.Allowed();

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9101,
                        "http://127.0.0.1:9101",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            String oversizedUrl =
                    "https://example.com/"
                            + "a".repeat(8 * 1024);

            String body =
                    "{\"url\":\""
                            + oversizedUrl
                            + "\"}";

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9101/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(body)
                            )
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            assertEquals(
                    413,
                    response.statusCode()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_unexpectedServiceFailure_returns500()
            throws Exception {

        ShortCodeGenerator generator =
                () -> "abc123";

        ShortUrlRepository repository =
                new ShortUrlRepository() {

                    @Override
                    public void save(ShortUrl shortUrl) {
                        throw new RuntimeException(
                                "database failure"
                        );
                    }

                    @Override
                    public Optional<ShortUrl> findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Repository lookup should not be called during creation"
                        );
                    }
                };

        ShortUrlCache cache =
                new ShortUrlCache() {

                    @Override
                    public CacheLookupResult findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Cache lookup should not be called during creation"
                        );
                    }

                    @Override
                    public void put(ShortUrl shortUrl) {
                        throw new AssertionError(
                                "Cache put should not be called after repository failure"
                        );
                    }

                    @Override
                    public void putNotFound(String shortCode) {
                        throw new AssertionError(
                                "Negative cache should not be called during creation"
                        );
                    }
                };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        RateLimiter rateLimiter =
                key -> new RateLimitDecision.Allowed();

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9102,
                        "http://127.0.0.1:9102",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9102/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":\"https://example.com\"}"
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            assertEquals(
                    500,
                    response.statusCode()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_missingUrl_returns400()
            throws Exception {

        ShortCodeGenerator generator =
                () -> "abc123";

        ShortUrlRepository repository =
                new ShortUrlRepository() {

                    @Override
                    public void save(ShortUrl shortUrl) {
                        throw new AssertionError(
                                "Repository should not be called"
                        );
                    }

                    @Override
                    public Optional<ShortUrl> findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Repository lookup should not be called"
                        );
                    }
                };

        ShortUrlCache cache =
                new ShortUrlCache() {

                    @Override
                    public CacheLookupResult findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Cache lookup should not be called"
                        );
                    }

                    @Override
                    public void put(ShortUrl shortUrl) {
                        throw new AssertionError(
                                "Cache put should not be called"
                        );
                    }

                    @Override
                    public void putNotFound(String shortCode) {
                        throw new AssertionError(
                                "Negative cache should not be called"
                        );
                    }
                };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        RateLimiter rateLimiter =
                key -> new RateLimitDecision.Allowed();

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9103,
                        "http://127.0.0.1:9103",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9103/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString("{}")
                            )
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            assertEquals(
                    400,
                    response.statusCode()
            );

        } finally {
            server.stop();
        }
    }

    @Test
    void createUrl_nullUrl_returns400()
            throws Exception {

        ShortCodeGenerator generator =
                () -> "abc123";

        ShortUrlRepository repository =
                new ShortUrlRepository() {

                    @Override
                    public void save(ShortUrl shortUrl) {
                        throw new AssertionError(
                                "Repository should not be called"
                        );
                    }

                    @Override
                    public Optional<ShortUrl> findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Repository lookup should not be called"
                        );
                    }
                };

        ShortUrlCache cache =
                new ShortUrlCache() {

                    @Override
                    public CacheLookupResult findByShortCode(
                            String shortCode
                    ) {
                        throw new AssertionError(
                                "Cache lookup should not be called"
                        );
                    }

                    @Override
                    public void put(ShortUrl shortUrl) {
                        throw new AssertionError(
                                "Cache put should not be called"
                        );
                    }

                    @Override
                    public void putNotFound(String shortCode) {
                        throw new AssertionError(
                                "Negative cache should not be called"
                        );
                    }
                };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        RateLimiter rateLimiter =
                key -> new RateLimitDecision.Allowed();

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        9104,
                        "http://127.0.0.1:9104",
                        ""
                );

        server.start();

        try {
            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://127.0.0.1:9104/api/v1/urls"
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"url\":null}"
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            assertEquals(
                    400,
                    response.statusCode()
            );

        } finally {
            server.stop();
        }
    }



}