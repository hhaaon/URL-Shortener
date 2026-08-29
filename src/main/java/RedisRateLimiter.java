import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.List;

public class RedisRateLimiter implements RateLimiter {

    private static final int CAPACITY = 20;
    private static final int REFILL_INTERVAL_SECONDS = 3;
    private static final int BUCKET_TTL_SECONDS = 120;

    private static final String TOKEN_BUCKET_SCRIPT = """
        local key = KEYS[1]

        local capacity = tonumber(ARGV[1])
        local refillInterval = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        local bucketTtl = tonumber(ARGV[4])

        local bucket = redis.call(
            'HMGET',
            key,
            'tokens',
            'lastRefill'
        )

        local tokens = tonumber(bucket[1])
        local lastRefill = tonumber(bucket[2])

        if tokens == nil or lastRefill == nil then
            tokens = capacity
            lastRefill = now
        else
            local elapsed = math.max(0, now - lastRefill)
            local refilled = math.floor(elapsed / refillInterval)

            if refilled > 0 then
                tokens = math.min(
                    capacity,
                    tokens + refilled
                )

                lastRefill =
                    lastRefill + refilled * refillInterval
            end
        end

        if tokens > 0 then
            tokens = tokens - 1

            redis.call(
                'HSET',
                key,
                'tokens', tokens,
                'lastRefill', lastRefill
            )

            redis.call('EXPIRE', key, bucketTtl)

            return {1, 0}
        end

        redis.call(
            'HSET',
            key,
            'tokens', tokens,
            'lastRefill', lastRefill
        )

        redis.call('EXPIRE', key, bucketTtl)

        local elapsed =
            math.max(0, now - lastRefill)

        local retryAfter =
            refillInterval - (elapsed % refillInterval)

        return {0, retryAfter}
        """;

    private final RedisClient redisClient;

    public RedisRateLimiter(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public RateLimitDecision tryAcquire(String clientIp) {
        try {
            return executeLimiter(clientIp);
        }
        catch (JedisConnectionException firstFailure) {
            try {
                return executeLimiter(clientIp);
            }
            catch (JedisConnectionException secondFailure) {
                throw new RateLimiterUnavailableException(
                        "Rate limiter unavailable after retry",
                        secondFailure
                );
            }
        }
    }

    private RateLimitDecision executeLimiter(String clientIp) {
        String redisKey =
                "rate-limit:create-url:" + clientIp;

        long now =
                System.currentTimeMillis() / 1000;

        Object rawResult = redisClient.eval(
                TOKEN_BUCKET_SCRIPT,
                List.of(redisKey),
                List.of(
                        String.valueOf(CAPACITY),
                        String.valueOf(REFILL_INTERVAL_SECONDS),
                        String.valueOf(now),
                        String.valueOf(BUCKET_TTL_SECONDS)
                )
        );

        List<?> result = (List<?>) rawResult;

        long allowed =
                ((Number) result.get(0)).longValue();

        long retryAfterSeconds =
                ((Number) result.get(1)).longValue();

        if (allowed == 1) {
            return new RateLimitDecision.Allowed();
        }

        return new RateLimitDecision.Rejected(
                retryAfterSeconds
        );
    }
}
