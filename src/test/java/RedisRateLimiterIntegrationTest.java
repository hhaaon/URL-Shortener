import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.RedisClient;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class RedisRateLimiterIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            )
                    .withExposedPorts(6379);

    private RedisClient redisClient;
    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisClient =
                RedisClient.create(
                        REDIS.getHost(),
                        REDIS.getMappedPort(6379)
                );

        redisClient.flushDB();

        rateLimiter =
                new RedisRateLimiter(
                        redisClient
                );
    }

    @AfterEach
    void tearDown() {
        redisClient.close();
    }

    @Test
    void tryAcquire_newClient_isAllowed() {
        RateLimitDecision decision =
                rateLimiter.tryAcquire(
                        "203.0.113.10"
                );

        assertInstanceOf(
                RateLimitDecision.Allowed.class,
                decision
        );
    }

    @Test
    void tryAcquire_afterCapacityIsConsumed_isRejected() {
        String clientIp =
                "203.0.113.20";

        for (int i = 0; i < 20; i++) {
            RateLimitDecision decision =
                    rateLimiter.tryAcquire(
                            clientIp
                    );

            assertInstanceOf(
                    RateLimitDecision.Allowed.class,
                    decision
            );
        }

        RateLimitDecision decision =
                rateLimiter.tryAcquire(
                        clientIp
                );

        RateLimitDecision.Rejected rejected =
                assertInstanceOf(
                        RateLimitDecision.Rejected.class,
                        decision
                );

        assertTrue(
                rejected.retryAfterSeconds() > 0
        );

        assertTrue(
                rejected.retryAfterSeconds() <= 3
        );
    }

    @Test
    void tryAcquire_differentClients_haveIndependentBuckets() {
        String firstClient =
                "203.0.113.30";

        String secondClient =
                "203.0.113.31";

        for (int i = 0; i < 20; i++) {
            assertInstanceOf(
                    RateLimitDecision.Allowed.class,
                    rateLimiter.tryAcquire(
                            firstClient
                    )
            );
        }

        assertInstanceOf(
                RateLimitDecision.Rejected.class,
                rateLimiter.tryAcquire(
                        firstClient
                )
        );

        assertInstanceOf(
                RateLimitDecision.Allowed.class,
                rateLimiter.tryAcquire(
                        secondClient
                )
        );
    }
}