# =============================================================================
# Python Language Support - Makefile Module
# =============================================================================

# Python project variables - use dynamic directories from config
PYTHON := $(shell which python3 || which python)
BLACK := black
ISORT := isort --profile black
FLAKE8 := flake8 --max-line-length 88 --ignore=E203,W503,E501 --max-complexity 10
MYPY := mypy --disallow-untyped-defs --disallow-incomplete-defs --check-untyped-defs --no-implicit-optional
PYLINT := pylint --max-line-length=88 --max-args=7 --max-locals=15 --max-returns=6 --max-branches=12 --max-statements=50

# Get all Python directories from config
PYTHON_DIRS := $(shell \
	if [ -n "$(LOCALCI_CONFIG)" ] && [ -f "$(LOCALCI_CONFIG)" ]; then \
		makefiles/parse_localci.sh enabled python $(LOCALCI_CONFIG) | cut -d'|' -f2 | tr '\n' ' '; \
	else \
		echo "demo-apps/backends/python"; \
	fi)

PYTHON_PRIMARY_DIR := $(shell echo $(PYTHON_DIRS) | cut -d' ' -f1)
PYTHON_DIR := $(PYTHON_PRIMARY_DIR)
PYTHON_FILES := $(shell \
	for dir in $(PYTHON_DIRS); do \
		if [ -d "$$dir" ]; then \
			find $$dir -name "*.py" 2>/dev/null || true; \
		fi; \
	done)

# =============================================================================
# Core Python Commands
# =============================================================================

install-tools-python: ## 🛠️ Install Python development tools
	@if [ -d "$(PYTHON_DIR)" ]; then \
		echo "$(YELLOW)Installing Python tools...$(RESET)"; \
		$(PYTHON) -m pip install \
			black==24.4.2 \
			isort==5.13.2 \
			flake8==7.0.0 \
			mypy==1.9.0 \
			pylint==3.1.0 \
			pytest==8.0.0 \
			pytest-cov==4.0.0; \
		echo "$(GREEN)Python tools installed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Python tools (no Python project detected)$(RESET)"; \
	fi

check-tools-python: ## ✅ Check Python development tools availability
	@if [ -d "$(PYTHON_DIR)" ]; then \
		echo "$(YELLOW)Checking Python tools...$(RESET)"; \
		command -v $(PYTHON) >/dev/null 2>&1 || (echo "$(RED)Python not found. Please install Python or set PYTHON environment variable$(RESET)" && exit 1); \
		$(PYTHON) -c "import black" 2>/dev/null || (echo "$(RED)black is not installed. Run 'make install-tools-python'$(RESET)" && exit 1); \
		$(PYTHON) -c "import isort" 2>/dev/null || (echo "$(RED)isort is not installed. Run 'make install-tools-python'$(RESET)" && exit 1); \
		$(PYTHON) -c "import flake8" 2>/dev/null || (echo "$(RED)flake8 is not installed. Run 'make install-tools-python'$(RESET)" && exit 1); \
		$(PYTHON) -c "import mypy" 2>/dev/null || (echo "$(RED)mypy is not installed. Run 'make install-tools-python'$(RESET)" && exit 1); \
		$(PYTHON) -c "import pylint" 2>/dev/null || (echo "$(RED)pylint is not installed. Run 'make install-tools-python'$(RESET)" && exit 1); \
		echo "$(GREEN)Python tools available$(RESET)"; \
		echo "  Python files: $(words $(PYTHON_FILES))"; \
		echo "  Python version: $$($(PYTHON) --version)"; \
		echo "  Pip version: $$($(PYTHON) -m pip --version)"; \
	fi

