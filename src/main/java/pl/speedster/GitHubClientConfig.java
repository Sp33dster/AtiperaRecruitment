package pl.speedster;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class GitHubClientConfig {

    @Bean
    RestClient gitHubRestClient(
            @Value("${github.base-url:https://api.github.com}") String baseUrl) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "AtiperaTask")
                .build();
    }
}