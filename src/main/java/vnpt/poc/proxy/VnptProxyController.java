package vnpt.poc.proxy;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Deliberately transport-only proxy. It never reads JSON fields, images, or
 * multipart parts: the incoming byte stream is passed directly to VNPT.
 */
@RestController
public class VnptProxyController {
    private static final Logger log = LoggerFactory.getLogger(VnptProxyController.class);
    private static final String PREFIX = "/vnpt-proxy";
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host");
    private static final Set<String> VNPT_AUTH_HEADERS = Set.of(
            "authorization", "token_id", "token_key", "token-id", "token-key",
            "access_token", "access-token", "cookie");

    private final WebClient client;
    private final String baseUrl;
    private final String tokenId;
    private final String tokenKey;
    private final String authorization;
    private final String tokenIdHeader;
    private final String tokenKeyHeader;
    private final boolean authContractConfirmed;

    public VnptProxyController(
            WebClient.Builder clientBuilder,
            @Value("${vnpt.base-url}") String baseUrl,
            @Value("${VNPT_TOKEN_ID:}") String tokenId,
            @Value("${VNPT_TOKEN_KEY:}") String tokenKey,
            @Value("${VNPT_ACCESS_TOKEN:}") String accessToken,
            @Value("${VNPT_AUTHORIZATION_PREFIX:}") String authorizationPrefix,
            @Value("${VNPT_TOKEN_ID_HEADER:Token-id}") String tokenIdHeader,
            @Value("${VNPT_TOKEN_KEY_HEADER:Token-key}") String tokenKeyHeader,
            @Value("${VNPT_AUTH_CONTRACT_CONFIRMED:false}") boolean authContractConfirmed) {
        this.client = clientBuilder.build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.tokenId = tokenId;
        this.tokenKey = tokenKey;
        this.authorization = accessToken.isBlank() ? "" : (authorizationPrefix.isBlank() ? accessToken : authorizationPrefix.trim() + " " + accessToken);
        this.tokenIdHeader = tokenIdHeader;
        this.tokenKeyHeader = tokenKeyHeader;
        this.authContractConfirmed = authContractConfirmed;
    }

    @RequestMapping({PREFIX, PREFIX + "/**"})
    public Mono<Void> proxy(ServerHttpRequest request, ServerHttpResponse response) {
        log.info("Incoming SDK request: {} {}", request.getMethod(), request.getURI().getRawPath());
        if (!credentialsConfigured()) {
            log.warn("Rejected VNPT proxy request: credentials missing or auth contract not confirmed");
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return response.setComplete();
        }

        URI target = targetUri(request);
        log.info("Forwarding: {} {}", request.getMethod(), safeUriForLog(target));

        return client.method(request.getMethod())
                .uri(target)
                .headers(headers -> copyRequestHeaders(request.getHeaders(), headers))
                .body(request.getBody(), DataBuffer.class)
                .exchangeToMono(vnptResponse -> copyResponse(vnptResponse, response))
                .onErrorResume(error -> {
                    log.warn("VNPT proxy transport failure: {}", error.getClass().getSimpleName());
                    if (response.isCommitted()) return Mono.error(error);
                    response.getHeaders().clear();
                    response.setStatusCode(HttpStatus.BAD_GATEWAY);
                    return response.setComplete();
                });
    }

    private Mono<Void> copyResponse(ClientResponse upstream, ServerHttpResponse downstream) {
        downstream.setStatusCode(upstream.statusCode());
        upstream.headers().asHttpHeaders().forEach((name, values) -> {
            if (!excludedHeaders(upstream.headers().asHttpHeaders()).contains(name.toLowerCase(Locale.ROOT))
                    && !VNPT_AUTH_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                    && !name.equalsIgnoreCase("set-cookie")
                    && !name.equalsIgnoreCase(tokenIdHeader) && !name.equalsIgnoreCase(tokenKeyHeader)) {
                downstream.getHeaders().put(name, values);
            }
        });
        log.info("VNPT response: HTTP {}", upstream.statusCode().value());
        return downstream.writeWith(upstream.bodyToFlux(DataBuffer.class));
    }

    private void copyRequestHeaders(HttpHeaders incoming, HttpHeaders outgoing) {
        incoming.forEach((name, values) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!excludedHeaders(incoming).contains(normalized) && !VNPT_AUTH_HEADERS.contains(normalized)
                    && !name.equalsIgnoreCase(tokenIdHeader) && !name.equalsIgnoreCase(tokenKeyHeader)) {
                outgoing.put(name, values);
            }
        });
        outgoing.set(tokenIdHeader, tokenId);
        outgoing.set(tokenKeyHeader, tokenKey);
        outgoing.set(HttpHeaders.AUTHORIZATION, authorization);
    }

    private URI targetUri(ServerHttpRequest request) {
        String rawPath = request.getURI().getRawPath();
        String remainder = rawPath.length() == PREFIX.length() ? "/" : rawPath.substring(PREFIX.length());
        if (!remainder.startsWith("/") || remainder.contains("..")) {
            throw new IllegalArgumentException("Invalid proxied path");
        }
        String rawQuery = request.getURI().getRawQuery();
        return URI.create(baseUrl + remainder + (rawQuery == null ? "" : "?" + rawQuery));
    }

    private boolean credentialsConfigured() {
        return authContractConfirmed && !tokenId.isBlank() && !tokenKey.isBlank() && !authorization.isBlank();
    }

    private static Set<String> excludedHeaders(HttpHeaders headers) {
        Set<String> excluded = new HashSet<>(HOP_BY_HOP_HEADERS);
        for (String value : headers.getOrEmpty("Connection")) {
            for (String name : value.split(",")) excluded.add(name.trim().toLowerCase(Locale.ROOT));
        }
        return excluded;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String safeUriForLog(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority() + uri.getRawPath();
    }
}
