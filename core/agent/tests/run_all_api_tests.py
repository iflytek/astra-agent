"""
Complete API Test Suite

Run tests for all API interfaces and generate unified test reports:
1. Chat Completions API
2. Custom Chat Completions API
3. Bot Config Management API

Use fixed test parameters to ensure consistency and reproducibility.
"""

import json
import subprocess
import sys
import time
from datetime import datetime
from typing import Any, Dict, Tuple


class APITestRunner:
    """APItest运行器"""

    def __init__(self) -> None:
        self.test_results: Dict[str, Dict[str, Any]] = {}
        self.start_time = datetime.now()

    def run_test_file(self, test_file: str, test_name: str) -> Dict[str, Any]:
        """运行单个test文件并返回结果"""
        print(f"\n🚀 运行{test_name}test...")
        print("=" * 60)

        start_time = time.time()

        try:
            # run test file
            result = subprocess.run(
                [sys.executable, test_file],
                capture_output=True,
                text=True,
                timeout=300,  # 5 minute timeout
                check=False,  # do not auto-check return code
            )

            end_time = time.time()
            execution_time = end_time - start_time

            # parse results
            success = result.returncode == 0
            output = result.stdout
            errors = result.stderr

            # extract statistics from output
            passed, failed, total = self.parse_test_statistics(output)

            test_result = {
                "success": success,
                "execution_time": execution_time,
                "passed": passed,
                "failed": failed,
                "total": total,
                "output": output,
                "errors": errors,
            }

            print(f"✅ {test_name}test completed - 耗时: {execution_time:.2f}s")
            print(f"📊 结果: passed {passed}, failed {failed}, total {total}")

            if not success:
                print("❌ testexecutefailed:")
                print(errors)

            return test_result

        except subprocess.TimeoutExpired:
            print(f"⏰ {test_name}test超时")
            return {
                "success": False,
                "execution_time": 300,
                "passed": 0,
                "failed": 1,
                "total": 1,
                "output": "",
                "errors": "testexecute超时",
            }
        except (OSError, ValueError, RuntimeError) as e:
            print(f"❌ 运行{test_name}test时发生异常: {e}")
            return {
                "success": False,
                "execution_time": 0,
                "passed": 0,
                "failed": 1,
                "total": 1,
                "output": "",
                "errors": str(e),
            }

    def parse_test_statistics(self, output: str) -> Tuple[int, int, int]:
        """从test输出中解析统计信息"""
        passed, failed, total = 0, 0, 0

        # find test completion statistics
        lines = output.split("\n")
        for line in lines:
            if "test completed！passed:" in line:
                try:
                    # parse lines like "test completed! passed: 12, failed: 0, total: 12"
                    parts = line.split("passed: ")[1]
                    passed = int(parts.split(",")[0].strip())

                    failed_part = parts.split("failed: ")[1]
                    failed = int(failed_part.split(",")[0].strip())

                    total_part = parts.split("total: ")[1]
                    total = int(total_part.strip())

                    break
                except (IndexError, ValueError):
                    continue

        return passed, failed, total

    def run_all_tests(self) -> Dict[str, Any]:
        """运行所有APItest"""
        print("🧪 开始运行完整APItest套件...")
        print(f"⏰ 开始time: {self.start_time.strftime('%Y-%m-%d %H:%M:%S')}")
        print("=" * 80)

        # define tests to run
        tests = [
            ("tests/integration/test_chat_completions_v2.py", "Chat Completions"),
            (
                "tests/integration/test_custom_chat_completions.py",
                "Custom Chat Completions",
            ),
            (
                "tests/integration/test_bot_config_management.py",
                "Bot Config Management",
            ),
        ]

        # run each test
        for test_file, test_name in tests:
            self.test_results[test_name] = self.run_test_file(test_file, test_name)

        # generate summary report
        return self.generate_summary_report()

    def generate_summary_report(self) -> Dict[str, Any]:
        """生成汇总test报告"""
        end_time = datetime.now()
        total_execution_time = (end_time - self.start_time).total_seconds()

        # calculate overall statistics
        total_passed = sum(result["passed"] for result in self.test_results.values())
        total_failed = sum(result["failed"] for result in self.test_results.values())
        total_tests = sum(result["total"] for result in self.test_results.values())

        successful_suites = sum(
            1 for result in self.test_results.values() if result["success"]
        )
        total_suites = len(self.test_results)

        success_rate = (total_passed / total_tests * 100) if total_tests > 0 else 0

        summary = {
            "start_time": self.start_time.isoformat(),
            "end_time": end_time.isoformat(),
            "total_execution_time": total_execution_time,
            "total_suites": total_suites,
            "successful_suites": successful_suites,
            "total_tests": total_tests,
            "total_passed": total_passed,
            "total_failed": total_failed,
            "success_rate": success_rate,
            "suite_results": self.test_results,
        }

        return summary

    def print_final_report(self, summary: Dict[str, Any]) -> None:
        """打印最终test报告"""
        print("\n" + "=" * 80)
        print("🏁 APItest套件execute完成")
        print("=" * 80)

        print(f"⏰ 总executetime: {summary['total_execution_time']:.2f}秒")
        print(
            f"📦 test套件: {summary['successful_suites']}/"
            f"{summary['total_suites']} 成功"
        )
        print(f"🧪 test用例: {summary['total_passed']}/{summary['total_tests']} passed")
        print(f"📊 成功率: {summary['success_rate']:.1f}%")

        print("\n📋 详细结果:")
        for suite_name, result in summary["suite_results"].items():
            status = "✅" if result["success"] else "❌"
            print(
                f"  {status} {suite_name}: {result['passed']}/{result['total']} "
                f"passed (耗时: {result['execution_time']:.1f}s)"
            )

        if summary["total_failed"] > 0:
            print(f"\n⚠️ 发现 {summary['total_failed']} 个failed的test用例")
            print("详细错误信息请查看上方的test输出。")
        else:
            print(f"\n🎉 所有 {summary['total_tests']} 个test用例都passed了！")

        print("\n💡 test配置信息:")
        print("  - x-consumer-username: cb7386a7")
        print("  - bot_id: 14a9bbbcf0254f9b94562e6705d3a13f")
        print("  - uid: 12")
        print("  - 服务地址: http://localhost:17870")

        print("=" * 80)

    def save_detailed_report(
        self, summary: Dict[str, Any], filename: str = "api_test_report.json"
    ) -> None:
        """保存详细的JSON格式test报告"""
        report_path = f"tests/{filename}"

        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)

        print(f"📄 详细test报告已保存至: {report_path}")


def main() -> None:
    """主函数"""
    runner = APITestRunner()

    try:
        # run all tests
        summary = runner.run_all_tests()

        # print final report
        runner.print_final_report(summary)

        # save detailed report
        runner.save_detailed_report(summary)

        # set exit code based on test results
        if summary["total_failed"] == 0:
            print("\n🎊 所有APItest成功完成！")
            sys.exit(0)
        else:
            print(f"\n⚠️ 有 {summary['total_failed']} 个test failed")
            sys.exit(1)

    except KeyboardInterrupt:
        print("\n\n⏹️ 用户中断了testexecute")
        sys.exit(130)
    except (RuntimeError, ValueError, OSError) as e:
        print(f"\n❌ test运行器发生异常: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
