# Branch and Commit Standards

This document defines the branch management and commit message standards for the project, ensuring consistency in team collaboration and code quality.

## Branch Management Standards

### Branch Types

| Branch Type | Naming Format | Purpose | Example |
|-------------|---------------|---------|---------|
| **Main Branch** | `main` | Production code | `main` |
| **Development Branch** | `develop` | Development integration | `develop` |
| **Feature Branch** | `feature/feature-name` | New feature development | `feature/user-login` |
| **Bugfix Branch** | `bugfix/issue-name` | Bug fixes | `bugfix/auth-error` |
| **Hotfix Branch** | `hotfix/patch-name` | Emergency fixes | `hotfix/security-patch` |
| **Design Branch** | `design/design-name` | UI/UX optimization | `design/mobile-layout` |
| **Refactor Branch** | `refactor/refactor-name` | Code refactoring | `refactor/user-service` |
| **Test Branch** | `test/test-name` | Test development | `test/integration-tests` |
| **Documentation Branch** | `doc/doc-name` | Documentation updates | `doc/api-guide` |

### Branch Creation Commands

```bash
# Using Makefile commands to create standard branches
make new-feature name=user-login      # Create feature branch
make new-bugfix name=auth-error       # Create bugfix branch
make new-hotfix name=security-patch   # Create hotfix branch
make new-design name=mobile-layout    # Create design branch

# Manual branch creation
git checkout -b feature/user-login
git checkout -b bugfix/auth-error
git checkout -b hotfix/security-patch
```

### Branch Workflow

```bash
# 1. Create feature branch from main
git checkout main
git pull origin main
git checkout -b feature/user-login

# 2. After development, merge to develop
git checkout develop
git merge feature/user-login
git push origin develop

# 3. Merge to main via Pull Request
# Create PR on GitHub: develop → main
```

## Commit Message Standards

### Commit Types

| Type | Description | Example |
|------|-------------|---------|
| `feat` | New feature | `feat: add phone number login` |
| `fix` | Bug fix | `fix: resolve token expiration issue` |
| `docs` | Documentation update | `docs: update API documentation` |
| `style` | Code formatting | `style: unify indentation format` |
| `refactor` | Code refactoring | `refactor: split user service` |
| `perf` | Performance optimization | `perf: optimize database queries` |
| `test` | Test related | `test: add unit tests` |
| `build` | Build system | `build: upgrade webpack to 5.0` |
| `ci` | CI/CD configuration | `ci: add GitHub Actions` |
| `chore` | Miscellaneous tasks | `chore: update .gitignore` |
| `revert` | Revert commit | `revert: revert commit abc123` |

### Commit Format

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Format Requirements

- **Type**: Must use predefined types above
- **Scope**: Optional, indicates affected area (e.g., module name)
- **Description**: Concise and clear, use English
- **Length**: Title max 50 characters, body max 72 characters per line
- **Tense**: Use present tense, e.g., "add" not "added"

### Commit Examples

```bash
# Basic format
feat: add user login functionality
fix: resolve password validation bug
docs: update API documentation

# With scope
feat(auth): add OAuth2 login support
fix(api): resolve user info query endpoint
docs(guide): improve quick start guide

# Detailed format
feat: add user permission management

- Implement role-based permission control
- Add permission validation middleware
- Update user management interface

Closes #123
```

## Quality Gates

### Pre-commit Checks

```bash
# Automatic execution (via Git hooks)
make format    # Code formatting
make check     # Quality checks
make test      # Run tests

# Manual checks
make check-branch    # Check branch naming
make safe-push       # Safe push
```

### Check Items

- **Code Format**: Auto-format all language code
- **Syntax Check**: Pass all language lint tools
- **Type Check**: TypeScript/Python type validation
- **Complexity Control**: Function complexity limits
- **Branch Naming**: Validate branch naming conventions
- **Commit Message**: Validate commit message format

## Best Practices

### Development Workflow

1. **Start Development**: `make dev-setup` (first time) → `make new-feature name=feature-name`
2. **Write Code**: Frequent commits with standard commit messages
3. **Pre-commit Check**: `make fmt && make check` ensure quality
4. **Push Code**: `make safe-push` validate and push
5. **Create PR**: Create Pull Request via GitHub interface
6. **Code Review**: Team review and feedback
7. **Merge Code**: Merge to main branch after approval

### Team Conventions

- 🚫 **No direct push to main/develop branches**
- ✅ **Must use branch development + PR process**
- ✅ **Must pass all quality checks before commit**
- ✅ **Use standard branch naming and commit messages**
- ✅ **Break large features into small commits for easier review**

## Common Issues

### Branch Management Issues

**Issue**: Developing on wrong branch
**Solution**: Use git commands to migrate code to correct branch
```bash
git stash
git checkout -b feature/correct-branch
git stash pop
```

**Issue**: Non-standard branch name
**Solution**: Rename branch or create new standard branch
```bash
git branch -m old-branch-name feature/new-name
```

### Commit Issues

**Issue**: Incorrect commit message format
**Solution**: Use `git commit --amend` to modify last commit
```bash
git commit --amend -m "feat: correct commit message"
```

**Issue**: Quality check failure
**Solution**: Run `make check` to see detailed errors, fix and recommit

## Related Documentation

- [Code Quality Requirements](./code-requirements.md) - Language-specific code quality detection
- [Makefile Usage Guide](../docs/Makefile-readme.md) - Complete Makefile command reference
- [Local Development Configuration](../docs/Makefile-readme.md#local-development-configuration) - Using `.localci.toml` for modular development
