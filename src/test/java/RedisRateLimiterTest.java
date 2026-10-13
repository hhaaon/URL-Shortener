import org.junit.jupiter.api.Test;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRateLimiterTest {

    @Test
    void tryAcquire_nonConnectionJedisFailure_translatesToUnavailableWithoutRetry() {
        RedisClient redisClient =
                mock(RedisClient.class);

        JedisDataException jedisFailure =
                new JedisDataException(
                        "Redis command failed"
                );

        when(
                redisClient.eval(
                        anyString(),
                        anyList(),
                        anyList()
                )
        ).thenThrow(jedisFailure);

        RedisRateLimiter limiter =
                new RedisRateLimiter(redisClient);

        RateLimiterUnavailableException exception =
                assertThrows(
                        RateLimiterUnavailableException.class,
                        () -> limiter.tryAcquire(
                                "203.0.113.25"
                        )
                );

        assertSame(
                jedisFailure,
                exception.getCause()
        );

        verify(
                redisClient,
                times(1)
        ).eval(
                anyString(),
                anyList(),
                anyList()
        );
    }

    @Test
    void tryAcquire_connectionFailure_retriesOnceThenTranslatesSecondFailure() {
        RedisClient redisClient =
                mock(RedisClient.class);

        JedisConnectionException firstFailure =
                new JedisConnectionException(
                        "Connection failed"
                );

        JedisDataException secondFailure =
                new JedisDataException(
                        "Redis command failed"
                );

        when(
                redisClient.eval(
                        anyString(),
                        anyList(),
                        anyList()
                )
        )
                .thenThrow(firstFailure)
                .thenThrow(secondFailure);

        RedisRateLimiter limiter =
                new RedisRateLimiter(redisClient);

        RateLimiterUnavailableException exception =
                assertThrows(
                        RateLimiterUnavailableException.class,
                        () -> limiter.tryAcquire(
                                "203.0.113.25"
                        )
                );

        assertSame(
                secondFailure,
                exception.getCause()
        );

        verify(
                redisClient,
                times(2)
        ).eval(
                anyString(),
                anyList(),
                anyList()
        );
    }

    @Test
    void tryAcquire_twoConnectionFailures_retriesOnceThenTranslatesToUnavailable() {
        RedisClient redisClient =
                mock(RedisClient.class);

        JedisConnectionException firstFailure =
                new JedisConnectionException(
                        "First connection failed"
                );

        JedisConnectionException secondFailure =
                new JedisConnectionException(
                        "Second connection failed"
                );

        when(
                redisClient.eval(
                        anyString(),
                        anyList(),
                        anyList()
                )
        )
                .thenThrow(firstFailure)
                .thenThrow(secondFailure);

        RedisRateLimiter limiter =
                new RedisRateLimiter(redisClient);

        RateLimiterUnavailableException exception =
                assertThrows(
                        RateLimiterUnavailableException.class,
                        () -> limiter.tryAcquire(
                                "203.0.113.25"
                        )
                );

        assertSame(
                secondFailure,
                exception.getCause()
        );

        verify(
                redisClient,
                times(2)
        ).eval(
                anyString(),
                anyList(),
                anyList()
        );
    }
}