FROM python:3.11-slim
WORKDIR /opt/agent

ENV PATH=$PATH:/opt/agent
ENV PYTHONPATH /opt/agent:/opt

# Install uv
RUN pip install uv

COPY core/common/ /opt/common/
COPY core/agent/ /opt/agent/
COPY core/agent/config.env.example /opt/agent/config.env

# Install dependencies using uv
# Export and install common dependencies
RUN cd /opt/common && uv export --format requirements-txt --no-dev > requirements.txt && \
    uv pip install -r requirements.txt --system

# Export and install agent dependencies
RUN cd /opt/agent && uv export --format requirements-txt --no-dev > requirements.txt && \
    uv pip install -r requirements.txt --system

CMD ["python", "main.py"]
