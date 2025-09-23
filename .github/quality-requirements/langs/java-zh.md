# Java代码质量检测

## 工具链

### 格式化工具
- **Spotless**: 基于Google Java Format的自动格式化
- **Maven集成**: 通过spotless-maven-plugin实现

### 质量检测工具
- **Checkstyle**: 代码风格验证（Google Java Style Guide）
- **SpotBugs**: 静态分析和bug检测
- **PMD**: 代码质量分析和复杂度控制

## Makefile集成

### 统一命令
```bash
make format    # 格式化所有语言（包含Java）
make check     # 检查所有语言质量（包含Java）
```

### Java专用命令
```bash
make fmt-java              # 格式化Java代码
make check-java            # Java质量检查
make test-java             # 运行Java测试
make build-java            # 构建Java项目
```

### 工具安装
```bash
make install-tools-java    # 安装Java开发工具
make check-tools-java      # 检查Java工具状态
```

## 质量标准

| 检测项 | 标准 | 工具 |
|--------|------|------|
| 代码格式 | Google Java Format | Spotless |
| 代码风格 | Google Java Style Guide | Checkstyle |
| 行长度 | ≤120字符 | Checkstyle |
| 圈复杂度 | 函数≤10，类≤40 | PMD |
| 方法长度 | ≤50行 | PMD |
| 参数数量 | ≤7个 | PMD |
| 类长度 | ≤500行 | PMD |
| 静态分析 | 0 issues | SpotBugs |

## 常见问题

### 格式化问题
```bash
make fmt-java  # 自动修复格式问题
# 内部执行: mvn spotless:apply
```

### 风格检查问题
```bash
make check-java  # 运行所有质量检查
# 内部执行: mvn checkstyle:check pmd:check spotbugs:check
```

### 复杂度问题
```bash
# PMD会检测复杂度过高的方法
# 需要重构复杂度>10的方法
```

### 静态分析问题
```bash
# SpotBugs会检测潜在bug
# 根据报告建议修复代码
```

## 配置文件

### Maven插件配置 (pom.xml)
```xml
<properties>
    <spotless.version>2.43.0</spotless.version>
    <checkstyle.version>3.3.1</checkstyle.version>
    <spotbugs.version>4.8.2.0</spotbugs.version>
    <pmd.version>3.21.2</pmd.version>
</properties>
```

### Checkstyle配置 (checkstyle.xml)
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

## 相关资源

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Spotless文档](https://github.com/diffplug/spotless)
- [Checkstyle文档](https://checkstyle.sourceforge.io/)
- [SpotBugs文档](https://spotbugs.github.io/)
- [PMD文档](https://pmd.github.io/)