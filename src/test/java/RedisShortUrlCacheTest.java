import org.junit.jupiter.api.Test;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.SetParams;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisShortUrlCacheTest {

    @Test
    void findByShortCode_jedisDataFailure_translatesToCacheUnavailable() {
        RedisClient redisClient = mock(RedisClient.class);

        JedisDataException jedisFailure =
                new JedisDataException("WRONGTYPE");

        when(redisClient.get(anyString()))
                .thenThrow(jedisFailure);

        RedisShortUrlCache cache =
                new RedisShortUrlCache(redisClient);

        CacheUnavailableException exception =
                assertThrows(
                        CacheUnavailableException.class,
                        () -> cache.findByShortCode("abc123")
                );

        assertSame(
                jedisFailure,
                exception.getCause()
        );
    }

    @Test
    void put_jedisDataFailure_translatesToCacheUnavailable() {
        RedisClient redisClient = mock(RedisClient.class);

        JedisDataException jedisFailure =
                new JedisDataException(
                        "Redis SET failed"
                );

        when(
                redisClient.set(
                        anyString(),
                        anyString(),
                        any(SetParams.class)
                )
        ).thenThrow(jedisFailure);

        RedisShortUrlCache cache =
                new RedisShortUrlCache(redisClient);

        ShortUrl shortUrl =
                new ShortUrl(
                        "abc123",
                        "https://example.com"
                );

        CacheUnavailableException exception =
                assertThrows(
                        CacheUnavailableException.class,
                        () -> cache.put(shortUrl)
                );

        assertSame(
                jedisFailure,
                exception.getCause()
        );
    }

    @Test
    void putNotFound_jedisDataFailure_translatesToCacheUnavailable() {
        RedisClient redisClient = mock(RedisClient.class);

        JedisDataException jedisFailure =
                new JedisDataException(
                        "Redis SET failed"
                );

        when(
                redisClient.set(
                        anyString(),
                        anyString(),
                        any(SetParams.class)
                )
        ).thenThrow(jedisFailure);

        RedisShortUrlCache cache =
                new RedisShortUrlCache(redisClient);

        CacheUnavailableException exception =
                assertThrows(
                        CacheUnavailableException.class,
                        () -> cache.putNotFound("missing")
                );

        assertSame(
                jedisFailure,
                exception.getCause()
        );
    }
}