package pl.speedster;

import java.util.List;

record RepositoryResponse(String repositoryName, String ownerLogin, List<BranchResponse> branches) { }