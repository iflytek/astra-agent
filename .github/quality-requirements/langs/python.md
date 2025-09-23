# Python Code Quality Detection

## Toolchain

### Formatting Tools
- **black**: Code formatting (PEP 8 standard)
- **isort**: Import statement sorting and organization

### Quality Detection Tools
- **flake8**: Code style and error checking
- **mypy**: Static type checking
- **pylint**: Comprehensive code quality analysis

## Makefile Integration

### Unified Commands
```bash
make format    # Format all languages (including Python)
make check     # Check all language quality (including Python)
```

### Python-specific Commands
```bash
make fmt-python          # Format Python code
make check-python        # Python quality check
make test-python         # Run Python tests
```

### Tool Installation
```bash
make install-tools-python    # Install Python development tools
make check-tools-python      # Check Python tool status
```

## Quality Standards

| Check Item | Standard | Tool |
|------------|----------|------|
| Code Format | PEP 8 standard | black |
| Import Sorting | Standard library, third-party, local | isort |
| Code Style | PEP 8 + flake8 rules | flake8 |
| Type Checking | Strict type checking | mypy |
| Code Quality | Comprehensive quality analysis | pylint |
| Line Length | ≤88 characters | black |
| Complexity | Cyclomatic complexity ≤10 | pylint |

## Common Issues

### Formatting Issues
```bash
make fmt-python  # Auto-fix format issues
# Internal execution: black + isort
```

### Style Check Issues
```bash
make check-python  # Run all quality checks
# Internal execution: flake8 + mypy + pylint
```

### Type Check Issues
```bash
# mypy will detect type errors
# Need to add type annotations or fix type issues
```

### Complexity Issues
```bash
# pylint will detect overly complex functions
# Need to refactor functions with complexity >10
```

## Configuration Files

### pyproject.toml Configuration
```toml
[tool.black]
line-length = 88
target-version = ['py38', 'py39', 'py310', 'py311']
include = '\.pyi?$'
extend-exclude = '''
/(
  # directories
  \.eggs
  | \.git
  | \.hg
  | \.mypy_cache
  | \.tox
  | \.venv
  | build
  | dist
)/
'''

[tool.isort]
profile = "black"
multi_line_output = 3
line_length = 88
known_first_party = ["your_package_name"]

[tool.mypy]
python_version = "3.8"
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true
disallow_incomplete_defs = true
check_untyped_defs = true
disallow_untyped_decorators = true
no_implicit_optional = true
warn_redundant_casts = true
warn_unused_ignores = true
warn_no_return = true
warn_unreachable = true
strict_equality = true

[tool.pylint.messages_control]
disable = [
    "C0330",  # wrong-import-position
    "C0326",  # bad-whitespace
]

[tool.pylint.format]
max-line-length = 88

[tool.pylint.design]
max-args = 7
max-locals = 15
max-returns = 6
max-branches = 12
max-statements = 50
max-attributes = 10
max-public-methods = 20
max-bool-expr = 5
```

### .flake8 Configuration
```ini
[flake8]
max-line-length = 88
extend-ignore = E203, W503
exclude = 
    .git,
    __pycache__,
    .venv,
    .eggs,
    *.egg,
    build,
    dist
```

## Related Resources

- [PEP 8 - Python Code Style Guide](https://pep8.org/)
- [Black Documentation](https://black.readthedocs.io/)
- [isort Documentation](https://pycqa.github.io/isort/)
- [flake8 Documentation](https://flake8.pycqa.org/)
- [mypy Documentation](https://mypy.readthedocs.io/)
- [pylint Documentation](https://pylint.pycqa.org/)
