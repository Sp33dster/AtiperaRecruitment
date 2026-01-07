package pl.speedster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class GitHubRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryService.class);

    private final GitHubClient gitHubClient;

    GitHubRepositoryService(final GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    List<RepositoryResponse> getUserRepositories(final String username) {
        log.info("Fetching repositories for username={}", username);

        return gitHubClient.getUserRepos(username).stream()
                .filter(repo -> !repo.fork())
                .map(repo -> toRepositoryResponse(username, repo))
                .toList();
    }

    private RepositoryResponse toRepositoryResponse(final String username, final GitHubRepo repo) {
        final var ownerLogin = repo.owner().login();
        final var branches = fetchBranches(ownerLogin, repo.name());

        return new RepositoryResponse(repo.name(), ownerLogin, branches);
    }

    private List<BranchResponse> fetchBranches(final String ownerLogin, final String repoName) {
        return gitHubClient.getRepoBranches(ownerLogin, repoName).stream()
                .map(b -> new BranchResponse(b.name(), b.commit().sha()))
                .toList();
    }
}