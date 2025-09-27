# =============================================================================
# TypeScript Language Support - Makefile Module
# =============================================================================

# TypeScript project variables - use dynamic directories from config
NPM := npm
PRETTIER := prettier
ESLINT := eslint
TSC := typescript

# Get all TypeScript directories from config
TS_DIRS := $(shell \
	if [ -n "$(LOCALCI_CONFIG)" ] && [ -f "$(LOCALCI_CONFIG)" ]; then \
		makefiles/parse_localci.sh enabled typescript $(LOCALCI_CONFIG) | cut -d'|' -f2 | tr '\n' ' '; \
	else \
		echo "console/frontend"; \
	fi)

TS_PRIMARY_DIR := $(shell echo $(TS_DIRS) | cut -d' ' -f1)
TS_DIR := $(TS_PRIMARY_DIR)
TS_FILES := $(shell \
	for dir in $(TS_DIRS); do \
		if [ -d "$$dir" ]; then \
			find $$dir -name "*.ts" -o -name "*.tsx" -o -name "*.js" -o -name "*.jsx" 2>/dev/null || true; \
		fi; \
	done)

# =============================================================================
# Core TypeScript Commands
# =============================================================================

install-tools-typescript: ## 🛠️ Install TypeScript development tools
	@echo "$(YELLOW)Installing TypeScript tools globally...$(RESET)"
	@npm install -g \
		typescript@latest \
		prettier@latest \
		eslint@latest \
		@typescript-eslint/parser@latest \
		@typescript-eslint/eslint-plugin@latest \
		eslint-config-prettier@latest \
		eslint-plugin-import@latest \
		eslint-plugin-prettier@latest && \
	echo "$(GREEN)TypeScript tools installed globally$(RESET)" || \
	(echo "$(RED)Failed to install TypeScript tools$(RESET)" && exit 1)

check-tools-typescript: ## ✅ Check TypeScript development tools availability
	@echo "$(YELLOW)Checking TypeScript tools...$(RESET)"
	@command -v node >/dev/null 2>&1 || (echo "$(RED)Node.js is not installed$(RESET)" && exit 1)
	@command -v npm >/dev/null 2>&1 || (echo "$(RED)npm is not installed$(RESET)" && exit 1)
	@command -v tsc >/dev/null 2>&1 || (echo "$(RED)TypeScript is not installed globally. Run 'make install-tools-typescript'$(RESET)" && exit 1)
	@command -v prettier >/dev/null 2>&1 || (echo "$(RED)Prettier is not installed globally. Run 'make install-tools-typescript'$(RESET)" && exit 1)
	@command -v eslint >/dev/null 2>&1 || (echo "$(RED)ESLint is not installed globally. Run 'make install-tools-typescript'$(RESET)" && exit 1)
	@echo "$(GREEN)TypeScript tools available globally$(RESET)"
	@echo "  TypeScript files: $(words $(TS_FILES))"
	@echo "  Node version: $$(node --version)"
	@echo "  NPM version: $$(npm --version)"
	@echo "  TypeScript version: $$(tsc --version)"
	@echo "  Prettier version: $$(prettier --version)"
	@echo "  ESLint version: $$(eslint --version)"

fmt-typescript: ## ✨ Format TypeScript code
	@if [ -n "$(TS_DIRS)" ]; then \
		echo "$(YELLOW)Formatting TypeScript code in: $(TS_DIRS)$(RESET)"; \
		for dir in $(TS_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Processing $$dir...$(RESET)"; \
				cd $$dir && prettier --write "**/*.{ts,tsx,js,jsx,json,md}"; \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)TypeScript code formatting completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping TypeScript formatting (no TypeScript projects configured)$(RESET)"; \
	fi

check-typescript: ## 🔍 Check TypeScript code quality
	@if [ -n "$(TS_DIRS)" ]; then \
		echo "$(YELLOW)Checking TypeScript code quality in: $(TS_DIRS)$(RESET)"; \
		for dir in $(TS_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Processing $$dir...$(RESET)"; \
				(cd $$dir && \
				echo "$(YELLOW)    Checking format compliance...$(RESET)" && \
				UNFORMATTED=$(npx prettier --list-different "**/*.{ts,tsx,js,jsx,json,md}" 2>/dev/null || true) && \
				if [ -n "$$UNFORMATTED" ]; then \
					echo "Files that need formatting:" && \
					echo "$$UNFORMATTED" && \
					echo "Run 'npm run format' to fix formatting issues." && \
					exit 1; \
				fi && \
				echo "$(YELLOW)    Running TypeScript type checking...$(RESET)" && \
				npx tsc --noEmit --pretty && \
				echo "$(YELLOW)    Running ESLint...$(RESET)" && \
				npx eslint "**/*.{ts,tsx}" --format=stylish --max-warnings=0) || exit 1; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
				exit 1; \
			fi; \
		done; \
		echo "$(GREEN)TypeScript code quality checks completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping TypeScript checks (no TypeScript projects configured)$(RESET)"; \
	fi

test-typescript: ## 🧪 Run TypeScript tests
	@if [ -n "$(TS_DIRS)" ]; then \
		echo "$(YELLOW)Running TypeScript tests in: $(TS_DIRS)$(RESET)"; \
		for dir in $(TS_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Testing $$dir...$(RESET)"; \
				cd $$dir; \
				if [ -f package.json ] && grep -q '"test"' package.json; then \
					if grep -q '"test":.*vite.*--host' package.json || grep -q '"test":.*dev' package.json; then \
						echo "$(BLUE)    Test script appears to be dev server, skipping$(RESET)"; \
					else \
						$(NPM) test; \
					fi; \
				else \
					echo "$(BLUE)    No test script found in package.json$(RESET)"; \
				fi; \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)TypeScript tests completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping TypeScript tests (no TypeScript projects configured)$(RESET)"; \
	fi

build-typescript: ## 📦 Build TypeScript projects
	@if [ -n "$(TS_DIRS)" ]; then \
		echo "$(YELLOW)Building TypeScript projects in: $(TS_DIRS)$(RESET)"; \
		for dir in $(TS_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Building $$dir...$(RESET)"; \
				cd $$dir; \
				if [ -f package.json ]; then \
					$(NPM) ci --prefer-offline; \
					if grep -q '"build"' package.json; then \
						$(NPM) run build; \
						echo "$(GREEN)  Built: $$dir$(RESET)"; \
					else \
						echo "$(BLUE)  No build script found in $$dir/package.json$(RESET)"; \
					fi; \
				else \
					echo "$(RED)  No package.json found in $$dir$(RESET)"; \
				fi; \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)TypeScript build completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping TypeScript build (no TypeScript projects configured)$(RESET)"; \
	fi

clean-typescript: ## 🧹 Clean TypeScript build artifacts
	@if [ -n "$(TS_DIRS)" ]; then \
		echo "$(YELLOW)Cleaning TypeScript build artifacts in: $(TS_DIRS)$(RESET)"; \
		for dir in $(TS_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Cleaning $$dir...$(RESET)"; \
				cd $$dir && \
				rm -rf dist/ build/ .next/ out/ coverage/ && \
				rm -rf node_modules/.cache/ .eslintcache .tsbuildinfo && \
				echo "$(GREEN)  Cleaned: $$dir$(RESET)"; \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)TypeScript build artifacts cleaned$(RESET)"; \
	else \
		echo "$(BLUE)Skipping TypeScript clean (no TypeScript projects configured)$(RESET)"; \
	fi