# TypeScript代码质量检测

## 工具链

### 格式化工具
- **prettier**: 代码格式化（统一代码风格）
- **全局安装**: 避免项目空间污染

### 质量检测工具
- **eslint**: 代码规范和最佳实践检查
- **tsc**: TypeScript编译器类型检查
- **@typescript-eslint**: TypeScript专用ESLint规则

## Makefile集成

### 统一命令
```bash
make format    # 格式化所有语言（包含TypeScript）
make check     # 检查所有语言质量（包含TypeScript）
```

### TypeScript专用命令
```bash
make fmt-typescript      # 格式化TypeScript代码
make check-typescript    # TypeScript质量检查
make test-typescript     # 运行TypeScript测试
make build-typescript    # 构建TypeScript项目
```

### 工具安装
```bash
make install-tools-typescript    # 全局安装TypeScript工具
make check-tools-typescript      # 检查TypeScript工具状态
```

## 质量标准

| 检测项 | 标准 | 工具 |
|--------|------|------|
| 代码格式 | Prettier标准 | prettier |
| 代码规范 | ESLint规则 | eslint |
| 类型检查 | 严格类型检查 | tsc |
| Import管理 | 自动排序 | eslint-plugin-import |
| 代码复杂度 | 圈复杂度≤10 | eslint-complexity |
| 最佳实践 | TypeScript最佳实践 | @typescript-eslint |

## 常见问题

### 格式化问题
```bash
make fmt-typescript  # 自动修复格式问题
# 内部执行: prettier --write
```

### 代码规范问题
```bash
make check-typescript  # 运行所有质量检查
# 内部执行: eslint + tsc
```

### 类型检查问题
```bash
# tsc会检测类型错误
# 需要添加类型注解或修复类型问题
```

### 复杂度问题
```bash
# eslint会检测复杂度过高的函数
# 需要重构复杂度>10的函数
```

## 配置文件

### .prettierrc配置
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

### .eslintrc.js配置
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

### tsconfig.json配置
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

## 相关资源

- [TypeScript官方文档](https://www.typescriptlang.org/)
- [Prettier文档](https://prettier.io/)
- [ESLint文档](https://eslint.org/)
- [TypeScript ESLint文档](https://typescript-eslint.io/)
