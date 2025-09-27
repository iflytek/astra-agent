# 代码质量检测文档

本目录包含各语言的代码质量检测工具说明，与Makefile工具链集成。

## 支持的语言

| 语言 | 文档 | Makefile命令 | 工具链 |
|------|------|-------------|--------|
| **Go** | [`go-zh.md`](./langs/go-zh.md) | `make fmt-go`, `make check-go` | gofmt + goimports + gofumpt + golines + staticcheck + golangci-lint |
| **Java** | [`java-zh.md`](./langs/java-zh.md) | `make fmt-java`, `make check-java` | spotless + checkstyle + spotbugs + pmd |
| **Python** | [`python-zh.md`](./langs/python-zh.md) | `make fmt-python`, `make check-python` | black + isort + flake8 + mypy + pylint |
| **TypeScript** | [`typescript-zh.md`](./langs/typescript-zh.md) | `make fmt-typescript`, `make check-typescript` | prettier + eslint + tsc |

## 快速使用

### 统一命令（推荐）
```bash
make format    # 格式化所有语言
make check     # 检查所有语言质量
```

### 单语言命令
```bash
make fmt-go && make check-go           # Go
make fmt-java && make check-java       # Java  
make fmt-python && make check-python   # Python
make fmt-typescript && make check-typescript  # TypeScript
```

## 文档说明

每个语言文档包含：
- 工具链说明
- 质量标准
- Makefile集成方式
- 常见问题解决

## 相关文档

- [分支与提交规范](./branch-commit-standards-zh.md) - 分支管理和提交消息规范
- [Makefile使用指南](../docs/Makefile-readme.md) - 完整的Makefile命令说明
- [本地开发配置](../docs/Makefile-readme.md#local-development-configuration) - 使用`.localci.toml`进行模块化开发