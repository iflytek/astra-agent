#!/usr/bin/env python3
"""
Agent main entry point
Load configuration files and start FastAPI service
"""

import os
import subprocess
import sys
from pathlib import Path


def load_env_file(env_file: str) -> None:
    """Load environment variables from .env file"""
    if not os.path.exists(env_file):
        print(f"❌ Configuration file {env_file} does not exist")
        return

    print(f"📋 Loading configuration file: {env_file}")

    with open(env_file, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()

            # Skip empty lines and comments
            if not line or line.startswith("#"):
                continue

            # Parse environment variables
            if "=" in line:
                key, value = line.split("=", 1)
                key = key.strip()
                value = value.strip()

                if key in os.environ:
                    print(f"  🔄 {key}={os.environ[key]} (using existing env var)")
                else:
                    os.environ[key] = value
                    print(f"  ✅ {key}={value} (loaded from config)")
            else:
                print(f"  ⚠️  Line {line_num} format error: {line}")


def setup_python_path() -> None:
    """Set up Python path"""
    project_root = Path(__file__).parent
    python_path = os.environ.get("PYTHONPATH", "")

    if str(project_root) not in python_path:
        if python_path:
            os.environ["PYTHONPATH"] = f"{project_root}:{python_path}"
        else:
            os.environ["PYTHONPATH"] = str(project_root)
        print(f"🔧 PYTHONPATH: {os.environ['PYTHONPATH']}")


def start_service() -> None:
    """Start FastAPI service"""
    print("\n🚀 Starting Agent service...")

    # Display key environment variables
    env_vars = [
        "PYTHONUNBUFFERED",
        "polaris_cluster",
        "polaris_url",
        "polaris_username",
        "RUN_ENVIRON",
        "USE_POLARIS",
    ]

    print("📋 Environment configuration:")
    for var in env_vars:
        value = os.environ.get(var, "None")
        # Hide passwords
        if "password" in var.lower():
            value = "***"
        print(f"  - {var}: {value}")

    print("")

    try:
        # Start FastAPI application
        subprocess.run([sys.executable, "api/app.py"], check=True)
    except subprocess.CalledProcessError as e:
        print(f"❌ Service startup failed: {e}")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n🛑 Service stopped")
        sys.exit(0)


def main() -> None:
    """Main function"""
    print("🌟 Agent Development Environment Launcher")
    print("=" * 50)

    # Set up Python path
    setup_python_path()

    # Load environment configuration
    config_file = Path(__file__).parent / "config.env"
    load_env_file(str(config_file))

    # Start service
    start_service()


if __name__ == "__main__":
    main()
