FROM python:3.11-slim

WORKDIR /opt/database

ENV PATH=$PATH:/opt/database
ENV PYTHONPATH /opt/database
ENV UV_NO_CACHE=1

RUN pip install uv --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple/

COPY core/memory/database/pyproject.toml ./
COPY core/memory/database/uv.lock ./

RUN uv sync -i https://pypi.tuna.tsinghua.edu.cn/simple/

COPY core/common ./common
RUN mkdir ./memory
COPY core/memory/database ./memory/database

CMD ["uv", "run", "memory/database/main.py"]