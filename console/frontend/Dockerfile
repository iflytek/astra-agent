# 第一阶段：构建阶段
FROM node:18-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制 package.json 和 package-lock.json（如果存在）
COPY console/frontend/package*.json ./

# 安装依赖
RUN npm i --legacy-peer-deps

# 复制源代码
COPY console/frontend/ ./

# 执行构建
RUN npm run build-dev

# 第二阶段：部署阶段
FROM nginx:1.15-alpine

ENV port 1881

RUN echo "user nginx; \
worker_processes  8; \
error_log  /var/log/nginx/error.log error; \
pid        /var/run/nginx.pid; \
events { \
    worker_connections  65535; \
} \
http { \
    include       /etc/nginx/mime.types; \
    default_type  application/octet-stream; \
    sendfile        on; \
    keepalive_timeout  65; \
    gzip on; \
    gzip_http_version 1.0; \
    gzip_disable \"MSIE [1-6].\"; \
    gzip_types text/plain application/x-javascript text/css  application/javascript text/javascript; \
    server { \
        listen ${port}; \
        index index.html; \
        root /var/www; \
        access_log off; \
	location / { \
        try_files \$uri \$uri /index.html; \
            expires -1; \
        } \
        location ~ .*\.(gif|jpg|jpeg|png|PNG|bmp|swf|asp|cfm|xml|py|pl|lasso|cfc|afp|txt|zip|log|ico|csv|json|xls|pdf|mp3|mp4|apk)$ \
        { \
            expires      1y; \
            access_log off; \
        } \
        location ~ .*\.(js|css)?$ \
        { \
            expires      1y; \
            access_log off; \
        } \
    } \
}" > /etc/nginx/nginx.conf

EXPOSE ${port}

# 从构建阶段复制构建产物
COPY --from=builder /app/dist /var/www