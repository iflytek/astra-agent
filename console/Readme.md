# 开源星辰控制台（Astra Console）

控制台子仓库，提供后台管理与用户可视化界面能力，涵盖用户与权限、智能体（Agent）广场与创建、数据集/知识库、工作流画布等核心功能。对应根仓库中的 `console` 目录，包含后端多模块与前端单页应用。

## 功能特性
- 用户与授权：登录、注册、鉴权、限流与审计
- 智能体管理：创建、上架、市场展示与分享
- 数据与知识库：数据集、文件管理与检索追踪
- 工作流与画布：可视化编排、模板管理
- 运营能力：统计、日志与配置管理

## 目录结构
```
console/
├─ backend/               后端多模块（Java · Spring Boot）
│  ├─ auth/               身份认证与授权登录模块
│  ├─ commons/            复用组件与通用 DTO/工具
│  ├─ config/             静态代码扫描与风格配置（Checkstyle/PMD/SpotBugs 等）
│  ├─ docker/             本地/CI 镜像与依赖服务（如 Redis/MinIO）
│  ├─ hub/                业务域聚合模块（实体、Mapper、Controller）
│  └─ toolkit/            后端工具模块
└─ frontend/              前端应用（Vite + React + TypeScript + Tailwind）
```

## 技术栈
- 后端：Java 17、Spring Boot、MyBatis、Lombok、Maven
- 前端：Node.js、Vite、React、TypeScript、Tailwind CSS、ESLint
- 中间件：Redis、MinIO（对象存储）
- 工具与质量：Checkstyle、PMD、SpotBugs、单元测试（JUnit）

## 环境要求
- Java 17+
- Maven 3.8+
- Node.js 18+ 与 npm/yarn（任选其一）
- Docker 及 Docker Compose（可选，用于一键启动依赖）

## 快速开始

### 1) 启动依赖（可选）
在 `console/backend/docker/compose.yml` 提供了常用依赖（如 Redis、MinIO）。
```bash
cd console/backend/docker
docker compose up -d
```

### 2) 后端运行
后端为 Maven 多模块工程，可在根 `console/backend` 目录构建与运行。
```bash
cd console/backend
mvn -q -DskipTests clean package

# 运行各服务（示例：auth、hub、toolkit）
cd auth && mvn -q spring-boot:run
# 另开终端
cd ../hub && mvn -q spring-boot:run
# 如需工具模块
cd ../toolkit && mvn -q spring-boot:run
```

应用的默认配置位于各模块的 `src/main/resources/application.yml`。如需覆盖，请通过环境变量或 `--spring.profiles.active` 指定外部配置。

### 3) 前端运行
```bash
cd console/frontend
npm install    # 或 yarn
npm run dev    # 或 yarn dev
```

默认使用 Vite 开发服务器，端口与代理可在 `console/frontend/vite.config.js` 中调整。

## 构建与打包
- 后端：
  - 本地构建：`mvn -q -DskipTests clean package`
  - 生成可执行 Jar，或使用各模块下的 `Dockerfile` 构建镜像
- 前端：
  - 生产构建：`npm run build`
  - 预览产物：`npm run preview`

## 配置说明
常见配置位于：
- 后端：`console/backend/*/src/main/resources/application.yml`
- 前端：`console/frontend/.env*` 与 `vite.config.js`

如需接入外部存储/缓存/数据库，请在相应模块的 `application.yml` 中调整连接信息，或通过环境变量注入。

## 测试
- 后端：在各模块内执行
```bash
cd console/backend
mvn -q test
```
- 前端：
  - 若使用 Vitest/Jest，请在前端根目录执行相应测试命令（可根据项目需要补充测试配置）。

## 代码规范
- Java：遵循本仓库 `console/backend/config` 下的 Checkstyle/PMD/SpotBugs 规则
- TypeScript/JavaScript：遵循 `console/frontend/eslint.config.js`
- 提交前建议本地执行格式化与静态检查，确保 CI 可通过

## 部署
- Docker：
  - 后端各模块均提供 `Dockerfile`，可分别构建与部署
  - 前端提供 `Dockerfile` 与 `nginx.conf`，可直接产出静态站点镜像
- K8s：
  - 前端包含 `deployment.yml` 样例，可按需调整镜像、环境变量与服务暴露

## 常见问题（FAQ）
- 端口冲突：修改后端 `server.port` 或前端 Vite 端口
- 存储不可用：确认 MinIO/Redis 服务已启动且网络可达
- 构建失败：检查 JDK/Maven/Node 版本是否满足要求，清理缓存后重试

## 贡献指南
欢迎通过 Issue/PR 贡献代码或文档：
- Fork 本仓库并创建特性分支（建议 `feat/*`、`fix/*`、`docs/*` 命名）
- 提交前确保通过构建与静态检查
- PR 中清晰描述变更动机、实现与影响范围

## 许可证
本项目遵循仓库根目录 `LICENSE` 所述的开源许可证。

## 致谢
感谢所有为开源星辰生态贡献代码、文档与反馈的开发者与社区伙伴。
