# =============================================================================
# Java Language Support - Makefile Module
# =============================================================================

# Java project variables - use dynamic directories from config
MVN := mvn
JAVA_DIRS := $(shell \
	if [ -n "$(LOCALCI_CONFIG)" ] && [ -f "$(LOCALCI_CONFIG)" ]; then \
		makefiles/parse_localci.sh enabled java $(LOCALCI_CONFIG) | cut -d'|' -f2 | tr '\n' ' '; \
	else \
		echo "demo-apps/backends/java"; \
	fi)

JAVA_PRIMARY_DIR := $(shell echo $(JAVA_DIRS) | cut -d' ' -f1)
JAVA_DIR := $(JAVA_PRIMARY_DIR)
JAVA_FILES := $(shell \
	for dir in $(JAVA_DIRS); do \
		if [ -d "$$dir" ]; then \
			find $$dir -name "*.java" 2>/dev/null || true; \
		fi; \
	done)

# Maven options
MAVEN_OPTS := -Dmaven.test.failure.ignore=false
MAVEN_SKIP_TESTS := -DskipTests
MAVEN_DEFAULT :=
# Use MAVEN_VERBOSE=1 to see full Maven output
ifneq ($(MAVEN_VERBOSE),1)
MAVEN_DEFAULT_OPTS := $(MAVEN_DEFAULT)
else
MAVEN_DEFAULT_OPTS := $(MAVEN_DEFAULT)
endif

# =============================================================================
# Core Java Commands
# =============================================================================

install-tools-java: ## 🛠️ Install Java development tools
	@if [ -d "$(JAVA_DIR)" ]; then \
		echo "$(YELLOW)Installing Java tools...$(RESET)"; \
		echo "$(GREEN)Java tools ready (using Maven plugins)$(RESET)"; \
		echo "  - Spotless: Code formatting with Google Java Format"; \
		echo "  - Checkstyle: Code style verification"; \
		echo "  - SpotBugs: Static analysis for bug detection"; \
		echo "  - PMD: Code quality analyzer"; \
	else \
		echo "$(BLUE)Skipping Java tools (no Java project detected)$(RESET)"; \
	fi

check-tools-java: ## ✅ Check Java development tools availability
	@if [ -d "$(JAVA_DIR)" ]; then \
		echo "$(YELLOW)Checking Java tools...$(RESET)"; \
		command -v java >/dev/null 2>&1 || (echo "$(RED)Java is not installed$(RESET)" && exit 1); \
		command -v $(MVN) >/dev/null 2>&1 || (echo "$(RED)Maven is not installed$(RESET)" && exit 1); \
		echo "$(GREEN)Java tools available$(RESET)"; \
		echo "  Java files: $(words $(JAVA_FILES))"; \
		echo "  Java version: $$(java -version 2>&1 | head -n 1)"; \
		echo "  Maven version: $$(mvn --version | head -n 1)"; \
	fi

fmt-java: ## ✨ Format Java code
	@if [ -n "$(JAVA_DIRS)" ]; then \
		echo "$(YELLOW)Formatting Java code in: $(JAVA_DIRS)$(RESET)"; \
		for dir in $(JAVA_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Processing $$dir...$(RESET)"; \
				cd $$dir && $(MVN) spotless:apply $(MAVEN_DEFAULT_OPTS); \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)Java code formatting completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Java formatting (no Java projects configured)$(RESET)"; \
	fi
	@echo "$(GREEN)Console backend code formatting checks passed$(RESET)"

check-java: ## 🔍 Check Java code quality
	@if [ -n "$(JAVA_DIRS)" ]; then \
		echo "$(YELLOW)Checking Java code quality in: $(JAVA_DIRS)$(RESET)"; \
		for dir in $(JAVA_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Processing $$dir...$(RESET)"; \
				(cd $$dir && \
				echo "$(YELLOW)    Compiling project...$(RESET)" && \
				$(MVN) clean compile $(MAVEN_DEFAULT_OPTS) && \
				echo "$(YELLOW)    Running Spotless format check...$(RESET)" && \
				$(MVN) spotless:check $(MAVEN_DEFAULT_OPTS) && \
				echo "$(YELLOW)    Running Checkstyle...$(RESET)" && \
				$(MVN) checkstyle:check $(MAVEN_DEFAULT_OPTS) && \
				echo "$(YELLOW)    Running SpotBugs...$(RESET)" && \
				$(MVN) clean compile spotbugs:check $(MAVEN_DEFAULT_OPTS) && \
				echo "$(YELLOW)    Running PMD...$(RESET)" && \
				$(MVN) clean compile pmd:check $(MAVEN_DEFAULT_OPTS)) || exit 1; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
				exit 1; \
			fi; \
		done; \
		echo "$(GREEN)Java code quality checks completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Java checks (no Java projects configured)$(RESET)"; \
	fi

test-java: ## 🧪 Run Java tests
	@if [ -n "$(JAVA_DIRS)" ]; then \
		echo "$(YELLOW)Running Java tests in: $(JAVA_DIRS)$(RESET)"; \
		for dir in $(JAVA_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Testing $$dir...$(RESET)"; \
				cd $$dir && $(MVN) test $(MAVEN_DEFAULT_OPTS); \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)Java tests completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Java tests (no Java projects configured)$(RESET)"; \
	fi

build-java: ## 📦 Build Java projects
	@if [ -n "$(JAVA_DIRS)" ]; then \
		echo "$(YELLOW)Building Java projects in: $(JAVA_DIRS)$(RESET)"; \
		for dir in $(JAVA_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Building $$dir...$(RESET)"; \
				cd $$dir && $(MVN) clean package $(MAVEN_SKIP_TESTS) $(MAVEN_DEFAULT_OPTS); \
				echo "$(GREEN)  Java project built successfully: $$dir$(RESET)"; \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)Java build completed$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Java build (no Java projects configured)$(RESET)"; \
	fi

clean-java: ## 🧹 Clean Java build artifacts
	@if [ -n "$(JAVA_DIRS)" ]; then \
		echo "$(YELLOW)Cleaning Java build artifacts in: $(JAVA_DIRS)$(RESET)"; \
		for dir in $(JAVA_DIRS); do \
			if [ -d "$$dir" ]; then \
				echo "$(YELLOW)  Cleaning $$dir...$(RESET)"; \
				cd $$dir && $(MVN) clean $(MAVEN_DEFAULT_OPTS); \
				cd - > /dev/null; \
			else \
				echo "$(RED)    Directory $$dir does not exist$(RESET)"; \
			fi; \
		done; \
		echo "$(GREEN)Java build artifacts cleaned$(RESET)"; \
	else \
		echo "$(BLUE)Skipping Java clean (no Java projects configured)$(RESET)"; \
	fi
