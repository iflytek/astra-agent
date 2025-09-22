# TypeScript Code Quality Detection

## Toolchain

### Formatting Tools
- **prettier**: Code formatting (unified code style)
- **Global Installation**: Avoid project space pollution

### Quality Detection Tools
- **eslint**: Code standards and best practices checking
- **tsc**: TypeScript compiler type checking
- **@typescript-eslint**: TypeScript-specific ESLint rules

## Makefile Integration

### Unified Commands
```bash
make format    # Format all languages (including TypeScript)
make check     # Check all language quality (including TypeScript)
```

### TypeScript-specific Commands
```bash
make fmt-typescript      # Format TypeScript code
make check-typescript    # TypeScript quality check
make test-typescript     # Run TypeScript tests
make build-typescript    # Build TypeScript project
```

### Tool Installation
```bash
make install-tools-typescript    # Global TypeScript tool installation
make check-tools-typescript      # Check TypeScript tool status
```

## Quality Standards

| Check Item | Standard | Tool |
|------------|----------|------|
| Code Format | Prettier standard | prettier |
| Code Standards | ESLint rules | eslint |
| Type Checking | Strict type checking | tsc |
| Import Management | Auto sorting | eslint-plugin-import |
| Code Complexity | Cyclomatic complexity ≤10 | eslint-complexity |
| Best Practices | TypeScript best practices | @typescript-eslint |

## Common Issues

### Formatting Issues
```bash
make fmt-typescript  # Auto-fix format issues
# Internal execution: prettier --write
```

### Code Standards Issues
```bash
make check-typescript  # Run all quality checks
# Internal execution: eslint + tsc
```

### Type Check Issues
```bash
# tsc will detect type errors
# Need to add type annotations or fix type issues
```

### Complexity Issues
```bash
# eslint will detect overly complex functions
# Need to refactor functions with complexity >10
```

## Configuration Files

### .prettierrc Configuration
```json
{
  "semi": true,
  "trailingComma": "es5",
  "singleQuote": true,
  "printWidth": 80,
  "tabWidth": 2,
  "useTabs": false
}
```

### .eslintrc.js Configuration
```javascript
module.exports = {
  parser: '@typescript-eslint/parser',
  plugins: ['@typescript-eslint'],
  extends: [
    'eslint:recommended',
    '@typescript-eslint/recommended',
    'prettier'
  ],
  rules: {
    '@typescript-eslint/no-unused-vars': 'error',
    '@typescript-eslint/explicit-function-return-type': 'warn',
    'complexity': ['error', 10]
  }
};
```

### tsconfig.json Configuration
```json
{
  "compilerOptions": {
    "strict": true,
    "noImplicitAny": true,
    "noImplicitReturns": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true
  }
}
```

## Related Resources

- [TypeScript Official Documentation](https://www.typescriptlang.org/)
- [Prettier Documentation](https://prettier.io/)
- [ESLint Documentation](https://eslint.org/)
- [TypeScript ESLint Documentation](https://typescript-eslint.io/)
