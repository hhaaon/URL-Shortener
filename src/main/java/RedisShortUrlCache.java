import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.params.SetParams;

public class RedisShortUrlCache implements ShortUrlCache{

    private static final String NOT_FOUND_MARKER = "__NOT_FOUND__";

    private static final int URL_TTL_SECONDS = 300;
    private static final int NOT_FOUND_TTL_SECONDS = 30;

    private final RedisClient redisClient;

    public RedisShortUrlCache(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public CacheLookupResult findByShortCode(String shortCode) {
        try {
            String value =
                    redisClient.get(key(shortCode));

            if (value == null) {
                return new CacheLookupResult.Miss();
            }

            if (NOT_FOUND_MARKER.equals(value)) {
                return new CacheLookupResult.NegativeHit();
            }

            return new CacheLookupResult.Hit(new ShortUrl(shortCode, value));
        } catch (JedisConnectionException e) {
            throw new CacheUnavailableException(
                    "Redis cache unavailable",
                    e
            );
        }
    }

    @Override
    public void put(ShortUrl shortUrl) {
        try {
            redisClient.set(
                    key(shortUrl.getShortCode()),
                    shortUrl.getOriginalUrl(),
                    SetParams.setParams().ex(URL_TTL_SECONDS)
            );
        } catch (JedisConnectionException e) {
            throw new CacheUnavailableException(
                    "Redis cache unavailable",
                    e
            );
        }
    }

    @Override
    public void putNotFound(String shortCode){
        try {
            redisClient.set(
                    key(shortCode),
                    NOT_FOUND_MARKER,
                    SetParams.setParams().ex(NOT_FOUND_TTL_SECONDS)
            );
        } catch (JedisConnectionException e) {
            throw new CacheUnavailableException(
                    "Redis cache unavailable",
                    e
            );
        }
    }

    private String key(String shortCode) {
        return "short-url:" + shortCode;
    }
}
