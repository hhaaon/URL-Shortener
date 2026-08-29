import com.sun.net.httpserver.HttpServer;
import redis.clients.jedis.RedisClient;

public class Main {

    private HttpServer server;

    public static void main(String[] args) throws Exception {
        String dbHost =
                ApplicationConfig.getOrDefault(
                        "DB_HOST",
                        "127.0.0.1"
                );

        int dbPort =
                ApplicationConfig.getIntOrDefault(
                        "DB_PORT",
                        5433
                );

        String dbName =
                ApplicationConfig.getOrDefault(
                        "DB_NAME",
                        "url_shortener"
                );

        String dbUser =
                ApplicationConfig.getOrDefault(
                        "DB_USER",
                        "postgres"
                );

        String dbPassword =
                ApplicationConfig.getRequired(
                        "DB_PASSWORD"
                );

        var dataSource =
                DatabaseConfig.createDataSource(
                        dbHost,
                        dbPort,
                        dbName,
                        dbUser,
                        dbPassword
                );

        ShortUrlRepository repository =
                new PostgresShortUrlRepository(dataSource);

        ShortCodeGenerator generator =
                new RandomShortCodeGenerator();

        String redisHost =
                ApplicationConfig.getOrDefault(
                        "REDIS_HOST",
                        "localhost"
                );

        int redisPort =
                ApplicationConfig.getIntOrDefault(
                        "REDIS_PORT",
                        6379
                );

        int appPort =
                ApplicationConfig.getIntOrDefault(
                        "APP_PORT",
                        8080
                );

        String publicBaseUrl =
                ApplicationConfig.getOrDefault(
                        "PUBLIC_BASE_URL",
                        "http://localhost:8080"
                );

        String trustedProxyIp =
                ApplicationConfig.getOrDefault(
                        "TRUSTED_PROXY_IP",
                        ""
                );

        RedisClient redisClient =
                RedisClient.create(
                        redisHost,
                        redisPort
                );

        ShortUrlCache cache =
                new RedisShortUrlCache(redisClient);

        ShortUrlService service =
                new ShortUrlService(generator, repository, cache);

        RateLimiter rateLimiter =
                new RedisRateLimiter(redisClient);

        UrlShortenerHttpServer server =
                new UrlShortenerHttpServer(
                        service,
                        rateLimiter,
                        appPort,
                        publicBaseUrl,
                        trustedProxyIp
                );

        server.start();
    }
}