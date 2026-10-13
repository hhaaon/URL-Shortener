import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class UrlShortenerHttpServer {

    private final ShortUrlService shortUrlService;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String trustedProxyIp;
    private final int port;
    private HttpServer server;
    private final String publicBaseUrl;


    public UrlShortenerHttpServer
            (ShortUrlService shortUrlService,
             RateLimiter rateLimiter,
             int port, String publicBaseurl,
             String trustedProxyIp)
    {
        this.shortUrlService = shortUrlService;
        this.rateLimiter = rateLimiter;
        this.port = port;
        this.publicBaseUrl = publicBaseurl;
        this.trustedProxyIp = trustedProxyIp;
    }

    public void start() throws IOException {
        server =
                HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext(
                "/api/v1/urls",
                this::handleCreateShortUrl
        );

        server.createContext(
                "/",
                this::handleRedirect
        );

        server.start();

        System.out.println(
                "URL Shortener listening on http://localhost:8080"
        );
    }

    private String resolveClientIp(HttpExchange exchange) {
        String peerIp =
                exchange.getRemoteAddress()
                        .getAddress()
                        .getHostAddress();



        String forwardedFor =
                exchange.getRequestHeaders()
                        .getFirst("X-Forwarded-For");

        if (forwardedFor == null
                || forwardedFor.isBlank()
                || !peerIp.equals(trustedProxyIp)
        ) {
            return peerIp;
        }

        String clientIp =
                forwardedFor.split(",", -1)[0].trim();

        if (clientIp.isBlank()) {
            return peerIp;
        }

        return clientIp;
    }

    private void handleCreateShortUrl(HttpExchange exchange) throws IOException {
        try{
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String clientIp = resolveClientIp(exchange);

            final RateLimitDecision decision;

            try {
                decision = rateLimiter.tryAcquire(clientIp);
            } catch (RateLimiterUnavailableException e) {
                exchange.sendResponseHeaders(503, -1);
                return;
            }

            if (decision instanceof RateLimitDecision.Rejected rejected) {
                exchange.getResponseHeaders().set(
                        "Retry-After",
                        String.valueOf(rejected.retryAfterSeconds())
                );

                exchange.sendResponseHeaders(429, -1);
                return;
            }


            String contentType = exchange.getRequestHeaders()
                    .getFirst("Content-Type");

            if (contentType == null ||
                    !contentType.startsWith("application/json")) {
                exchange.sendResponseHeaders(415, -1);
                return;
            }

            CreateShortUrlRequest request;

            try {
                request = objectMapper.readValue(
                        exchange.getRequestBody(),
                        CreateShortUrlRequest.class
                );
            } catch (JsonProcessingException e) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            try {
                ShortUrl created =
                        shortUrlService.createShortUrl(request.url());

                String publicUrl =
                        publicBaseUrl + '/' + created.getShortCode();

                CreateShortUrlResponse response =
                        new CreateShortUrlResponse(publicUrl);

                byte[] responseBody = objectMapper
                        .writeValueAsString(response)
                        .getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                        "Content-Type",
                        "application/json; charset=utf-8"
                );

                exchange.sendResponseHeaders(201, responseBody.length);
                exchange.getResponseBody().write(responseBody);

            } catch (IllegalArgumentException e) {
                exchange.sendResponseHeaders(400, -1);
            }
        } finally {
            exchange.close();
        }
    }

    private void handleRedirect(HttpExchange exchange) throws IOException{
        String path = exchange.getRequestURI().getPath();
        String shortCode = path.substring(1);

        try {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            ShortUrl shortUrl =
                    shortUrlService.findByShortCode(shortCode);

            exchange.getResponseHeaders().set(
                    "Location",
                    shortUrl.getOriginalUrl()
            );

            exchange.sendResponseHeaders(302, -1);
        } catch (ShortUrlNotFoundException e) {
            exchange.sendResponseHeaders(404, -1);
        } finally {
            exchange.close();
        }
    }

    public void stop() {
        if(server != null) {
            server.stop(0);
        }
    }

}
