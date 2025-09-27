# Java Code Quality Detection

## Toolchain

### Formatting Tools
- **Spotless**: Automatic formatting based on Google Java Format
- **Maven Integration**: Implemented through spotless-maven-plugin

### Quality Detection Tools
- **Checkstyle**: Code style validation (Google Java Style Guide)
- **SpotBugs**: Static analysis and bug detection
- **PMD**: Code quality analysis and complexity control

## Makefile Integration

### Unified Commands
```bash
make format    # Format all languages (including Java)
make check     # Check all language quality (including Java)
```

### Java-specific Commands
```bash
make fmt-java              # Format Java code
make check-java            # Java quality check
make test-java             # Run Java tests
make build-java            # Build Java project
```

### Tool Installation
```bash
make install-tools-java    # Install Java development tools
make check-tools-java      # Check Java tool status
```

## Quality Standards

| Check Item | Standard | Tool |
|------------|----------|------|
| Code Format | Google Java Format | Spotless |
| Code Style | Google Java Style Guide | Checkstyle |
| Line Length | ≤120 characters | Checkstyle |
| Cyclomatic Complexity | Function ≤10, Class ≤40 | PMD |
| Method Length | ≤50 lines | PMD |
| Parameter Count | ≤7 parameters | PMD |
| Class Length | ≤500 lines | PMD |
| Static Analysis | 0 issues | SpotBugs |

## Common Issues

### Formatting Issues
```bash
make fmt-java  # Auto-fix format issues
# Internal execution: mvn spotless:apply
```

### Style Check Issues
```bash
make check-java  # Run all quality checks
# Internal execution: mvn checkstyle:check pmd:check spotbugs:check
```

### Complexity Issues
```bash
# PMD will detect overly complex methods
# Need to refactor methods with complexity >10
```

### Static Analysis Issues
```bash
# SpotBugs will detect potential bugs
# Fix code according to report suggestions
```

## Configuration Files

### Maven Plugin Configuration (pom.xml)
```xml
<properties>
    <spotless.version>2.43.0</spotless.version>
    <checkstyle.version>3.3.1</checkstyle.version>
    <spotbugs.version>4.8.2.0</spotbugs.version>
    <pmd.version>3.21.2</pmd.version>
</properties>
```

### Checkstyle Configuration (checkstyle.xml)
```xml
<module name="Checker">
    <module name="TreeWalker">
        <module name="LineLength">
            <property name="max" value="120"/>
        </module>
        <module name="CyclomaticComplexity">
            <property name="max" value="10"/>
        </module>
    </module>
</module>
```

## Related Resources

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Spotless Documentation](https://github.com/diffplug/spotless)
- [Checkstyle Documentation](https://checkstyle.sourceforge.io/)
- [SpotBugs Documentation](https://spotbugs.github.io/)
- [PMD Documentation](https://pmd.github.io/)
