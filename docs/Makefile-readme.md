# 🚀 Multi-Language CI/CD Toolchain

> **Unified development workflow for Go, Java, Python, TypeScript**

## Quick Start

### One-time Setup
```bash
make setup
```
Installs all language tools, configures Git hooks, and sets up branch strategy.

### Daily Commands
```bash
make format    # Format all code
make check     # Quality checks
make test      # Run tests
make build     # Build projects
make push      # Safe push with pre-checks
make clean     # Clean build artifacts
```

### Project Status
```bash
make status    # Show project information
make info      # Show tool versions
```

## Local Development Configuration

For efficient local development, you can create a `.localci.toml` file in the root directory to override the default configuration:

### Create Local Configuration
```bash
# Copy the default configuration
cp makefiles/localci.toml .localci.toml

# Edit to enable only the modules you're working on
# Set enabled = true for active modules, false for others
```

### Example Local Configuration
```toml
[meta]
version = 1

[[python.apps]]
name = "core-agent"
dir = "core/agent"
enabled = true    # Only enable the module you're working on

[[python.apps]]
name = "core-memory"
dir = "core/memory/database"
enabled = false   # Disable other modules for faster execution

# ... other modules set to enabled = false
```

### Benefits
- **Faster execution**: Only processes enabled modules
- **Focused development**: Work on specific modules without interference
- **Easy switching**: Change `enabled` values to switch between modules

## Core Commands

### `make setup`
One-time environment setup. Installs tools, configures Git hooks, sets branch strategy.

### `make format`
Formats code for all languages:
- Go: `gofmt` + `goimports` + `gofumpt` + `golines`
- Java: Maven `spotless:apply`
- Python: `black` + `isort`
- TypeScript: `prettier`

### `make check` (alias: `make lint`)
Quality checks for all languages:
- Go: `gocyclo` + `staticcheck` + `golangci-lint`
- Java: `checkstyle` + `pmd` + `spotbugs`
- Python: `flake8` + `mypy` + `pylint`
- TypeScript: `eslint` + `tsc`

### `make test`
Runs tests for all projects:
- Go: `go test` with coverage
- Java: `mvn test`
- Python: `pytest` with coverage
- TypeScript: `npm test`

### `make build`
Builds all projects:
- Go: Build binaries
- Java: Maven `package`
- Python: Install dependencies
- TypeScript: Vite `build`

### `make push`
Safe push with pre-checks:
- Runs `format` and `check` automatically
- Validates branch naming
- Pushes to remote repository

### `make clean`
Cleans build artifacts for all languages.

## Running Services

```bash
# Go service
cd core/tenant && go run cmd/main.go

# Java service
cd console/backend && mvn spring-boot:run

# Python services
cd core/memory/database && python main.py
cd core/agent && python main.py

# TypeScript frontend
cd console/frontend && npm run dev
```

## Additional Commands

### `make status`
Shows project information and active projects.

### `make info`
Displays tool versions and installation status.

### `make fix`
Auto-fixes code issues (formatting + some lint fixes).

### `make ci`
Complete CI pipeline: `format` + `check` + `test` + `build`.

### `make hooks`
Git hook management:
- `make hooks-install` - Install complete hooks
- `make hooks-install-basic` - Install lightweight hooks
- `make hooks-uninstall` - Uninstall hooks

### `make enable-legacy`
Enables specialized language commands for backward compatibility.

## Specialized Commands

After running `make enable-legacy`, you can use language-specific commands:

### Go Commands
```bash
make fmt-go              # Format Go code
make check-go            # Go quality check
make test-go             # Run Go tests
make build-go            # Build Go project
```

### Java Commands
```bash
make fmt-java            # Format Java code
make check-java          # Java quality check
make test-java           # Run Java tests
make build-java          # Build Java project
```

### Python Commands
```bash
make fmt-python          # Format Python code
make check-python        # Python quality check
make test-python         # Run Python tests
```

### TypeScript Commands
```bash
make fmt-typescript      # Format TypeScript code
make check-typescript    # TypeScript quality check
make test-typescript     # Run TypeScript tests
make build-typescript    # Build TypeScript project
```

## Git Hooks

### Install Hooks
```bash
make hooks-install       # Complete hooks (format+check)
make hooks-install-basic # Lightweight hooks (format only)
```

### Branch Naming
```bash
feature/user-auth        # Feature branch
bugfix/fix-login         # Bug fix
hotfix/security-patch    # Hotfix
```

### Commit Messages
```bash
feat: add user authentication
fix: resolve login timeout
docs: update API documentation
```

## Troubleshooting

### Common Issues
```bash
# Tool installation problems
make info                # Check tool status
make install-tools       # Reinstall tools

# Project detection issues
make status              # Check project status
make _debug              # Debug detection

# Hook problems
make hooks-uninstall && make hooks-install

# Local configuration issues
rm .localci.toml         # Remove local config to use defaults
cp makefiles/localci.toml .localci.toml  # Reset local config
```