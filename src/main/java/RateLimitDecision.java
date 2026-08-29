public sealed interface RateLimitDecision
        permits RateLimitDecision.Allowed,
        RateLimitDecision.Rejected {

    record Allowed() implements RateLimitDecision {}

    record Rejected(long retryAfterSeconds)
            implements RateLimitDecision {}
}