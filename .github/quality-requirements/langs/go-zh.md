# Go代码质量检测

## 工具链

### 格式化工具
- **gofmt**: Go官方格式化
- **goimports**: 自动管理imports
- **gofumpt**: 更严格的格式化
- **golines**: 控制行长度（120字符）

### 质量检测工具
- **gocyclo**: 圈复杂度检测（≤10）
- **staticcheck**: 静态分析
- **golangci-lint**: 综合代码规范检查

## Makefile集成

### 统一命令
```bash
make format    # 格式化所有语言（包含Go）
make check     # 检查所有语言质量（包含Go）
```

### Go专用命令
```bash
make fmt-go              # 格式化Go代码
make check-go            # Go质量检查
make test-go             # 运行Go测试
make build-go            # 构建Go项目
```

### 工具安装
```bash
make install-tools-go    # 安装Go开发工具
make check-tools-go      # 检查Go工具状态
```

## 质量标准

| 检测项 | 标准 | 工具 |
|--------|------|------|
| 代码格式 | Go标准格式 | gofmt + gofumpt |
| Import管理 | 无未使用导入 | goimports |
| 行长度 | ≤120字符 | golines |
| 函数复杂度 | 圈复杂度≤10 | gocyclo |
| 静态分析 | 0 issues | staticcheck |
| 代码规范 | 0 issues | golangci-lint |

## 常见问题

### 格式化问题
```bash
make fmt-go  # 自动修复格式问题
```

### Import问题
```bash
goimports -w .  # 自动修复imports
```

### 复杂度问题
```bash
gocyclo -over 10 .  # 检测复杂函数
# 需要重构复杂度>10的函数
```

### 静态分析问题
```bash
staticcheck ./...  # 查看详细报告
# 根据报告建议修复代码
```

## 配置文件

### golangci-lint配置 (`.golangci.yml`)
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

## 相关资源

- [Go官方代码规范](https://golang.org/doc/effective_go.html)
- [golangci-lint文档](https://golangci-lint.run/)
- [staticcheck文档](https://staticcheck.io/)