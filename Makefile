# =============================================================================
# Multi-language CI/CD Toolchain - Optimized Main Makefile (Only 15 Core Commands)
# Streamlined from 95 commands to 15 core commands, providing intelligent project detection and automated workflows
# =============================================================================

# Include core modules
include makefiles/core/detection.mk
include makefiles/core/workflows.mk

# Include original language modules (for internal calls)
include makefiles/go.mk
include makefiles/typescript.mk
include makefiles/java.mk
include makefiles/python.mk
include makefiles/git.mk
include makefiles/common.mk
include makefiles/comment-check.mk

# =============================================================================
# Core command declarations
# =============================================================================
.PHONY: help setup format check test build push clean status info lint fix ci hooks enable-legacy

# =============================================================================
# Tier 1: Daily Core Commands (8) - These are all you need to remember!
# =============================================================================

# Default target - Intelligent help
.DEFAULT_GOAL := help
help: ## 📚 Show help information and project status  
	@echo "$(BLUE)🚀 Multi-language CI/CD Toolchain - Intelligent Version$(RESET)"
	@echo "$(YELLOW)Active Projects:$(RESET) $(GREEN)$(ACTIVE_PROJECTS)$(RESET) | $(YELLOW)Current Context:$(RESET) $(GREEN)$(CURRENT_CONTEXT)$(RESET)"
	@echo ""
	@echo "$(BLUE)📋 Core Commands (Daily Development):$(RESET)"
	@echo "  $(GREEN)make setup$(RESET)     🛠️  One-time environment setup (tools+hooks+branch strategy)"
	@echo "  $(GREEN)make format$(RESET)    ✨  Format code (intelligent detection: $(ACTIVE_PROJECTS))"
	@echo "  $(GREEN)make check$(RESET)     🔍  Quality check (intelligent detection: $(ACTIVE_PROJECTS))"  
	@echo "  $(GREEN)make test$(RESET)      🧪  Run tests (intelligent detection: $(ACTIVE_PROJECTS))"
	@echo "  $(GREEN)make build$(RESET)     📦  Build projects (intelligent detection: $(ACTIVE_PROJECTS))"
	@echo "  $(GREEN)make push$(RESET)      📤  Safe push to remote (with pre-checks)"
	@echo "  $(GREEN)make clean$(RESET)     🧹  Clean build artifacts"
	@echo ""
	@echo "$(BLUE)🔧 Professional Commands:$(RESET)"
	@echo "  $(GREEN)make status$(RESET)    📊  Show detailed project status"
	@echo "  $(GREEN)make info$(RESET)      ℹ️   Show tools and dependency information"  
	@echo "  $(GREEN)make lint$(RESET)      🔧  Run code linting (alias for check)"
	@echo "  $(GREEN)make fix$(RESET)       🛠️  Auto-fix code issues"
	@echo "  $(GREEN)make ci$(RESET)        🤖  Complete CI pipeline (format+check+test+build)"
	@echo ""
	@echo "$(BLUE)⚙️ Advanced Commands:$(RESET)"
	@echo "  $(GREEN)make hooks$(RESET)     ⚙️  Git hooks management menu"
	@echo "  $(GREEN)make enable-legacy$(RESET) 🔄  Enable complete legacy command set (backward compatibility)"
	@echo ""
	@if [ "$(IS_MULTI_PROJECT)" = "true" ]; then \
		echo "$(YELLOW)💡 Multi-project environment detected, all commands will intelligently handle multiple projects$(RESET)"; \
	else \
		echo "$(YELLOW)💡 Single project environment, please run common commands in corresponding subdirectories (setup/format/check/test/build)$(RESET)"; \
	fi

# Core workflow commands - Direct calls to intelligent implementations
setup: smart_setup ## 🛠️ One-time environment setup (tools+hooks+branch strategy)

format: smart_format ## ✨ Intelligent code formatting (detect active projects)

