public interface RateLimiter {

    RateLimitDecision tryAcquire(String key);
}