# CampusShare 虚拟机部署脚本
# 在虚拟机上执行这个脚本即可完成部署

echo "=========================================="
echo "  CampusShare 一键部署脚本"
echo "=========================================="
echo ""

# 1. 安装 git
echo "[1/4] 安装 git..."
if ! command -v git &> /dev/null; then
    yum install -y git
fi
echo "✓ git 安装完成"

# 2. 克隆项目
echo ""
echo "[2/4] 克隆项目..."
if [ -d "CampusShare" ]; then
    echo "项目已存在，跳过克隆"
else
    git clone git@github.com:Xstly1014/CampusShare.git
fi
echo "✓ 项目克隆完成"

# 3. 进入项目目录
cd CampusShare
echo "✓ 进入项目目录: $(pwd)"

# 4. 启动服务
echo ""
echo "[3/4] 启动所有服务..."
docker-compose up -d

# 5. 查看状态
echo ""
echo "[4/4] 查看服务状态..."
docker-compose ps

echo ""
echo "=========================================="
echo "  部署完成！"
echo "=========================================="
echo ""
echo "访问地址："
echo "  - 前端页面: http://localhost"
echo "  - API网关:  http://localhost:8080"
echo "  - 用户服务:  http://localhost:8081"
echo ""
echo "测试登录命令："
echo "  curl -X POST http://localhost:8080/api/auth/register \\"
echo '    -H "Content-Type: application/json" \'
echo '    -d '\''{"username":"test","password":"123456","email":"test@test.com"}'\'''
echo ""
echo "查看日志命令："
echo "  docker-compose logs -f"
echo "=========================================="
