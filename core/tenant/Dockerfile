FROM golang:1.23 AS builder
WORKDIR /opt/tenant
ENV GOPROXY=https://goproxy.cn,direct
COPY ./core/tenant/go.mod ./core/tenant/go.sum ./
RUN go mod download
COPY ./core/tenant .
RUN go build -o tenant .
FROM debian:bookworm-slim
WORKDIR /opt/tenant
COPY --from=builder /opt/tenant/tenant .
STOPSIGNAL 3
CMD ["./tenant"]