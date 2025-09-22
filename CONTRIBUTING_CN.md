# 为 Astra Agent 做出贡献

感谢您对 Astra Agent 项目的关注！我们欢迎社区贡献，感谢您帮助改进这个项目。

## 目录

- [行为准则](#行为准则)
- [快速开始](#快速开始)
- [开发环境搭建](#开发环境搭建)
- [项目结构](#项目结构)
- [开发工作流](#开发工作流)
- [代码质量标准](#代码质量标准)
- [测试指南](#测试指南)
- [文档规范](#文档规范)
- [提交变更](#提交变更)
- [问题报告指南](#问题报告指南)
- [拉取请求指南](#拉取请求指南)
- [发布流程](#发布流程)
- [社区准则](#社区准则)

## 行为准则

本项目遵循行为准则。参与项目时，请遵守此准则。如遇到不当行为，请向项目维护者举报。

请阅读我们的[行为准则](.github/code_of_conduct.md)，了解我们为所有贡献者提供欢迎和包容环境的承诺。

## 快速开始

### 前置要求

在开始贡献之前，请确保已安装以下工具：

- **Java 21+** (用于后端服务)
- **Maven 3.8+** (用于 Java 项目管理)
- **Node.js 18+** (用于前端开发)
- **Python 3.9+** (用于核心服务)
- **Go 1.21+** (用于租户服务)
- **Docker & Docker Compose** (用于容器化服务)
- **Git** (用于版本控制)

### Fork 和克隆

1. 在 GitHub 上 Fork 仓库
2. 克隆您的 Fork 到本地：
   ```bash
   git clone https://github.com/your-username/astra-agent.git
   cd astra-agent
   ```
3. 添加上游仓库：
   ```bash
   git remote add upstream https://github.com/iflytek/astra-agent.git
   ```

## 开发环境搭建

### 一键设置

运行自动化设置脚本来安装所有必需工具并配置环境：

```bash
make dev-setup
```

此命令将：
- 安装语言特定的开发工具
- 配置代码质量的 Git 钩子
- 设置分支命名约定
- 安装所有模块的依赖

### 手动设置

如果您偏好手动设置或需要安装特定组件：

```bash
# 安装开发工具
make install-tools

# 检查工具安装状态
make check-tools

# 安装 Git 钩子
make hooks-install
```

## 项目结构

Astra Agent 是一个基于微服务的平台，具有以下结构：

```
astra-agent/
├── console/                   # 控制台子系统
│   ├── backend/               # Java Spring Boot 服务
│   │   ├── auth/              # 认证服务
│   │   ├── commons/           # 共享工具
│   │   ├── hub/               # 主要业务逻辑
│   │   ├── toolkit/           # 工具包服务
│   │   └── config/            # 质量配置
│   └── frontend/              # React TypeScript SPA
├── core/                      # 核心平台服务
│   ├── agent/                 # 智能体执行引擎 (Python)
│   ├── common/                # 共享 Python 库
│   ├── knowledge/             # 知识库服务 (Python)
│   ├── memory/                # 内存管理
│   ├── plugin/                # 插件系统
│   ├── tenant/                # 多租户服务 (Go)
│   └── workflow/              # 工作流编排 (Python)
├── docs/                      # 文档
├── makefiles/                 # 构建系统组件
└── .github/                   # GitHub 配置
    └── quality-requirements/  # 代码质量标准
```

## 开发工作流

### 分支管理

遵循我们的分支命名约定：

| 分支类型 | 格式 | 示例 | 用途 |
|---------|------|------|------|
| 功能分支 | `feature/功能名` | `feature/user-auth` | 新功能开发 |
| 修复分支 | `bugfix/问题名` | `bugfix/login-error` | Bug 修复 |
| 热修复分支 | `hotfix/补丁名` | `hotfix/security-patch` | 紧急修复 |
| 文档分支 | `doc/文档名` | `doc/api-guide` | 文档更新 |

### 创建分支

使用 Makefile 命令创建一致的分支：

```bash
# 创建功能分支
make new-feature name=user-authentication

# 创建修复分支
make new-bugfix name=login-timeout

# 创建热修复分支
make new-hotfix name=security-vulnerability
```

### 日常开发命令

```bash
# 格式化所有代码
make format

# 运行质量检查
make check

# 运行测试
make test

# 构建所有项目
make build

# 安全推送（带预检查）
make safe-push
```

## 代码质量标准

### 多语言支持

Astra Agent 支持多种编程语言，具有统一的质量标准：

| 语言 | 格式化 | 质量工具 | 标准 |
|------|--------|----------|------|
| **Go** | gofmt + goimports + gofumpt | golangci-lint + staticcheck | Go 标准格式，复杂度 ≤10 |
| **Java** | Spotless (Google Java Format) | Checkstyle + PMD + SpotBugs | Google Java 风格，复杂度 ≤10 |
| **Python** | black + isort | flake8 + mypy + pylint | PEP 8，复杂度 ≤10 |
| **TypeScript** | prettier | eslint + tsc | ESLint 规则，严格类型检查 |

### 代码质量要求

所有代码必须通过以下检查：

- **格式化**：应用自动代码格式化
- **代码检查**：无 linting 错误或警告
- **类型检查**：严格类型检查 (TypeScript/Python)
- **复杂度**：圈复杂度 ≤10
- **测试**：充分的测试覆盖率
- **文档**：清晰的代码注释和文档

### 质量检查命令

```bash
# 检查所有语言
make check

# 检查特定语言
make check-go
make check-java
make check-python
make check-typescript
```

## 测试指南

### 测试结构

- **单元测试**：独立测试各个组件
- **集成测试**：测试组件交互
- **端到端测试**：测试完整的用户工作流

### 运行测试

```bash
# 运行所有测试
make test

# 运行特定语言测试
make test-go
make test-java
make test-python
make test-typescript

# 运行覆盖率测试
make test-coverage
```

### 测试要求

- 所有新功能必须包含测试
- Bug 修复必须包含回归测试
- 测试覆盖率不应降低
- 测试必须是确定性的且快速

## 文档规范

### 代码文档

- 使用清晰、简洁的注释
- 记录公共 API 和接口
- 在适当的地方包含使用示例
- 遵循语言特定的文档标准

### 项目文档

- 为重大变更更新 README 文件
- 记录新功能和 API
- 维护最新的安装和设置指南
- 包含故障排除信息

## 提交变更

### 提交消息格式

遵循 Conventional Commits 规范：

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**类型：**
- `feat`：新功能
- `fix`：Bug 修复
- `docs`：文档更新
- `style`：代码格式化
- `refactor`：代码重构
- `test`：测试相关变更
- `chore`：构建工具、依赖更新

**示例：**
```bash
feat(auth): 添加 OAuth2 认证支持
fix(api): 修复用户信息查询接口
docs(guide): 完善快速开始指南
```

### 提交前检查清单

提交前，请确保：

- [ ] 代码已格式化 (`make format`)
- [ ] 质量检查通过 (`make check`)
- [ ] 测试通过 (`make test`)
- [ ] 分支命名遵循约定
- [ ] 提交消息遵循格式
- [ ] 如需要，文档已更新

## 问题报告指南

### 报告 Bug

报告 Bug 时，请包含：

1. **清晰描述**问题
2. **重现步骤**
3. **预期行为**与实际行为
4. **环境详情**（操作系统、版本等）
5. **相关日志**或错误消息
6. **截图**（如适用）

### 功能请求

功能请求时，请包含：

1. **清晰描述**功能
2. **使用场景**和动机
3. **建议解决方案**或方法
4. **考虑的替代方案**
5. **其他上下文**或参考资料

## 拉取请求指南

### 提交前

- [ ] Fork 仓库并创建功能分支
- [ ] 按照编码标准进行更改
- [ ] 为新功能添加测试
- [ ] 根据需要更新文档
- [ ] 确保所有检查在本地通过
- [ ] 基于最新的 main 分支进行 rebase

### PR 描述模板

```markdown
## 描述
变更的简要描述

## 变更类型
- [ ] Bug 修复
- [ ] 新功能
- [ ] 破坏性变更
- [ ] 文档更新

## 测试
- [ ] 添加/更新单元测试
- [ ] 添加/更新集成测试
- [ ] 完成手动测试

## 检查清单
- [ ] 代码遵循项目风格指南
- [ ] 完成自我审查
- [ ] 更新文档
- [ ] 无破坏性变更（或已记录）
```

### 审查流程

1. **自动化检查**：所有 PR 必须通过自动化质量检查
2. **代码审查**：至少一位维护者必须批准
3. **测试**：所有测试必须通过
4. **文档**：如需要，文档必须更新

## 发布流程

### 版本控制

我们遵循[语义化版本控制](https://semver.org/)：

- **主版本**：破坏性变更
- **次版本**：新功能（向后兼容）
- **补丁版本**：Bug 修复（向后兼容）

### 发布工作流

1. 从 main 创建发布分支
2. 更新版本号和变更日志
3. 运行完整测试套件
4. 创建发布 PR 供审查
5. 合并并标记发布
6. 部署到生产环境

## 社区准则

### 沟通

- 保持尊重和包容
- 使用清晰、建设性的语言
- 提供有用的反馈
- 需要时提出问题

### 获取帮助

- 首先查看现有文档
- 搜索现有问题和讨论
- 在讨论或问题中提问
- 如有可用，加入社区频道

### 认可

贡献者将在以下方面得到认可：
- 发布说明
- 贡献者列表
- 社区亮点

## 其他资源

- [分支与提交规范](.github/quality-requirements/branch-commit-standards-zh.md)
- [代码质量要求](.github/quality-requirements/code-requirements-zh.md)
- [Makefile 使用指南](docs/Makefile-readme-zh.md)
- [项目 README](README.md)

## 有问题？

如果您对贡献有疑问，请：

1. 首先查看 `docs/` 目录中的文档
2. 查看现有问题和讨论
3. 创建带有 "question" 标签的新问题
4. 联系维护者

感谢您为 Astra Agent 做出贡献！🚀
