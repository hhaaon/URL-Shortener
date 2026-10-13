import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.RedisClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Testcontainers
class RedisShortUrlCacheIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            )
                    .withExposedPorts(6379);

    private RedisClient redisClient;
    private RedisShortUrlCache cache;

    @BeforeEach
    void setUp() {
        redisClient =
                RedisClient.create(
                        REDIS.getHost(),
                        REDIS.getMappedPort(6379)
                );

        redisClient.flushDB();

        cache =
                new RedisShortUrlCache(
                        redisClient
                );
    }

    @AfterEach
    void tearDown() {
        redisClient.close();
    }

    @Test
    void putAndFindByShortCode_roundTripsThroughRedis() {
        ShortUrl shortUrl =
                new ShortUrl(
                        "abc123",
                        "https://example.com"
                );

        cache.put(shortUrl);

        CacheLookupResult result =
                cache.findByShortCode(
                        "abc123"
                );

        CacheLookupResult.Hit hit =
                assertInstanceOf(
                        CacheLookupResult.Hit.class,
                        result
                );

        assertEquals(
                "abc123",
                hit.shortUrl().getShortCode()
        );

        assertEquals(
                "https://example.com",
                hit.shortUrl().getOriginalUrl()
        );
    }

    @Test
    void findByShortCode_missingKey_returnsMiss() {
        CacheLookupResult result =
                cache.findByShortCode(
                        "missing"
                );

        assertInstanceOf(
                CacheLookupResult.Miss.class,
                result
        );
    }

    @Test
    void putNotFoundAndFind_returnsNegativeHit() {
        cache.putNotFound(
                "ZZZZZZ"
        );

        CacheLookupResult result =
                cache.findByShortCode(
                        "ZZZZZZ"
                );

        assertInstanceOf(
                CacheLookupResult.NegativeHit.class,
                result
        );
    }
}