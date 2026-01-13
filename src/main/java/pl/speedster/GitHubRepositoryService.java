package pl.speedster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
class GitHubRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryService.class);

    private final GitHubClient gitHubClient;
    private final ExecutorService executor;

    GitHubRepositoryService(final GitHubClient gitHubClient, final  ExecutorService virtualThreadExecutor) {
        this.gitHubClient = gitHubClient;
        this.executor = virtualThreadExecutor;
    }

    List<RepositoryResponse> getUserRepositories(final String username) {
        log.info("Fetching repositories for username={}", username);

        final var nonForkRepos = gitHubClient.getUserRepos(username).stream()
                .filter(repo -> !repo.fork())
                .toList();

        if (nonForkRepos.isEmpty()) {
            return List.of();
        }

        final var repositoryResponseTasks = nonForkRepos.stream()
                .map(repo -> executor.submit(() -> toRepositoryResponse(repo)))
                .toList();

        return repositoryResponseTasks.stream()
                .map(GitHubRepositoryService::uncheckedGet)
                .toList();
    }

    private RepositoryResponse toRepositoryResponse(final GitHubRepo repo) {
        final var ownerLogin = repo.owner().login();
        final var branches = fetchBranches(ownerLogin, repo.name());

        return new RepositoryResponse(repo.name(), ownerLogin, branches);
    }

    private List<BranchResponse> fetchBranches(final String ownerLogin, final String repoName) {
        return gitHubClient.getRepoBranches(ownerLogin, repoName).stream()
                .map(b -> new BranchResponse(b.name(), b.commit().sha()))
                .toList();
    }

    private static <T> T uncheckedGet(final Future<T> future) {
        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (final ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}