FROM python:3.11-slim

WORKDIR /opt/rpa

ENV PATH=$PATH:/opt/rpa
ENV PYTHONPATH /opt/rpa
ENV UV_NO_CACHE=1

RUN pip install uv --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple/

COPY core/plugin/rpa/pyproject.toml ./
COPY core/plugin/rpa/uv.lock ./

RUN uv sync -i https://pypi.tuna.tsinghua.edu.cn/simple/

COPY core/common ./common
RUN mkdir ./plugin
COPY core/plugin/rpa ./plugin/rpa

CMD ["uv", "run", "plugin/rpa/main.py"]