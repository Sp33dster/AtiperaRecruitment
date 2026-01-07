# GitHub Repository Proxy API

A simple Spring Boot application acting as a proxy to the GitHub REST API.
The application exposes a single endpoint that returns all **non-fork** public repositories
for a given GitHub user together with their branches and last commit SHAs.


---

## Features

- Fetches public repositories for a GitHub user
- Filters out forked repositories
- For each repository returns:
  - repository name
  - owner login
  - branches with last commit SHA
- Returns HTTP 404 when the GitHub user does not exist

---

## Tech Stack

- Java 25 
- Spring Boot
- Spring Web (RestClient)
- JUnit 5
- WireMock
- WebTestClient
- JSONAssert

---

## Architecture

The application follows a simple **Controller / Service / Client** structure:

- **Controller** – exposes the REST endpoint and returns typed responses
- **Service** – contains business logic (filtering forks, mapping data)
- **Client** – communicates with the external GitHub REST API using `RestClient`

---

## API Endpoint

### GET `/api/github/{username}/repositories`

**Response (200 OK)**

```json
[
  {
    "repositoryName": "example-repo",
    "ownerLogin": "octocat",
    "branches": [
      {
        "name": "main",
        "lastCommitSha": "abc123"
      }
    ]
  }
]
```
**Response (404 Not Found)**

```json

{
  "status": 404,
  "message": "GitHub user not found: username"
}
```

---

## Running the application

### Prerequisites

- Java 25 
- Gradle  

### Run locally

`gradle bootRun`

The application will start on port 8080 by default.

---

### Tests

The project contains only integration tests.

#### Test characteristics

- @SpringBootTest with a real embedded Tomcat server
- Real HTTP requests sent to the application
- GitHub API calls emulated using WireMock
- No mocks (@MockBean, Mockito) used
- Minimal set of business-oriented test cases:
  - happy path (non-fork repositories with branches)
  - user not found (404)

### Run tests

`gradle test`

### Testing with Postman

Request

GET `http://localhost:8080/api/github/{username}/repositories`

**Recommended headers**
```
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
User-Agent: AtiperaTask
```