check: smart_check ## 🔍 Intelligent code quality check (detect active projects)  

test: smart_test ## 🧪 Intelligent test execution (detect active projects)

build: smart_build ## 📦 Intelligent project build (detect active projects)

push: smart_push ## 📤 Intelligent safe push (branch check + quality check)

clean: smart_clean ## 🧹 Intelligent cleanup of build artifacts

# =============================================================================  
# Tier 2: Professional Commands (5)
# =============================================================================

status: smart_status ## 📊 Show detailed project status

info: smart_info ## ℹ️ Show tools and dependency information  

lint: smart_check ## 🔧 Run code linting (alias for check)

fix: smart_fix ## 🛠️ Auto-fix code issues (format + partial lint fixes)

ci: smart_ci ## 🤖 Complete CI pipeline (format + check + test + build)

# =============================================================================
# Tier 3: Advanced Commands (2) 
# =============================================================================

hooks: ## ⚙️ Git hooks management menu
	@echo "$(BLUE)⚙️ Git Hooks Management$(RESET)"
	@echo ""
	@echo "$(GREEN)Install Hooks:$(RESET)"
	@echo "  make hooks-install       📌 Install all hooks (recommended)"
	@echo "  make hooks-install-basic 📋 Install basic hooks (lightweight)"
	@echo "  make hooks-fmt           ✨ Format hooks only"
	@echo "  make hooks-commit-msg    💬 Commit message hooks only"
	@echo ""
	@echo "$(RED)Uninstall Hooks:$(RESET)"
	@echo "  make hooks-uninstall     ❌ Uninstall all hooks"
	@echo ""
	@echo "$(YELLOW)Current Hook Status:$(RESET)"
	@ls -la .git/hooks/ | grep -E "(pre-commit|commit-msg|pre-push)" | head -3

enable-legacy: ## 🔄 Enable complete legacy command set (backward compatibility)
	@echo "$(YELLOW)🔄 Enabling legacy command set...$(RESET)"
	@if [ ! -f "makefiles/legacy/enabled" ]; then \
		echo "# Legacy commands enabled" > makefiles/legacy/enabled; \
		echo "$(GREEN)✅ Legacy command set enabled$(RESET)"; \
		echo ""; \
		echo "$(BLUE)You can now use all original commands, for example:$(RESET)"; \
		echo "  make fmt-go fmt-java fmt-python fmt-typescript"; \
		echo "  make check-go check-java check-python check-typescript"; \
		echo "  make install-tools-go install-tools-java ..."; \
		echo ""; \
		echo "$(YELLOW)Note: Recommended to prioritize new intelligent commands for better experience$(RESET)"; \
	else \
		echo "$(GREEN)✅ Legacy command set already enabled$(RESET)"; \
	fi

# =============================================================================
# Backward compatibility: Conditional inclusion of legacy commands
# =============================================================================
-include makefiles/legacy/enabled
ifneq (,$(wildcard makefiles/legacy/enabled))
    # If legacy mode is enabled, additional legacy command definitions can be included here
    # In current version, legacy commands are directly available through original module files
endif

# =============================================================================
# Hidden utility commands (for debugging and testing)
# =============================================================================
_debug: ## 🔍 [Debug] Test project detection and Makefile status
	@echo "$(YELLOW)Project Detection Test:$(RESET)"
	@echo "ACTIVE_PROJECTS: '$(ACTIVE_PROJECTS)'"
	@echo "CURRENT_CONTEXT: '$(CURRENT_CONTEXT)'"
	@echo "PROJECT_COUNT: $(PROJECT_COUNT)"
	@echo "IS_MULTI_PROJECT: $(IS_MULTI_PROJECT)"
	$(call show_project_status)
	@echo ""
	@echo "$(BLUE)Current Makefile Status:$(RESET)"
	@echo "Included modules: detection.mk workflows.mk + original language modules"
