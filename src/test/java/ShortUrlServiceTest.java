import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ShortUrlServiceTest {
    @Test
    void findByShortCode_cacheHit_skipsRepository(){

    ShortCodeGenerator generator = () -> "unused";

    ShortUrlRepository repository = new ShortUrlRepository() {
        @Override
        public void save(ShortUrl shortUrl) {
            throw new AssertionError(
                    "Repository save should not be called"
            );
        }

        @Override
        public Optional<ShortUrl> findByShortCode(String shortCode) {
            throw new AssertionError(
                    "Repository should not be called on cache hit"
            );
        }
    };

    ShortUrlCache cache = new ShortUrlCache() {
        @Override
        public CacheLookupResult findByShortCode(String shortCode) {
            return new CacheLookupResult.Hit(
                    new ShortUrl(
                            "abc123",
                            "https://example.com"
                    )
            );
        }

        @Override
        public void put(ShortUrl shortUrl) {
            throw new AssertionError(
                "Cache put should not be called on cache hit"
            );
        }

        @Override
        public void putNotFound(String shortCode) {
            throw new AssertionError(
              "Negative cache should not be written on cache hit"
            );
        }
    };

    ShortUrlService service = new ShortUrlService(generator, repository, cache);

    ShortUrl result = service.findByShortCode("abc123");

    assertEquals("abc123", result.getShortCode());

    assertEquals("https://example.com", result.getOriginalUrl());
    }

    @Test
    void findByShortCode_cacheMiss_readsRepositoryAndPopulatesCache() {
        // Arrange
        ShortCodeGenerator generator = () -> "unused";

        ShortUrl expected = new ShortUrl(
                "abc123",
                "https://example.com"
        );

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Repository save should not be called"
                );
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                return Optional.of(expected);
            }
        };

        boolean[] cachePopulated = {false};

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                return new CacheLookupResult.Miss();
            }

            @Override
            public void put(ShortUrl shortUrl) {
                cachePopulated[0] = true;
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be written when repository finds the URL"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        // Act
        ShortUrl result =
                service.findByShortCode("abc123");

        // Assert
        assertEquals(
                "abc123",
                result.getShortCode()
        );

        assertEquals(
                "https://example.com",
                result.getOriginalUrl()
        );

        assertTrue(cachePopulated[0]);
    }

    @Test
    void findByShortCode_negativeCacheHit_skipsRepositoryAndThrowsNotFound() {
        // Arrange
        ShortCodeGenerator generator = () -> "unused";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Repository save should not be called"
                );
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                throw new AssertionError(
                        "Repository lookup should not be called on negative cache hit"
                );
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                return new CacheLookupResult.NegativeHit();
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Positive cache should not be written"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be rewritten on negative hit"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        // Act + Assert
        assertThrows(
                ShortUrlNotFoundException.class,
                () -> service.findByShortCode("missing")
        );
    }

    @Test
    void findByShortCode_repositoryMiss_populatesNegativeCacheAndThrowsNotFound() {
        // Arrange
        ShortCodeGenerator generator = () -> "unused";

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Repository save should not be called"
                );
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                return Optional.empty();
            }
        };

        boolean[] negativeCacheWritten = {false};

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                return new CacheLookupResult.Miss();
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Positive cache should not be written"
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                negativeCacheWritten[0] = true;
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        // Act + Assert
        assertThrows(
                ShortUrlNotFoundException.class,
                () -> service.findByShortCode("missing")
        );

        assertTrue(negativeCacheWritten[0]);
    }

    @Test
    void findByShortCode_cacheUnavailable_fallsBackToRepository() {
        // Arrange
        ShortCodeGenerator generator = () -> "unused";

        ShortUrl expected = new ShortUrl(
                "abc123",
                "https://example.com"
        );

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                throw new AssertionError(
                        "Repository save should not be called"
                );
            }

            @Override
            public Optional<ShortUrl> findByShortCode(String shortCode) {
                return Optional.of(expected);
            }
        };

        ShortUrlCache cache = new ShortUrlCache() {
            @Override
            public CacheLookupResult findByShortCode(String shortCode) {
                throw new CacheUnavailableException(
                        "Redis unavailable",
                        new RuntimeException("connection failed")
                );
            }

            @Override
            public void put(ShortUrl shortUrl) {
                throw new CacheUnavailableException(
                        "Redis unavailable",
                        new RuntimeException("connection failed")
                );
            }

            @Override
            public void putNotFound(String shortCode) {
                throw new AssertionError(
                        "Negative cache should not be written"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        // Act
        ShortUrl result =
                service.findByShortCode("abc123");

        // Assert
        assertEquals(
                "abc123",
                result.getShortCode()
        );

        assertEquals(
                "https://example.com",
                result.getOriginalUrl()
        );
    }

    @Test
    void createShortUrl_collision_retriesWithAnotherCode() {
        // Arrange
        String[] codes = {"abc123", "def456"};
        int[] generatorIndex = {0};

        ShortCodeGenerator generator = () ->
                codes[generatorIndex[0]++];

        int[] saveAttempts = {0};

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                saveAttempts[0]++;

                if (saveAttempts[0] == 1) {
                    throw new ShortCodeCollisionException(
                            "Short code already exists"
                    );
                }

                // second save succeeds
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
                        "Negative cache should not be used during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        // Act
        ShortUrl result =
                service.createShortUrl("https://example.com");

        // Assert
        assertEquals(
                "def456",
                result.getShortCode()
        );

        assertEquals(
                "https://example.com",
                result.getOriginalUrl()
        );

        assertEquals(
                2,
                saveAttempts[0]
        );
    }

    @Test
    void createShortUrl_nonCollisionRepositoryFailure_doesNotRetry() {
        // Arrange
        int[] generateCalls = {0};

        ShortCodeGenerator generator = () -> {
            generateCalls[0]++;
            return "abc123";
        };

        int[] saveAttempts = {0};

        ShortUrlRepository repository = new ShortUrlRepository() {
            @Override
            public void save(ShortUrl shortUrl) {
                saveAttempts[0]++;

                throw new RuntimeException(
                        "Database unavailable"
                );
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
                        "Negative cache should not be used during creation"
                );
            }
        };

        ShortUrlService service =
                new ShortUrlService(
                        generator,
                        repository,
                        cache
                );

        // Act + Assert
        assertThrows(
                RuntimeException.class,
                () -> service.createShortUrl(
                        "https://example.com"
                )
        );

        assertEquals(1, generateCalls[0]);
        assertEquals(1, saveAttempts[0]);
    }
}
