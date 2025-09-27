#!/usr/bin/env python3
"""Test runner script.

This script is used to run various test suites for the project.
"""

import os
import subprocess
import sys
from pathlib import Path


def run_command(command: str, description: str) -> bool:
    """Run command and return result."""
    print(f"\n{'='*50}")
    print(f"Running: {description}")
    print(f"Command: {command}")
    print("=" * 50)

    try:
        result = subprocess.run(
            command, shell=True, check=True, capture_output=True, text=True
        )
        print("✅ Success")
        if result.stdout:
            print("Output:")
            print(result.stdout)
        return True
    except subprocess.CalledProcessError as e:
        print("❌ Failed")
        if e.stdout:
            print("Standard output:")
            print(e.stdout)
        if e.stderr:
            print("Error output:")
            print(e.stderr)
        return False


def main() -> int:
    """Main function."""
    print("🚀 Starting RPA server test suite")

    # Switch to project directory
    project_dir = Path(__file__).parent
    os.chdir(project_dir)

    test_commands = [
        ("python -m pytest tests/api/test_schemas.py -v", "API Schemas test"),
        ("python -m pytest tests/errors/test_error_code.py -v", "Error code test"),
        (
            "python -m pytest tests/exceptions/test_config_exceptions.py -v",
            "Exception test",
        ),
        ("python -m pytest tests/consts/test_const.py -v", "Constants test"),
        ("python -m pytest tests/utils/test_utl_util.py -v", "Utility functions test"),
        ("python -m pytest tests/api/test_router.py -v", "Router test"),
    ]

    passed = 0
    failed = 0

    for command, description in test_commands:
        if run_command(command, description):
            passed += 1
        else:
            failed += 1

    print(f"\n{'='*50}")
    print("🎯 Test Summary")
    print(f"{'='*50}")
    print(f"✅ Passed: {passed}")
    print(f"❌ Failed: {failed}")
    print(f"📊 Total: {passed + failed}")

    if failed == 0:
        print("\n🎉 All tests passed!")
        return 0

    print(f"\n⚠️  {failed} test suites failed")
    return 1


if __name__ == "__main__":
    sys.exit(main())
