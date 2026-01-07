package pl.speedster;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GitHubIntegrationTest {

    static WireMockServer wm;

    @BeforeAll
    static void startWireMock() {
        wm = new WireMockServer(wireMockConfig().dynamicPort());
        wm.start();
        configureFor("localhost", wm.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wm != null) wm.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("github.base-url", () -> "http://localhost:" + wm.port());
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void shouldReturnNonForkReposWithBranches_happyPath() throws Exception {
        stubFor(get(urlEqualTo("/users/john/repos"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"name":"core-repo","fork":false,"owner":{"login":"john"}},
                                  {"name":"forked-repo","fork":true,"owner":{"login":"john"}}
                                ]
                                """)));

        stubFor(get(urlEqualTo("/repos/john/core-repo/branches"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"name":"main","commit":{"sha":"abc"}},
                                  {"name":"dev","commit":{"sha":"def"}}
                                ]
                                """)));

        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/github/john/repositories"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, res.statusCode());

        JSONAssert.assertEquals("""
                [
                  {
                    "repositoryName":"core-repo",
                    "ownerLogin":"john",
                    "branches":[
                      {"name":"main","lastCommitSha":"abc"},
                      {"name":"dev","lastCommitSha":"def"}
                    ]
                  }
                ]
                """, res.body(), false);

        verify(1, getRequestedFor(urlEqualTo("/users/john/repos")));
        verify(1, getRequestedFor(urlEqualTo("/repos/john/core-repo/branches")));
        verify(0, getRequestedFor(urlEqualTo("/repos/john/forked-repo/branches")));
    }

    @Test
    void shouldReturn404WithErrorResponse_whenGithubUserNotFound() throws Exception {
        stubFor(get(urlEqualTo("/users/missing/repos"))
                .willReturn(aResponse().withStatus(404)));

        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/github/missing/repositories"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(404, res.statusCode());

        JSONAssert.assertEquals("""
                {"status":404,"message":"GitHub user not found: missing"}
                """, res.body(), true);

        verify(1, getRequestedFor(urlEqualTo("/users/missing/repos")));
    }
}