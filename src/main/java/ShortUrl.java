public class ShortUrl {
    private final String shortCode;
    private final String originalUrl;

    public ShortUrl(String shortCode, String originalUrl) {
        if (shortCode == null || shortCode.isBlank()) throw new IllegalArgumentException("shortCode is required");
        if (originalUrl == null || originalUrl.isBlank()) throw new IllegalArgumentException("originalUrl is required");

        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }
}
