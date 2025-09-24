"""
AI Tools service main entry module
"""

from plugin.aitools.app.start_server import AIToolsServer

if __name__ == "__main__":
    import os

    # dev环境配置
    os.environ["PolarisUsername"] = ""
    os.environ["PolarisPassword"] = ""

    AIToolsServer().start()
