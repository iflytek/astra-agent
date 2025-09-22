# Go Code Quality Detection

## Toolchain

### Formatting Tools
- **gofmt**: Go official formatting
- **goimports**: Automatic import management
- **gofumpt**: Stricter formatting
- **golines**: Line length control (120 characters)

### Quality Detection Tools
- **gocyclo**: Cyclomatic complexity detection (≤10)
- **staticcheck**: Static analysis
- **golangci-lint**: Comprehensive code standard checks

## Makefile Integration

### Unified Commands
```bash
make format    # Format all languages (including Go)
make check     # Check all language quality (including Go)
```

### Go-specific Commands
```bash
make fmt-go              # Format Go code
make check-go            # Go quality check
make test-go             # Run Go tests
make build-go            # Build Go project
```

### Tool Installation
```bash
make install-tools-go    # Install Go development tools
make check-tools-go      # Check Go tool status
```

## Quality Standards

| Check Item | Standard | Tool |
|------------|----------|------|
| Code Format | Go standard format | gofmt + gofumpt |
| Import Management | No unused imports | goimports |
| Line Length | ≤120 characters | golines |
| Function Complexity | Cyclomatic complexity ≤10 | gocyclo |
| Static Analysis | 0 issues | staticcheck |
| Code Standards | 0 issues | golangci-lint |

## Common Issues

### Formatting Issues
```bash
make fmt-go  # Auto-fix format issues
```

### Import Issues
```bash
goimports -w .  # Auto-fix imports
```

### Complexity Issues
```bash
gocyclo -over 10 .  # Detect complex functions
# Need to refactor functions with complexity >10
```

### Static Analysis Issues
```bash
staticcheck ./...  # View detailed report
# Fix code according to report suggestions
```

## Configuration Files

### golangci-lint Configuration (`.golangci.yml`)
```yaml
linters-settings:
  gocyclo:
    min-complexity: 10
  funlen:
    lines: 50

linters:
  enable:
    - gocyclo
    - funlen
    - staticcheck
    - govet
    - unused
```

## Related Resources

- [Go Official Code Standards](https://golang.org/doc/effective_go.html)
- [golangci-lint Documentation](https://golangci-lint.run/)
- [staticcheck Documentation](https://staticcheck.io/)
