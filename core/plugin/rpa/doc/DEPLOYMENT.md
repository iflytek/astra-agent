# 🚀 Deployment Guide

This document provides a detailed deployment guide for the Xingchen RPA Server, including local development, testing environments, and production deployments.

## 📋 Table of Contents

- [Environment Requirements](#environment-requirements)
- [Local Development Deployment](#local-development-deployment)
- [Docker Deployment](#docker-deployment)
- [Production Environment Deployment](#production-environment-deployment)
- [Load Balancing Configuration](#load-balancing-configuration)
- [Monitoring and Logging](#monitoring-and-logging)
- [Troubleshooting](#troubleshooting)

## 🛠️ Environment Requirements

### System Requirements
- **Operating System**: Linux (Ubuntu 20.04+, CentOS 7+), macOS, Windows 10+
- **Python**: 3.11 or higher
- **Memory**: Minimum 2GB, recommended 4GB+
- **Disk**: Minimum 1GB available space
- **Network**: Access to external RPA API services required

### Software Dependencies
- Python 3.11+
- pip or uv package manager
- Git (for code management)
- Optional: Docker & Docker Compose

## 💻 Local Development Deployment

### 1. Environment Setup

```bash
# Create project directory
mkdir -p ~/projects/rpa-server
cd ~/projects/rpa-server

# Clone code
git clone <repository-url> .

# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # Linux/macOS
# or venv\Scripts\activate  # Windows

# Upgrade pip
pip install --upgrade pip
```

### 2. Install Dependencies

```bash
# Install production dependencies
pip install -r requirements.txt

# Install development dependencies (optional)
pip install pytest pytest-cov pytest-asyncio black isort mypy
```

### 3. Configure Environment Variables

```bash
# Copy environment variable template
cp .env.example .env

# Edit configuration file
nano .env
```

**Key Configuration Items**:
```bash
# Basic configuration
LOG_LEVEL=DEBUG
LOG_PATH=./logs

# RPA API configuration (replace with actual addresses)
XIAOWU_RPA_TASK_CREATE_URL=https://your-rpa-api.com/create
XIAOWU_RPA_TASK_QUERY_URL=https://your-rpa-api.com/query
```

### 4. Start Development Server

```bash
# Method 1: Using application entry point
python main.py

# Method 2: Using uvicorn (recommended for development)
uvicorn api.app:rpa_server_app --reload --host 127.0.0.1 --port 19999

# Method 3: Using custom startup script
python -c "
import uvicorn
from plugin.rpa.api.app import rpa_server_app
uvicorn.run(
    rpa_server_app,
    host='127.0.0.1',
    port=19999,
    reload=True,
    log_level='debug'
)"
```

### 5. Verify Deployment

```bash
# Check service status
curl http://127.0.0.1:19999/rpa/v1/docs

# Run health check
curl -X POST http://127.0.0.1:19999/rpa/v1/exec \
  -H "Authorization: Bearer test-token" \
  -H "Content-Type: application/json" \
  -d '{"project_id": "health-check"}'
```

## 🐳 Docker Deployment

### 1. Create Dockerfile

```dockerfile
# Dockerfile
FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy dependency files
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create log directory
RUN mkdir -p logs

# Set permissions
RUN chmod +x main.py

# Expose port
EXPOSE 19999

# Health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:19999/rpa/v1/docs || exit 1

# Startup command
CMD ["python", "main.py"]
```

### 2. Create Docker Compose Configuration

```yaml
# docker-compose.yml
version: '3.8'

services:
  rpa-server:
    build: .
    ports:
      - "19999:19999"
    environment:
      - LOG_LEVEL=INFO
      - LOG_PATH=/app/logs
      - XIAOWU_RPA_TIMEOUT=300
      - XIAOWU_RPA_TASK_CREATE_URL=${XIAOWU_RPA_TASK_CREATE_URL}
      - XIAOWU_RPA_TASK_QUERY_URL=${XIAOWU_RPA_TASK_QUERY_URL}
    volumes:
      - ./logs:/app/logs
      - ./config:/app/config
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:19999/rpa/v1/docs"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  # Optional: Add Nginx reverse proxy
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    depends_on:
      - rpa-server
    restart: unless-stopped
```

### 3. Build and Run

```bash
# Build image
docker build -t xingchen-rpa-server .

# Start using Docker Compose
docker-compose up -d

# View logs
docker-compose logs -f rpa-server

# Stop service
docker-compose down
```

### 4. Single Container Run

```bash
# Run single container
docker run -d \
  --name rpa-server \
  -p 19999:19999 \
  -e LOG_LEVEL=INFO \
  -e XIAOWU_RPA_TASK_CREATE_URL=https://your-api.com/create \
  -e XIAOWU_RPA_TASK_QUERY_URL=https://your-api.com/query \
  -v $(pwd)/logs:/app/logs \
  xingchen-rpa-server

# Check container status
docker ps
docker logs rpa-server
```

## 🏭 Production Environment Deployment

### 1. Using Gunicorn + Uvicorn Workers

```bash
# Install Gunicorn
pip install gunicorn

# Start production server
gunicorn api.app:rpa_server_app \
  -w 4 \
  -k uvicorn.workers.UvicornWorker \
  --bind 0.0.0.0:19999 \
  --access-logfile logs/access.log \
  --error-logfile logs/error.log \
  --log-level info \
  --preload
```

### 2. Create Gunicorn Configuration File

```python
# gunicorn.conf.py
bind = "0.0.0.0:19999"
workers = 4
worker_class = "uvicorn.workers.UvicornWorker"
worker_connections = 1000
max_requests = 1000
max_requests_jitter = 100
timeout = 30
keepalive = 2

# Logging configuration
accesslog = "logs/access.log"
errorlog = "logs/error.log"
loglevel = "info"
access_log_format = '%(h)s %(l)s %(u)s %(t)s "%(r)s" %(s)s %(b)s "%(f)s" "%(a)s" %(D)s'

# Process management
preload_app = True
worker_tmp_dir = "/dev/shm"

# Security configuration
limit_request_line = 4094
limit_request_fields = 100
limit_request_field_size = 8190
```

Start using configuration file:
```bash
gunicorn -c gunicorn.conf.py api.app:rpa_server_app
```

### 3. Systemd Service Configuration

```ini
# /etc/systemd/system/rpa-server.service
[Unit]
Description=Xingchen RPA Server
After=network.target

[Service]
Type=exec
User=rpa
Group=rpa
WorkingDirectory=/opt/rpa-server
Environment=PATH=/opt/rpa-server/venv/bin
ExecStart=/opt/rpa-server/venv/bin/gunicorn -c gunicorn.conf.py api.app:rpa_server_app
ExecReload=/bin/kill -s HUP $MAINPID
Restart=always
RestartSec=10

# Security settings
NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=strict
ReadWritePaths=/opt/rpa-server/logs

[Install]
WantedBy=multi-user.target
```

Start service:
```bash
# Create user and directory
sudo useradd -r -s /bin/false rpa
sudo mkdir -p /opt/rpa-server
sudo chown rpa:rpa /opt/rpa-server

# Deploy application
sudo cp -r . /opt/rpa-server/
sudo chown -R rpa:rpa /opt/rpa-server/

# Start service
sudo systemctl daemon-reload
sudo systemctl enable rpa-server
sudo systemctl start rpa-server

# Check status
sudo systemctl status rpa-server
```

### 4. PM2 Deployment (Node.js Environment)

```javascript
// ecosystem.config.js
module.exports = {
  apps: [{
    name: 'rpa-server',
    script: '/opt/rpa-server/venv/bin/gunicorn',
    args: '-c gunicorn.conf.py api.app:rpa_server_app',
    cwd: '/opt/rpa-server',
    instances: 1,
    autorestart: true,
    watch: false,
    max_memory_restart: '1G',
    env: {
      LOG_LEVEL: 'INFO',
      NODE_ENV: 'production'
    },
    error_file: './logs/pm2-error.log',
    out_file: './logs/pm2-out.log',
    log_file: './logs/pm2-combined.log',
    time: true
  }]
};
```

Deploy using PM2:
```bash
# Install PM2
npm install -g pm2

# Start application
pm2 start ecosystem.config.js

# Check status
pm2 status
pm2 logs rpa-server

# Set auto-start on boot
pm2 startup
pm2 save
```

## ⚖️ Load Balancing Configuration

### Nginx Configuration

```nginx
# /etc/nginx/sites-available/rpa-server
upstream rpa_backend {
    least_conn;
    server 127.0.0.1:19999 weight=1 max_fails=3 fail_timeout=30s;
    server 127.0.0.1:19998 weight=1 max_fails=3 fail_timeout=30s;
    server 127.0.0.1:19997 weight=1 max_fails=3 fail_timeout=30s;
}

server {
    listen 80;
    listen [::]:80;
    server_name rpa.yourdomain.com;

    # Redirect to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name rpa.yourdomain.com;

    # SSL configuration
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Maximum client upload size
    client_max_body_size 10M;

    # Proxy configuration
    location /rpa/ {
        proxy_pass http://rpa_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE support
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
    }

    # Health check endpoint
    location /health {
        access_log off;
        return 200 "healthy\n";
        add_header Content-Type text/plain;
    }

    # Security headers
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";
}
```

Enable configuration:
```bash
sudo ln -s /etc/nginx/sites-available/rpa-server /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### HAProxy Configuration

```
# /etc/haproxy/haproxy.cfg
global
    daemon
    chroot /var/lib/haproxy
    stats socket /run/haproxy/admin.sock mode 660 level admin
    stats timeout 30s
    user haproxy
    group haproxy

defaults
    mode http
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms
    errorfile 400 /etc/haproxy/errors/400.http
    errorfile 403 /etc/haproxy/errors/403.http
    errorfile 408 /etc/haproxy/errors/408.http
    errorfile 500 /etc/haproxy/errors/500.http
    errorfile 502 /etc/haproxy/errors/502.http
    errorfile 503 /etc/haproxy/errors/503.http
    errorfile 504 /etc/haproxy/errors/504.http

frontend rpa_frontend
    bind *:80
    bind *:443 ssl crt /path/to/cert.pem
    redirect scheme https if !{ ssl_fc }
    default_backend rpa_servers

backend rpa_servers
    balance roundrobin
    option httpchk GET /rpa/v1/docs
    http-check expect status 200
    server rpa1 127.0.0.1:19999 check
    server rpa2 127.0.0.1:19998 check backup
    server rpa3 127.0.0.1:19997 check backup

listen stats
    bind *:8404
    stats enable
    stats uri /stats
    stats refresh 30s
    stats admin if TRUE
```

## 📊 Monitoring and Logging

### 1. Application Monitoring

```python
# monitoring.py
import psutil
import time
from pathlib import Path

def get_system_metrics():
    """Get system metrics"""
    return {
        'cpu_percent': psutil.cpu_percent(),
        'memory_percent': psutil.virtual_memory().percent,
        'disk_percent': psutil.disk_usage('/').percent,
        'load_average': psutil.getloadavg(),
        'timestamp': time.time()
    }

def get_app_metrics():
    """Get application metrics"""
    process = psutil.Process()
    return {
        'memory_mb': process.memory_info().rss / 1024 / 1024,
        'cpu_percent': process.cpu_percent(),
        'threads': process.num_threads(),
        'connections': len(process.connections()),
        'open_files': process.num_fds() if hasattr(process, 'num_fds') else 0
    }
```

### 2. Log Aggregation

```bash
# Use rsyslog to aggregate logs
echo "*.* @@log-server:514" >> /etc/rsyslog.conf
systemctl restart rsyslog

# Use Logrotate to rotate logs
cat > /etc/logrotate.d/rpa-server << EOF
/opt/rpa-server/logs/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    create 644 rpa rpa
    postrotate
        systemctl reload rpa-server
    endscript
}
EOF
```

### 3. Prometheus Metrics

```python
# metrics.py
from prometheus_client import Counter, Histogram, Gauge, generate_latest
from fastapi import Response

# Define metrics
REQUEST_COUNT = Counter('rpa_requests_total', 'Total requests', ['method', 'endpoint'])
REQUEST_DURATION = Histogram('rpa_request_duration_seconds', 'Request duration')
ACTIVE_TASKS = Gauge('rpa_active_tasks', 'Active RPA tasks')

@app.middleware("http")
async def metrics_middleware(request, call_next):
    with REQUEST_DURATION.time():
        response = await call_next(request)
        REQUEST_COUNT.labels(
            method=request.method,
            endpoint=request.url.path
        ).inc()
    return response

@app.get("/metrics")
async def get_metrics():
    return Response(generate_latest(), media_type="text/plain")
```

## 🔧 Troubleshooting

### Common Issue Diagnosis

#### 1. Service Startup Failure

```bash
# Check port usage
sudo netstat -tulpn | grep 19999
sudo lsof -i :19999

# Check configuration files
python -c "
import os
from dotenv import load_dotenv
load_dotenv('.env')
print('XIAOWU_RPA_TASK_CREATE_URL:', os.getenv('XIAOWU_RPA_TASK_CREATE_URL'))
"

# Check dependencies
pip check
```

#### 2. Connection Timeout Issues

```bash
# Test network connection
curl -v $XIAOWU_RPA_TASK_CREATE_URL
ping $(echo $XIAOWU_RPA_TASK_CREATE_URL | cut -d'/' -f3)

# Check firewall
sudo ufw status
sudo iptables -L

# Check DNS resolution
nslookup $(echo $XIAOWU_RPA_TASK_CREATE_URL | cut -d'/' -f3)
```

#### 3. Memory Leak Debugging

```bash
# Monitor memory usage
watch -n 1 'ps aux | grep gunicorn'

# Use memory_profiler
pip install memory-profiler
python -m memory_profiler main.py
```

### Log Analysis Script

```bash
#!/bin/bash
# analyze_logs.sh

LOG_FILE="/opt/rpa-server/logs/rpa-server.log"

echo "=== RPA Server Log Analysis ==="
echo "Log file: $LOG_FILE"
echo

# Error statistics
echo "❌ Error Statistics:"
grep -c "ERROR" $LOG_FILE
echo

# Recent errors
echo "🔍 Recent 10 Errors:"
grep "ERROR" $LOG_FILE | tail -10
echo

# Request statistics
echo "📊 Today's Request Statistics:"
grep "$(date '+%Y-%m-%d')" $LOG_FILE | grep "POST /rpa/v1/exec" | wc -l
echo

# Response time analysis
echo "⏱️ Average Response Time:"
grep "Process-Time" $LOG_FILE | awk '{print $NF}' | \
  awk '{sum+=$1; count++} END {print sum/count "s"}'
```

### Performance Tuning

```bash
# System-level optimization
echo 'net.core.somaxconn = 65535' >> /etc/sysctl.conf
echo 'fs.file-max = 100000' >> /etc/sysctl.conf
sysctl -p

# Application-level optimization
export PYTHONOPTIMIZE=1
export PYTHONDONTWRITEBYTECODE=1

# Gunicorn tuning
gunicorn api.app:rpa_server_app \
  -w $(nproc) \
  --worker-tmp-dir /dev/shm \
  --worker-class uvicorn.workers.UvicornWorker \
  --max-requests 1000 \
  --max-requests-jitter 100
```

---

## 📞 Get Help

- 🐛 **Issue Reports**: [GitHub Issues](https://github.com/your-org/xingchen-rpa-server/issues)
- 📖 **Detailed Documentation**: [README.md](./README.md)
- 🔧 **API Examples**: [API_EXAMPLES.md](./API_EXAMPLES.md)

This deployment guide covers the complete deployment process from development to production environments. You can choose the appropriate deployment method based on your actual requirements.