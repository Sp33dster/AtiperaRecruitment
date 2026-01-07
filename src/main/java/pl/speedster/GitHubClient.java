package pl.speedster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    private static final String USER_REPOS = "/users/{username}/repos";
    private static final String REPO_BRANCHES = "/repos/{owner}/{repo}/branches";

    private final RestClient restClient;

    GitHubClient(final RestClient gitHubRestClient) {
        this.restClient = gitHubRestClient;
    }

    List<GitHubRepo> getUserRepos(final String username) {
        try {
            final var body = restClient.get()
                    .uri(USER_REPOS, username)
                    .retrieve()
                    .body(GitHubRepo[].class);

            if (body == null) {
                throw new IllegalStateException("Invalid GitHub response: repos body is null");
            }
            return List.of(body);
        } catch (final HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new UserNotFoundException(username);
            }
            log.warn("GitHub call failed: GET {} status={}", USER_REPOS, e.getStatusCode());
            throw e;
        }
    }

    List<GitHubBranch> getRepoBranches(final String owner, final String repo) {
        final var body = restClient.get()
                .uri(REPO_BRANCHES, owner, repo)
                .retrieve()
                .body(GitHubBranch[].class);

        if (body == null) {
            throw new IllegalStateException("Invalid GitHub response: branches body is null");
        }
        return List.of(body);
    }
}