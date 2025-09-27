# Python代码质量检测

## 工具链

### 格式化工具
- **black**: 代码格式化（PEP 8标准）
- **isort**: 导入语句排序和整理

### 质量检测工具
- **flake8**: 代码风格和错误检查
- **mypy**: 静态类型检查
- **pylint**: 综合代码质量分析

## Makefile集成

### 统一命令
```bash
make format    # 格式化所有语言（包含Python）
make check     # 检查所有语言质量（包含Python）
```

### Python专用命令
```bash
make fmt-python          # 格式化Python代码
make check-python        # Python质量检查
make test-python         # 运行Python测试
```

### 工具安装
```bash
make install-tools-python    # 安装Python开发工具
make check-tools-python      # 检查Python工具状态
```

## 质量标准

| 检测项 | 标准 | 工具 |
|--------|------|------|
| 代码格式 | PEP 8标准 | black |
| 导入排序 | 标准库、第三方、本地 | isort |
| 代码风格 | PEP 8 + flake8规则 | flake8 |
| 类型检查 | 严格类型检查 | mypy |
| 代码质量 | 综合质量分析 | pylint |
| 行长度 | ≤88字符 | black |
| 复杂度 | 圈复杂度≤10 | pylint |

## 常见问题

### 格式化问题
```bash
make fmt-python  # 自动修复格式问题
# 内部执行: black + isort
```

### 风格检查问题
```bash
make check-python  # 运行所有质量检查
# 内部执行: flake8 + mypy + pylint
```

### 类型检查问题
```bash
# mypy会检测类型错误
# 需要添加类型注解或修复类型问题
```

### 复杂度问题
```bash
# pylint会检测复杂度过高的函数
# 需要重构复杂度>10的函数
```

## 配置文件

### pyproject.toml配置
```toml
[tool.black]
line-length = 88
target-version = ['py38', 'py39', 'py310', 'py311']
include = '\.pyi?$'
extend-exclude = '''
/(
  # directories
  \.eggs
  | \.git
  | \.hg
  | \.mypy_cache
  | \.tox
  | \.venv
  | build
  | dist
)/
'''

[tool.isort]
profile = "black"
multi_line_output = 3
line_length = 88
known_first_party = ["your_package_name"]

[tool.mypy]
python_version = "3.8"
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true
disallow_incomplete_defs = true
check_untyped_defs = true
disallow_untyped_decorators = true
no_implicit_optional = true
warn_redundant_casts = true
warn_unused_ignores = true
warn_no_return = true
warn_unreachable = true
strict_equality = true

[tool.pylint.messages_control]
disable = [
    "C0330",  # wrong-import-position
    "C0326",  # bad-whitespace
]

[tool.pylint.format]
max-line-length = 88

[tool.pylint.design]
max-args = 7
max-locals = 15
max-returns = 6
max-branches = 12
max-statements = 50
max-attributes = 10
max-public-methods = 20
max-bool-expr = 5
```

### .flake8配置
```ini
[flake8]
max-line-length = 88
extend-ignore = E203, W503
exclude = 
    .git,
    __pycache__,
    .venv,
    .eggs,
    *.egg,
    build,
    dist
```

## 相关资源

- [PEP 8 - Python代码风格指南](https://pep8.org/)
- [Black文档](https://black.readthedocs.io/)
- [isort文档](https://pycqa.github.io/isort/)
- [flake8文档](https://flake8.pycqa.org/)
- [mypy文档](https://mypy.readthedocs.io/)
- [pylint文档](https://pylint.pycqa.org/)
