#!/bin/bash
# CampusShare 一键部署脚本
# 使用方法：在你的服务器上运行此脚本

set -e  # 遇到错误立即退出

echo "========================================"
echo "🚀 CampusShare 部署脚本"
echo "========================================"

# 1. 更新系统
echo ""
echo "📦 步骤 1/8：更新系统..."
sudo apt update && sudo apt upgrade -y

# 2. 安装 Docker
echo ""
echo "🐳 步骤 2/8：安装 Docker..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker $USER
    rm get-docker.sh
    echo "✅ Docker 安装完成"
else
    echo "✅ Docker 已安装，跳过"
fi

# 3. 安装 Docker Compose
echo ""
echo "🐳 步骤 3/8：安装 Docker Compose..."
if ! command -v docker-compose &> /dev/null; then
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    echo "✅ Docker Compose 安装完成"
else
    echo "✅ Docker Compose 已安装，跳过"
fi

# 4. 安装 Git
echo ""
echo "📦 步骤 4/8：安装 Git..."
sudo apt install -y git

# 5. 克隆项目
echo ""
echo "📥 步骤 5/8：克隆 CampusShare 项目..."
if [ -d "CampusShare" ]; then
    echo "⚠️ 项目目录已存在，更新代码..."
    cd CampusShare
    git pull origin main || git pull origin master
    cd ..
else
    git clone https://github.com/Xstly1014/CampusShare.git
fi

# 6. 配置环境变量
echo ""
echo "⚙️ 步骤 6/8：配置环境变量..."
cd CampusShare

# 生成随机密码
DB_PASSWORD=$(openssl rand -base64 32)

# 创建 .env 文件
cat > .env << EOF
# 数据库配置
MYSQL_ROOT_PASSWORD=${DB_PASSWORD}
MYSQL_DATABASE=campusshare
MYSQL_USER=campus
MYSQL_PASSWORD=${DB_PASSWORD}

# 后端配置
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:mysql://mysql:3306/campusshare?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DATABASE_USERNAME=root
DATABASE_PASSWORD=${DB_PASSWORD}

# Redis 配置
REDIS_HOST=redis
REDIS_PORT=6379

# JWT 配置
JWT_SECRET=$(openssl rand -base64 64)
EOF

echo "✅ 环境变量配置完成"
echo "   数据库密码：${DB_PASSWORD}"

# 7. 构建并启动服务
echo ""
echo "🏗️ 步骤 7/8：构建并启动服务（这可能需要 10-15 分钟）..."
docker-compose down || true
docker-compose up -d --build

echo ""
echo "⏳ 等待服务启动..."
sleep 30

# 8. 验证部署
echo ""
echo "✅ 步骤 8/8：验证部署..."
docker-compose ps

echo ""
echo "========================================"
echo "🎉 部署完成！"
echo "========================================"
echo ""
echo "📝 访问信息："
echo "   前端：http://$(curl -s ifconfig.me)"
echo "   后端 API：http://$(curl -s ifconfig.me):8080"
echo ""
echo "📊 查看服务状态："
echo "   cd CampusShare"
echo "   docker-compose ps"
echo ""
echo "📋 查看日志："
echo "   docker-compose logs -f"
echo ""
echo "🛠️ 重启服务："
echo "   docker-compose restart"
echo ""
echo "========================================"
