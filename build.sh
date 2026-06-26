#!/bin/bash
# ========================================
# CampusShare - Docker镜像构建脚本
# ========================================
# 用法：./build.sh
# 功能：手动构建所有Docker镜像（绕过buildx版本问题）

set -e

echo "=========================================="
echo "  CampusShare 镜像构建脚本"
echo "=========================================="
echo ""

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# 1. 构建后端用户服务
echo "[1/3] 构建用户服务镜像..."
cd backend
docker build -t campusshare/user-service:latest --target user-service .
echo "✓ 用户服务镜像构建完成"
echo ""

# 2. 构建后端网关服务
echo "[2/3] 构建网关服务镜像..."
docker build -t campusshare/gateway-service:latest --target gateway-service .
echo "✓ 网关服务镜像构建完成"
cd ..
echo ""

# 3. 构建前端
echo "[3/3] 构建前端镜像..."
cd frontend
docker build -t campusshare/frontend:latest .
echo "✓ 前端镜像构建完成"
cd ..
echo ""

echo "=========================================="
echo "  所有镜像构建完成！"
echo "=========================================="
echo ""
echo "镜像列表："
docker images | grep campusshare
echo ""
echo "下一步："
echo "  docker compose up -d"
echo "=========================================="
