package vnpt.poc.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.net.URI;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class VnptProxyControllerTest {
    private static final MockWebServer vnpt = new MockWebServer();

    @Autowired
    WebTestClient http;

    @BeforeAll
    static void startUpstream() throws IOException {
        vnpt.start();
    }

    @AfterAll
    static void stopUpstream() throws IOException {
        vnpt.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vnpt.base-url", () -> vnpt.url("/").toString());
        registry.add("VNPT_TOKEN_ID", () -> "real-id-from-env");
        registry.add("VNPT_TOKEN_KEY", () -> "real-key-from-env");
        registry.add("VNPT_ACCESS_TOKEN", () -> "real-auth-from-env");
        registry.add("VNPT_AUTHORIZATION_PREFIX", () -> "Bearer");
        registry.add("VNPT_AUTH_CONTRACT_CONFIRMED", () -> "true");
    }

    @Test
    void preserves_method_path_query_content_type_body_and_returns_upstream_response() throws Exception {
        vnpt.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setHeader("X-VNPT-Trace", "mock-trace")
                .setBody("{\"accepted\":true}"));

        http.post().uri("/vnpt-proxy/file-service/addFile?source=web-sdk")
                .contentType(MediaType.APPLICATION_JSON)
                .header("token_id", "dummy")
                .header("token_key", "dummy")
                .header("Token-id", "dummy-hyphen")
                .header("Token-key", "dummy-hyphen")
                .header("ACCESS_TOKEN", "dummy")
                .header("Cookie", "session=dummy")
                .header(HttpHeaders.AUTHORIZATION, "dummy")
                .bodyValue("{\"image\":\"not-a-real-image\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("X-VNPT-Trace", "mock-trace")
                .expectBody(String.class).isEqualTo("{\"accepted\":true}");

        RecordedRequest forwarded = vnpt.takeRequest(2, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getMethod()).isEqualTo("POST");
        assertThat(forwarded.getPath()).isEqualTo("/file-service/addFile?source=web-sdk");
        assertThat(forwarded.getHeader("Content-Type")).startsWith("application/json");
        assertThat(forwarded.getBody().readUtf8()).isEqualTo("{\"image\":\"not-a-real-image\"}");
        assertThat(forwarded.getHeader("Token-id")).isEqualTo("real-id-from-env");
        assertThat(forwarded.getHeader("Token-key")).isEqualTo("real-key-from-env");
        assertThat(forwarded.getHeader("token_id")).isNull();
        assertThat(forwarded.getHeader("token_key")).isNull();
        assertThat(forwarded.getHeader("ACCESS_TOKEN")).isNull();
        assertThat(forwarded.getHeader("Cookie")).isNull();
        assertThat(forwarded.getHeader("Authorization")).isEqualTo("Bearer real-auth-from-env");
    }

    @Test
    void streams_multipart_bytes_without_parsing_or_rebuilding_them() throws Exception {
        String boundary = "poc-boundary";
        String multipart = "--poc-boundary\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"card.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n"
                + "opaque-image-bytes\r\n--poc-boundary--\r\n";
        vnpt.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        http.post().uri("/vnpt-proxy/file-service/addFile")
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .bodyValue(multipart.getBytes(StandardCharsets.UTF_8))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");

        RecordedRequest forwarded = vnpt.takeRequest(2, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(MediaType.parseMediaType(forwarded.getHeader("Content-Type")).getParameter("boundary"))
                .isEqualTo(boundary);
        assertThat(forwarded.getBody().readUtf8()).isEqualTo(multipart);
    }

    @Test
    void preserves_encoded_path_query_and_non_success_response() throws Exception {
        vnpt.enqueue(new MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"mock unauthorized\"}"));
        http.put().uri(URI.create("/vnpt-proxy/transport-test/a%20b?x=a%2Bb&x=two&empty="))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(new byte[]{0, 1, (byte) 255})
                .exchange().expectStatus().isUnauthorized()
                .expectBody(String.class).isEqualTo("{\"error\":\"mock unauthorized\"}");
        RecordedRequest request = vnpt.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).isEqualTo("/transport-test/a%20b?x=a%2Bb&x=two&empty=");
        assertThat(request.getBody().readByteArray()).containsExactly(0, 1, (byte) 255);
    }

    @Test
    void missing_credentials_or_unconfirmed_contract_never_call_upstream() {
        int before = vnpt.getRequestCount();
        for (boolean confirmed : new boolean[]{false, true}) {
            WebTestClient local = WebTestClient.bindToController(new VnptProxyController(
                    WebClient.builder(), vnpt.url("/").toString(), "id", "key", confirmed ? "" : "token",
                    "Bearer", "Token-id", "Token-key", confirmed)).build();
            local.post().uri("/vnpt-proxy/transport-test").exchange().expectStatus().isEqualTo(503);
        }
        assertThat(vnpt.getRequestCount()).isEqualTo(before);
    }

    @Test
    void supports_verbatim_authorization_when_confirmed_contract_requires_it() throws Exception {
        vnpt.enqueue(new MockResponse().setResponseCode(204));
        WebTestClient local = WebTestClient.bindToController(new VnptProxyController(
                WebClient.builder(), vnpt.url("/").toString(), "id", "key", "opaque-token",
                "", "token_id", "token_key", true)).build();
        local.get().uri("/vnpt-proxy/transport-test").exchange().expectStatus().isNoContent();
        RecordedRequest request = vnpt.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Authorization")).isEqualTo("opaque-token");
        assertThat(request.getHeader("token_id")).isEqualTo("id");
        assertThat(request.getBodySize()).isZero();
    }
}
