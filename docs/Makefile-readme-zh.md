# 🚀 多语言CI/CD工具链

> **统一开发工作流，支持Go、Java、Python、TypeScript**

## 快速开始

### 一次性设置
```bash
make setup
```
安装所有语言工具，配置Git钩子，设置分支策略。

### 日常命令
```bash
make format    # 格式化所有代码
make check     # 质量检查
make test      # 运行测试
make build     # 构建项目
make push      # 安全推送（带预检查）
make clean     # 清理构建产物
```

### 项目状态
```bash
make status    # 显示项目信息
make info      # 显示工具版本
```

## 本地开发配置

为了提高本地开发效率，可以在根目录创建 `.localci.toml` 文件来覆盖默认配置：

### 创建本地配置
```bash
# 复制默认配置
cp makefiles/localci.toml .localci.toml

# 编辑配置，只启用你正在开发的模块
# 设置 enabled = true 启用模块，false 禁用模块
```

### 本地配置示例
```toml
[meta]
version = 1

[[python.apps]]
name = "core-agent"
dir = "core/agent"
enabled = true    # 只启用你正在开发的模块

[[python.apps]]
name = "core-memory"
dir = "core/memory/database"
enabled = false   # 禁用其他模块以提高执行速度

# ... 其他模块设置为 enabled = false
```

### 优势
- **更快执行**: 只处理启用的模块
- **专注开发**: 在特定模块上工作，不受其他模块干扰
- **轻松切换**: 修改 `enabled` 值来切换不同模块

## 核心命令

### `make setup`
一次性环境搭建。安装工具，配置Git钩子，设置分支策略。

### `make format`
格式化所有语言的代码：
- Go: `gofmt` + `goimports` + `gofumpt` + `golines`
- Java: Maven `spotless:apply`
- Python: `black` + `isort`
- TypeScript: `prettier`

### `make check` (别名: `make lint`)
所有语言的质量检查：
- Go: `gocyclo` + `staticcheck` + `golangci-lint`
- Java: `checkstyle` + `pmd` + `spotbugs`
- Python: `flake8` + `mypy` + `pylint`
- TypeScript: `eslint` + `tsc`

### `make test`
运行所有项目的测试：
- Go: `go test` with coverage
- Java: `mvn test`
- Python: `pytest` with coverage
- TypeScript: `npm test`

### `make build`
构建所有项目：
- Go: 构建二进制文件
- Java: Maven `package`
- Python: 安装依赖
- TypeScript: Vite `build`

### `make push`
安全推送（带预检查）：
- 自动运行 `format` 和 `check`
- 验证分支命名
- 推送到远程仓库

### `make clean`
清理所有语言的构建产物。

## 运行服务

```bash
# Go服务
cd core/tenant && go run cmd/main.go

# Java服务
cd console/backend && mvn spring-boot:run

# Python服务
cd core/memory/database && python main.py
cd core/agent && python main.py

# TypeScript前端
cd console/frontend && npm run dev
```

## 其他命令

### `make status`
显示项目信息和活跃项目。

### `make info`
显示工具版本和安装状态。

### `make fix`
自动修复代码问题（格式化 + 部分lint修复）。

### `make ci`
完整CI流程：`format` + `check` + `test` + `build`。

### `make hooks`
Git钩子管理：
- `make hooks-install` - 安装完整钩子
- `make hooks-install-basic` - 安装轻量级钩子
- `make hooks-uninstall` - 卸载钩子

### `make enable-legacy`
启用专用语言命令，实现向后兼容。

## 专用命令

运行 `make enable-legacy` 后，可以使用语言专用命令：

### Go命令
```bash
make fmt-go              # 格式化Go代码
make check-go            # Go质量检查
make test-go             # 运行Go测试
make build-go            # 构建Go项目
```

### Java命令
```bash
make fmt-java            # 格式化Java代码
make check-java          # Java质量检查
make test-java           # 运行Java测试
make build-java          # 构建Java项目
```

### Python命令
```bash
make fmt-python          # 格式化Python代码
make check-python        # Python质量检查
make test-python         # 运行Python测试
```

### TypeScript命令
```bash
make fmt-typescript      # 格式化TypeScript代码
make check-typescript    # TypeScript质量检查
make test-typescript     # 运行TypeScript测试
make build-typescript    # 构建TypeScript项目
```

## Git钩子

### 安装钩子
```bash
make hooks-install       # 完整钩子（格式化+检查）
make hooks-install-basic # 轻量级钩子（仅格式化）
```

### 分支命名
```bash
feature/user-auth        # 功能分支
bugfix/fix-login         # 错误修复
hotfix/security-patch    # 热修复
```

### 提交信息
```bash
feat: add user authentication
fix: resolve login timeout
docs: update API documentation
```

## 故障排除

### 常见问题
```bash
# 工具安装问题
make info                # 检查工具状态
make install-tools       # 重新安装工具

# 项目检测问题
make status              # 检查项目状态
make _debug              # 调试检测

# 钩子问题
make hooks-uninstall && make hooks-install

# 本地配置问题
rm .localci.toml         # 删除本地配置，使用默认配置
cp makefiles/localci.toml .localci.toml  # 重置本地配置
```
