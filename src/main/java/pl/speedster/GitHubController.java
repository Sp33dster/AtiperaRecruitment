package pl.speedster;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/github")
class GitHubController {

    private final GitHubRepositoryService repositoryService;

    GitHubController(final GitHubRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @GetMapping("/{username}/repositories")
    List<RepositoryResponse> getRepositories(@PathVariable final String username) {
        return repositoryService.getUserRepositories(username);
    }
}