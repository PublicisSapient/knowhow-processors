# Git Metadata Scanner

A Spring Boot application that scans and collects metadata from Git repositories across multiple platforms including GitHub, GitLab, Azure DevOps, and Bitbucket.

## Features

- **Multi-Platform Support**: Scan repositories from GitHub, GitLab, Azure DevOps, and Bitbucket
- **Flexible Scanning Strategies**: Choose between JGit (local cloning) or REST API approaches
- **Asynchronous Processing**: Support for both sync and async scanning operations
- **Scheduled Scanning**: Automated periodic scanning with configurable schedules
- **REST API**: HTTP endpoints for on-demand scanning and status checking
- **MongoDB Persistence**: Store commitDetails data, merge requests, and user information
- **Configurable**: Extensive configuration options for performance tuning
- **Health Monitoring**: Built-in health checks and metrics

## Architecture

The application follows clean architecture principles with clear separation of concerns:

```
├── controller/          # REST API controllers
├── service/
│   ├── core/           # Core business logic services
│   ├── platform/       # Platform-specific implementations
│   └── strategy/       # Strategy pattern implementations
├── model/              # Domain models and entities
├── repository/         # Data access layer
├── config/             # Configuration classes
├── executer/           # Scanning executors
├── util/               # Utility classes
└── exception/          # Custom exceptions
```

## Quick Start

### Prerequisites

- Java 17 or higher
- MongoDB 4.4 or higher
- Maven 3.6 or higher

### Configuration

1. Set up environment variables for platform tokens:
```bash
export GITHUB_TOKEN=your_github_token
export GITLAB_TOKEN=your_gitlab_token
export AZURE_PAT=your_azure_personal_access_token
export AZURE_ORGANIZATION=your_azure_organization
export BITBUCKET_USERNAME=your_bitbucket_username
export BITBUCKET_APP_PASSWORD=your_bitbucket_app_password
```

2. Configure MongoDB connection in `application.properties`:
```properties
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=git_metadata
```

### Running the Application

1. Clone the repository
2. Build the application:
```bash
mvn clean compile
```

3. Run the application:
```bash
mvn spring-boot:run
```

The application will start on port 8080 by default.

## API Endpoints

### Scan Repository (Synchronous)
```http
POST /api/v1/git-scanner/scan
Content-Type: application/json

{
  "toolConfigId": "config-123",
  "repositoryUrl": "https://github.com/owner/repo.git",
  "token": "optional-token",
  "commitLimit": 1000,
  "mergeRequestLimit": 500
}
```

### Scan Repository (Asynchronous)
```http
POST /api/v1/git-scanner/scan/async
Content-Type: application/json

{
  "toolConfigId": "config-123",
  "repositoryUrl": "https://github.com/owner/repo.git",
  "token": "optional-token"
}
```

### Check Scan Status
```http
GET /api/v1/git-scanner/scan/status/{taskId}
```

### Health Check
```http
GET /api/v1/git-scanner/health
```

## Data Models and Diff Statistics

### Commit Data Structure

The application collects comprehensive commitDetails information including detailed diff statistics and file-level changes:

```json
{
  "sha": "abc123def456",
  "commitMessage": "Add new feature with tests",
  "authorName": "John Doe",
  "authorEmail": "john.doe@example.com",
  "commitTimestamp": "2024-01-15T10:30:00",
  "addedLines": 150,
  "removedLines": 25,
  "changedLines": 10,
  "filesChanged": 5,
  "fileChanges": [
    {
      "filePath": "src/main/java/Service.java",
      "addedLines": 45,
      "removedLines": 10,
      "changedLines": 5,
      "changeType": "MODIFIED",
      "previousPath": null,
      "isBinary": false,
      "changedLineNumbers": [15, 16, 17, 25, 26, 30, 31, 32, 45, 46]
    },
    {
      "filePath": "src/test/java/ServiceTest.java",
      "addedLines": 30,
      "removedLines": 0,
      "changedLines": 0,
      "changeType": "ADDED",
      "previousPath": null,
      "isBinary": false,
      "changedLineNumbers": [1, 2, 3, 4, 5, 10, 11, 12, 20, 21, 22, 25, 26, 27, 28, 29, 30]
    },
    {
      "filePath": "docs/README.md",
      "addedLines": 20,
      "removedLines": 5,
      "changedLines": 3,
      "changeType": "MODIFIED",
      "previousPath": null,
      "isBinary": false,
      "changedLineNumbers": [45, 46, 47, 50, 51, 52, 60, 61, 62]
    }
  ]
}
```

### File Change Details

Each file change includes:
- **addedLines**: Number of lines added in this file
- **removedLines**: Number of lines removed from this file
- **changedLines**: Number of lines modified (minimum of added/removed)
- **changeType**: Type of change (ADDED, MODIFIED, DELETED, RENAMED, COPIED)
- **previousPath**: Previous file path (for renamed files)
- **isBinary**: Whether the file is binary
- **changedLineNumbers**: Array of specific line numbers where changes occurred

### Line Number Format

The `changedLineNumbers` array contains 1-based line numbers where:
- For **added lines**: Line numbers in the new file where content was inserted
- For **deleted lines**: Line numbers in the old file where content was removed
- For **modified lines**: Line numbers in the new file where content was changed

