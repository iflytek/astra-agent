# OpenStellar 项目完整部署指南

本指南将帮助您按照正确的顺序启动 OpenStellar 项目的所有组件，包括身份认证、知识库和核心服务。

## 📋 项目架构概述

OpenStellar 项目包含以下三个主要组件：

1. **Casdoor** - 身份认证和单点登录服务
2. **RagFlow** - 知识库和文档检索服务
3. **OpenStellar** - 核心业务服务集群

## 🚀 部署步骤

### 前置要求

- Docker Engine 20.10+
- Docker Compose 2.0+
- 至少 16GB 可用内存
- 至少 50GB 可用磁盘空间

### 第一步：启动 Casdoor 身份认证服务（根据需要部署）

Casdoor 是一个开源的身份和访问管理平台，提供OAuth 2.0、OIDC、SAML等多种认证协议支持。

```bash
# 进入 Casdoor 目录
cd docker/casdoor

# 修改环境变量配置
vim conf/app.conf

# 启动 Casdoor 服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

**服务信息：**
- 访问地址：http://localhost:8000
- 容器名称：casdoor
- 默认配置：生产模式 (GIN_MODE=release)

**配置目录：**
- 配置文件：`./conf` 目录
- 日志文件：`./logs` 目录

### 第二步：启动 RagFlow 知识库服务

RagFlow 是一个开源的RAG（检索增强生成）引擎，使用深度文档理解技术提供准确的问答服务。

```bash
# 进入 RagFlow 目录
cd docker/ragflow

# 复制环境变量配置（如果存在）
cp .env.example .env

# 启动 RagFlow 服务（包含所有依赖）
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f ragflow
```

**RagFlow 服务组件：**
- **ragflow-server**：主服务 (端口 9380)
- **ragflow-mysql**：MySQL数据库 (端口 3306)
- **ragflow-redis**：Redis缓存 (端口 6379)
- **ragflow-minio**：对象存储 (端口 9000, 控制台 9001)
- **ragflow-es-01** 或 **ragflow-opensearch-01**：搜索引擎 (端口 9200/9201)
- **ragflow-infinity**：向量数据库 (可选)

**访问地址：**
- RagFlow Web界面：http://localhost:9380
- MinIO控制台：http://localhost:9001

**重要配置说明：**
- 默认使用 Elasticsearch，如需使用 OpenSearch，请修改 docker-compose.yml 中的 profiles 配置
- 支持GPU加速，使用 `docker-compose-gpu.yml` 启动

### 第三步：配置 OpenStellar 核心服务

在启动 OpenStellar 服务之前，需要配置相关的连接信息以集成 Casdoor 和 RagFlow。

#### 3.1 配置知识库服务连接

编辑 `docker/astronAgent/.env` 文件，配置 RagFlow 连接信息：

**关键配置项：**

```env
# RAGFlow配置
RAGFLOW_BASE_URL=http://localhost:9380
RAGFLOW_API_TOKEN=ragflow-your-api-token-here
RAGFLOW_TIMEOUT=60
RAGFLOW_DEFAULT_GROUP=星辰知识库
```

**获取 RagFlow API Token：**
1. 访问 RagFlow Web界面：http://localhost:9380
2. 登录并进入用户设置
3. 生成 API Token
4. 将 Token 更新到配置文件中

#### 3.2 配置 Casdoor 认证集成

根据您的需求配置 Casdoor 认证集成，主要包括：

1. **OAuth 应用注册**：在 Casdoor 中注册 OpenStellar 应用
2. **回调地址配置**：设置正确的回调URL
3. **权限配置**：配置用户角色和权限

### 第四步：启动 astronAgent 核心服务

```bash
# 进入 astronAgent 目录
cd docker/astronAgent

# 复制环境变量配置
cp .env.example .env

# 根据需要修改配置
vim .env

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f
```

## 📊 服务访问地址

启动完成后，您可以通过以下地址访问各项服务：

### 认证服务
- **Casdoor 管理界面**：http://localhost:8000

### 知识库服务
- **RagFlow Web界面**：http://localhost:9380
- **MinIO 控制台**：http://localhost:9001 (minioadmin/minioadmin)

### OpenStellar 核心服务
- **控制台前端**：http://localhost:1881
- **控制台Hub API**：http://localhost:8080

### 中间件服务
- **PostgreSQL**：localhost:5432
- **MySQL**：localhost:3306
- **Redis**：localhost:6379
- **Elasticsearch**：http://localhost:9200
- **Kafka**：localhost:9092
- **MinIO**：http://localhost:9000

## 🔍 故障排除

### 1. 服务启动失败

```bash
# 查看详细错误日志
docker-compose logs service-name

# 检查端口占用
netstat -tlnp | grep :端口号

# 检查资源使用
docker stats
```

### 2. 服务间连接问题

**常见问题：**
- 确保所有服务都在同一网络中
- 检查服务名称解析是否正确
- 验证端口配置是否一致

**解决方案：**
```bash
# 查看网络配置
docker network ls
docker network inspect [network-name]

# 测试服务连通性
docker exec -it container-name ping target-service-name
```

### 3. 配置文件问题

**检查配置文件语法：**
```bash
# 验证 docker-compose 文件
docker-compose config

# 检查环境变量
docker-compose config --services
```

### 4. 数据持久化问题

```bash
# 查看数据卷
docker volume ls

# 检查数据卷挂载
docker volume inspect volume-name
```

## 📚 更多资源

- [OpenStellar 官方文档](https://docs.openstellar.cn)
- [Casdoor 官方文档](https://casdoor.org/docs/overview)
- [RagFlow 官方文档](https://ragflow.io/docs)
- [Docker Compose 官方文档](https://docs.docker.com/compose/)

## 🤝 技术支持

如遇到问题，请：

1. 查看相关服务的日志文件
2. 检查官方文档和故障排除指南
3. 在项目 GitHub 仓库提交 Issue
4. 联系技术支持团队

---

**注意**：首次部署建议在测试环境中验证所有功能后再部署到生产环境。