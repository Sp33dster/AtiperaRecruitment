package pl.speedster;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.commons.lang3.time.StopWatch;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldReturnNonForkReposWithBranches_happyPath() throws Exception{
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

    @Test
    void shouldCallGithub3TimesAndFinishIn2to3Seconds_dueToParallelBranches() throws Exception {

        stubFor(get(urlEqualTo("/users/sp33dster/repos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"name":"repo-1","fork":false,"owner":{"login":"sp33dster"}},
                                  {"name":"repo-2","fork":false,"owner":{"login":"sp33dster"}},
                                  {"name":"forked-repo","fork":true,"owner":{"login":"sp33dster"}}
                                ]
                                """)));

        stubFor(get(urlEqualTo("/repos/sp33dster/repo-1/branches"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"name":"main","commit":{"sha":"a1"}},
                                  {"name":"dev","commit":{"sha":"b2"}},
                                  {"name":"release","commit":{"sha":"c3"}}
                                ]
                                """)));

        stubFor(get(urlEqualTo("/repos/sp33dster/repo-2/branches"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"name":"main","commit":{"sha":"d4"}},
                                  {"name":"dev","commit":{"sha":"e5"}},
                                  {"name":"release","commit":{"sha":"f6"}}
                                ]
                                """)));

        final var stopWatch = new StopWatch();
        stopWatch.start();

        final HttpResponse<String> res = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/github/sp33dster/repositories"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        stopWatch.stop();
        final long ms = stopWatch.getTime();

        assertEquals(200, res.statusCode());

        JSONAssert.assertEquals("""
                [
                  {
                    "repositoryName":"repo-1",
                    "ownerLogin":"sp33dster",
                    "branches":[
                      {"name":"main","lastCommitSha":"a1"},
                      {"name":"dev","lastCommitSha":"b2"},
                      {"name":"release","lastCommitSha":"c3"}
                    ]
                  },
                  {
                    "repositoryName":"repo-2",
                    "ownerLogin":"sp33dster",
                    "branches":[
                      {"name":"main","lastCommitSha":"d4"},
                      {"name":"dev","lastCommitSha":"e5"},
                      {"name":"release","lastCommitSha":"f6"}
                    ]
                  }
                ]
                """, res.body(), false);

        verify(3, getRequestedFor(urlMatching(".*")));
        verify(1, getRequestedFor(urlEqualTo("/users/sp33dster/repos")));
        verify(1, getRequestedFor(urlEqualTo("/repos/sp33dster/repo-1/branches")));
        verify(1, getRequestedFor(urlEqualTo("/repos/sp33dster/repo-2/branches")));
        verify(0, getRequestedFor(urlEqualTo("/repos/sp33dster/forked-repo/branches")));

        assertTrue(ms >= 2000 && ms <= 3000, "Expected total time in [2000..3000] ms but was " + ms);
    }
}