Example file changes structure:
```json
{
  "Deployment/Helm/values-perf.yaml": [30, 31, 32, 33],
  "Deployment/Helm/values-uat1.yaml": [30, 31, 32, 33],
  "src/main/java/Controller.java": [15, 16, 25, 26, 45, 46, 47],
  "src/test/java/ControllerTest.java": [1, 2, 3, 10, 11, 12, 20, 21]
}
```

### Scanning Strategies

The application supports two strategies for collecting diff statistics:

1. **JGit Strategy** (Local):
   - Clones repository locally using JGit
   - Analyzes diffs using JGit's diff formatter
   - Provides complete line-level analysis
   - More accurate for complex changes

2. **REST API Strategy** (Remote):
   - Uses platform APIs (GitHub, GitLab, etc.)
   - Parses patch format from API responses
   - Faster for recent commitDetails
   - Subject to API rate limits

## Configuration Options

### Platform Configuration
```properties
# GitHub
git.platforms.github.enabled=true
git.platforms.github.api-url=https://api.github.com
git.platforms.github.token=${GITHUB_TOKEN}
git.platforms.github.rate-limit-per-hour=5000

# GitLab
git.platforms.gitlab.enabled=true
git.platforms.gitlab.api-url=https://gitlab.com/api/v4
git.platforms.gitlab.token=${GITLAB_TOKEN}

# Azure DevOps
git.platforms.azure.enabled=true
git.platforms.azure.organization=${AZURE_ORGANIZATION}
git.platforms.azure.personal-access-token=${AZURE_PAT}

# Bitbucket
git.platforms.bitbucket.enabled=true
git.platforms.bitbucket.username=${BITBUCKET_USERNAME}
git.platforms.bitbucket.app-password=${BITBUCKET_APP_PASSWORD}
```

### Scanner Configuration
```properties
# Scanning Strategy
git.scanner.default-commitDetails-strategy=jgit
git.scanner.use-rest-api-for-commitDetails=false
git.scanner.default-commitDetails-limit=1000

# Scheduled Scanning
git.scanner.scheduled.enabled=true
git.scanner.scheduled.cron-expression=0 0 2 * * ?
git.scanner.scheduled.batch-size=10
git.scanner.scheduled.parallel-threads=3

# Performance
git.scanner.performance.max-concurrent-scans=5
git.scanner.performance.http-timeout-seconds=30
git.scanner.performance.enable-rate-limiting=true
```

## Data Models

### Commit Data
- Commit hash, author, committer
- Commit message and timestamp
- File changes and statistics
- Parent commitDetails relationships

### Merge Request Data
- Title, description, and status
- Source and target branches
- Author and reviewer information
- Creation and merge timestamps

### User Data
- User profiles and contact information
- Platform-specific user IDs
- Activity statistics

## Development

### Building
```bash
mvn clean compile
```

### Running Tests
```bash
mvn test
```

### Creating Distribution
```bash
mvn clean package
```

## Monitoring

The application includes Spring Boot Actuator endpoints for monitoring:

- `/actuator/health` - Application health status
- `/actuator/metrics` - Application metrics
- `/actuator/info` - Application information

## Troubleshooting

### Windows File Handle Issues (Fixed)

**Problem**: On Windows systems, you might encounter errors like:
```
Could not delete temporary file: C:\Users\...\Temp\git-scanner-xxx\.git\objects\pack\pack-xxx.pack
java.nio.file.FileSystemException: The process cannot access the file because it is being used by another process
```

**Root Cause**: JGit keeps file handles open to Git repository files (especially pack files), preventing Windows from deleting them during cleanup.

**Solution**: The application now includes enhanced cleanup logic that:

1. **Properly closes Git resources** before attempting directory cleanup
2. **Implements retry logic** with configurable delays
3. **Forces garbage collection** to release remaining references
4. **Falls back to deletion on JVM exit** if immediate cleanup fails

**Configuration**: You can tune the cleanup behavior via these properties:

```properties
# Enhanced Cleanup Configuration
git.scanner.storage.cleanup-retry-attempts=3
git.scanner.storage.cleanup-retry-delay-ms=100
git.scanner.storage.cleanup-final-delay-ms=500
git.scanner.storage.force-gc-on-cleanup-failure=true
```

**Monitoring**: The application logs cleanup attempts at DEBUG level and failures at WARN/ERROR levels:

```
DEBUG - Successfully cleaned up temporary directory: /tmp/git-scanner-xxx
WARN  - Second cleanup attempt failed, trying with longer delay...
ERROR - Failed to delete temporary directory: /tmp/git-scanner-xxx. Marking for deletion on JVM exit.
```

### Performance Considerations

- **JGit Strategy**: Slower but more accurate, requires local disk space
- **REST API Strategy**: Faster but limited by API rate limits
- **Large Repositories**: Consider using commitDetails limits and date ranges to reduce processing time
- **Concurrent Scans**: Monitor system resources when running multiple scans simultaneously

## Contributing

1. Follow the existing code structure and patterns
2. Implement proper error handling and logging
3. Add unit tests for new functionality
4. Update documentation as needed

## License

This project is licensed under the MIT License - see the LICENSE file for details.