fmt-python: ## ✨ Format Python code
	@if [ -n "$(PYTHON_DIRS)" ]; then \
		echo "$(YELLOW)Formatting Python code in: $(PYTHON_DIRS)$(RESET)"; \
		for dir in $(PYTHON_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Processing $$dir...$(RESET)"; \
				cd $$dir && \
				$(PYTHON) -m $(ISORT) . && \
				$(PYTHON) -m $(BLACK) .; \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)Python code formatting completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Python formatting (no Python projects configured)$(RESET)"; \
	fi

check-python: ## 🔍 Check Python code quality
	@if [ -n "$(PYTHON_DIRS)" ]; then \
		echo "$(YELLOW)Checking Python code quality in: $(PYTHON_DIRS)$(RESET)"; \
		FAILED_PROJECTS=""; \
		for dir in $(PYTHON_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Processing $$dir...$(RESET)"; \
				if (cd $$dir && \
				echo "$(YELLOW)    1. Running flake8 code style check...$(RESET)" && \
				$(PYTHON) -m $(FLAKE8) . && \
				echo "$(YELLOW)    2. Running isort import formatting...$(RESET)" && \
				$(PYTHON) -m $(ISORT) . && \
				echo "$(YELLOW)    3. Running black code formatting...$(RESET)" && \
				$(PYTHON) -m $(BLACK) . && \
				echo "$(YELLOW)    4. Running mypy type checking...$(RESET)" && \
				$(PYTHON) -m $(MYPY) . && \
				echo "$(YELLOW)    5. Running pylint code analysis...$(RESET)" && \
				$(PYTHON) -m $(PYLINT) --max-line-length=88 --max-args=7 --max-locals=15 --max-returns=6 --max-branches=12 --max-statements=50 *.py); then \
					echo "$(GREEN)    ✅ $$dir passed all checks$(RESET)"; \
				else \
					echo "$(RED)    ❌ $$dir failed quality checks$(RESET)"; \
					FAILED_PROJECTS="$$FAILED_PROJECTS $$dir"; \
				fi; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
				FAILED_PROJECTS="$$FAILED_PROJECTS $$dir(missing)"; \
			fi; \
		done; \
		if [ -n "$$FAILED_PROJECTS" ]; then \
			echo "$(RED)Python code quality checks failed for:$$FAILED_PROJECTS$(RESET)"; \
			echo "$(YELLOW)Please fix the issues above and run 'make check-python' again$(RESET)"; \
			exit 1; \
		else \
			echo "$(GREEN)All Python projects passed code quality checks$(RESET)"; \
		fi; \
	else \
		echo "$(BLUE)Skipping Python checks (no Python projects configured)$(RESET)"; \
	fi

test-python: ## 🧪 Run Python tests
	@if [ -n "$(PYTHON_DIRS)" ]; then \
		echo "$(YELLOW)Running Python tests in: $(PYTHON_DIRS)$(RESET)"; \
		for dir in $(PYTHON_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Testing $$dir...$(RESET)"; \
				(cd $$dir && \
				if [ -d "tests" ]; then \
					echo "$(YELLOW)    Running tests...$(RESET)" && \
					if [ -f "uv.lock" ]; then \
						echo "$(YELLOW)      Syncing dependencies with uv...$(RESET)" && \
						uv sync && \
						uv run python -m pytest tests/ -v; \
					else \
						$(PYTHON) -m pytest tests/ -v; \
					fi; \
				else \
					echo "$(BLUE)    No tests directory found$(RESET)"; \
				fi) || exit 1; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)Python tests completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Python tests (no Python projects configured)$(RESET)"; \
	fi

build-python: ## 📦 Build Python projects (install dependencies)
	@if [ -n "$(PYTHON_DIRS)" ]; then \
		echo "$(YELLOW)Building Python projects in: $(PYTHON_DIRS)$(RESET)"; \
		for dir in $(PYTHON_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Building $$dir...$(RESET)"; \
				cd $$dir && \
				if [ -f uv.lock ]; then \
					echo "$(YELLOW)    Installing dependencies with uv...$(RESET)"; \
					uv sync; \
					echo "$(GREEN)  Dependencies installed with uv: $$dir$(RESET)"; \
				elif [ -f requirements.txt ]; then \
					echo "$(YELLOW)    Installing dependencies with pip...$(RESET)"; \
					$(PYTHON) -m pip install -r requirements.txt; \
					echo "$(GREEN)  Dependencies installed with pip: $$dir$(RESET)"; \
				else \
					echo "$(BLUE)  No uv.lock or requirements.txt found in $$dir$(RESET)"; \
				fi; \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)Python build completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Python build (no Python projects configured)$(RESET)"; \
	fi

clean-python: ## 🧹 Clean Python build artifacts
	@if [ -n "$(PYTHON_DIRS)" ]; then \
		echo "$(YELLOW)Cleaning Python build artifacts in: $(PYTHON_DIRS)$(RESET)"; \
		for dir in $(PYTHON_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Cleaning $$dir...$(RESET)"; \
				cd $$dir && \
				find . -type d -name "__pycache__" -exec rm -rf {} \; 2>/dev/null || true; \
				find . -type d -name "*.egg-info" -exec rm -rf {} \; 2>/dev/null || true; \
				find . -type f -name "*.pyc" -delete 2>/dev/null || true; \
				find . -type f -name "*.pyo" -delete 2>/dev/null || true; \
				rm -rf .pytest_cache/ .mypy_cache/ coverage_html/ .coverage 2>/dev/null || true; \
				echo "$(GREEN)  Cleaned: $$dir$(RESET)"; \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)Python build artifacts cleaned$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Python clean (no Python projects configured)$(RESET)"; \
	fi