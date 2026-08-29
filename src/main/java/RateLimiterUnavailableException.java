public class RateLimiterUnavailableException extends RuntimeException {

    public RateLimiterUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}