# VMware 虚拟机网络访问问题排查指南

> 本文档记录了启用 WSL2 + Hyper-V 后导致无法访问虚拟机 Web 服务的完整排查过程和解决方案。
> 适用于：宿主机无法访问虚拟机 HTTP/SSH 服务、ping 通但 TCP 超时等场景。

---

## 问题现象

```
[root@localhost ~]# docker-compose up -d
[+] Running 11/11
 ✔ Container campushare-frontend  Running  0.0s  (非 Healthy)
 ✔ Container campushare-agent-service  Running  0.0s  (非 Healthy)
 ✔ 其他容器 Healthy
```

- 宿主机浏览器访问 `http://192.168.150.103/` 超时
- 宿主机 `ping 192.168.150.103` 通
- 宿主机 `curl http://192.168.150.103/` 超时

---

## 根本原因

**启用 WSL2 + Hyper-V 后，Windows Hypervisor 接管硬件虚拟化，破坏了 VMware 的网络栈：**

1. VMnet8 网卡 IP 变成 `169.254.x.x`（APIPA 失败地址）
2. Docker 重启后 iptables FORWARD 链策略变成 `DROP`
3. Clash TUN 模式劫持流量，干扰正常网络访问

---

## 完整排查流程

### 阶段 1：确认问题范围

#### 1.1 检查容器状态

```bash
# 虚拟机内执行
docker ps
docker logs --tail 30 campushare-frontend
docker exec campushare-frontend curl -v http://localhost/health
docker exec campushare-frontend ls -la /usr/share/nginx/html
```

**判断标准：**
- 容器 Running 但非 Healthy → 健康检查未通过
- 容器内 curl 通 → 服务正常，问题在网络层

#### 1.2 检查端口监听

```bash
# 虚拟机内执行
ss -tlnp | grep :80
docker port campushare-frontend
```

**正常输出：**
```
LISTEN  0  128  *:80  *:*  users:(("docker-proxy",...))
80/tcp -> 0.0.0.0:80
```

#### 1.3 检查 Docker iptables 规则

```bash
iptables -t nat -L DOCKER -n | grep 80
```

**正常输出：**
```
DNAT  tcp  --  0.0.0.0/0  0.0.0.0/0  tcp dpt:80 to:172.24.0.2:80
```

---

### 阶段 2：排查宿主机网络

#### 2.1 测试 TCP 端口连通性

```powershell
# 宿主机 PowerShell 执行
Test-NetConnection -ComputerName 192.168.150.103 -Port 80
Test-NetConnection -ComputerName 192.168.150.103 -Port 22
```

**关键判断：**
- `TcpTestSucceeded: False` + `PingSucceeded: True` → 防火墙或网络栈问题
- Port 22 通但 Port 80 不通 → Docker iptables FORWARD 链问题

#### 2.2 检查 Windows 防火墙

```powershell
# 管理员 PowerShell
Get-NetFirewallRule -Direction Outbound -Enabled True -Action Block | Format-Table Name,DisplayName
Get-NetFirewallProfile | Format-Table Name,Enabled
```

**常见拦截规则：**
- `codex_sandbox_offline_block_outbound` - Codex 沙箱出站拦截
- `codex_sandbox_offline_block_loopback_tcp` - Codex 沙箱 loopback 拦截

**禁用拦截规则：**
```powershell
Disable-NetFirewallRule -Name "{规则GUID}"
```

#### 2.3 检查 VMnet8 网卡 IP（关键）

```powershell
ipconfig | Select-String -Pattern "VMnet8" -Context 0,5
```

**异常输出（问题）：**
```
Autoconfiguration IPv4 Address. . : 169.254.82.180
Subnet Mask . . . . . . . . . . . : 255.255.0.0
```

**正常输出：**
```
IPv4 地址 . . . . . . . . . . . . : 192.168.150.1
Subnet Mask . . . . . . . . . . . : 255.255.255.0
```

---

### 阶段 3：修复 VMnet8 网卡 IP

#### 3.1 检查 VMware 虚拟网络编辑器

1. VMware → 编辑 → 虚拟网络编辑器 → 更改设置
2. 选中 VMnet8，确认配置：
   - **类型**：NAT 模式
   - **子网 IP**：`192.168.150.0`
   - **子网掩码**：`255.255.255.0`
3. NAT 设置 → 网关 IP：`192.168.150.2`
4. DHCP 设置 → 起始 `192.168.150.128`，结束 `192.168.150.254`

#### 3.2 设置 VMnet8 网卡静态 IP

```powershell
# 管理员 PowerShell

# 方法 1：PowerShell 命令
Get-NetIPAddress -InterfaceAlias "VMware Network Adapter VMnet8" -AddressFamily IPv4 -ErrorAction SilentlyContinue | Remove-NetIPAddress -Confirm:$false
New-NetIPAddress -InterfaceAlias "VMware Network Adapter VMnet8" -IPAddress 192.168.150.1 -PrefixLength 24

# 方法 2：netsh（推荐，更可靠）
netsh interface ip set address "VMware Network Adapter VMnet8" static 192.168.150.1 255.255.255.0

# 验证
ipconfig | Select-String -Pattern "VMnet8" -Context 0,5
Get-NetIPAddress -InterfaceAlias "VMware Network Adapter VMnet8" -AddressFamily IPv4 | Format-Table IPAddress,AddressState
```

**验证标准：**
- `IPAddress: 192.168.150.1`
- `AddressState: Preferred`（不是 Tentative 或 Invalid）

#### 3.3 如果 IP 设置后状态是 Tentative/Invalid

```powershell
# 重启网卡
Disable-NetAdapter -Name "VMware Network Adapter VMnet8" -Confirm:$false
Start-Sleep -Seconds 3
Enable-NetAdapter -Name "VMware Network Adapter VMnet8" -Confirm:$false
Start-Sleep -Seconds 5

# 重新设置 IP
netsh interface ip set address "VMware Network Adapter VMnet8" static 192.168.150.1 255.255.255.0
```

#### 3.4 手动设置（如果命令行失败）

1. 控制面板 → 网络和 Internet → 网络连接
2. 右键 VMware Network Adapter VMnet8 → 属性
3. 双击 IPv4 → 使用下面的 IP 地址
4. IP: `192.168.150.1`，子网掩码: `255.255.255.0`，网关留空

---

### 阶段 4：修复虚拟机静态 IP

#### 4.1 确认虚拟机网卡名

```bash
# 虚拟机内执行
ip addr
```

找到网卡名（通常是 `ens33`、`eth0` 或 `enp0s3`）。

#### 4.2 设置静态 IP（CentOS 7）

```bash
# 切换到 root
su -

# 备份并写入配置
cp /etc/sysconfig/network-scripts/ifcfg-ens33 /etc/sysconfig/network-scripts/ifcfg-ens33.bak

cat > /etc/sysconfig/network-scripts/ifcfg-ens33 << 'EOF'
TYPE=Ethernet
BOOTPROTO=static
NAME=ens33
DEVICE=ens33
ONBOOT=yes
IPADDR=192.168.150.103
NETMASK=255.255.255.0
GATEWAY=192.168.150.2
DNS1=8.8.8.8
DNS2=114.114.114.114
EOF

# 重启网络（SSH 会断开，需用新 IP 重连）
systemctl restart network
```

#### 4.3 设置静态 IP（CentOS 8/Stream/RHEL 8+）

```bash
nmcli connection modify ens33 \
  ipv4.addresses 192.168.150.103/24 \
  ipv4.gateway 192.168.150.2 \
  ipv4.dns "8.8.8.8 114.114.114.114" \
  ipv4.method manual \
  connection.autoconnect yes

nmcli connection up ens33
```

#### 4.4 验证

```bash
ip addr show ens33 | grep "inet "
ping -c 3 192.168.150.1    # 测试宿主机连通
ping -c 3 192.168.150.2    # 测试网关连通
ping -c 3 8.8.8.8          # 测试外网连通
```

**关键：网关必须是 `192.168.150.2`（VMware NAT 网关），不是 `192.168.150.1`**

---

### 阶段 5：禁用 Hyper-V 和 WSL2（根本修复）

#### 5.1 禁用 Hypervisor 启动

```powershell
# 管理员 PowerShell
bcdedit /set hypervisorlaunchtype off
```

#### 5.2 禁用 Hyper-V 和 WSL2 功能

```powershell
dism.exe /online /disable-feature /featurename:Microsoft-Hyper-V-All /norestart
dism.exe /online /disable-feature /featurename:VirtualMachinePlatform /norestart
dism.exe /online /disable-feature /featurename:Microsoft-Windows-Subsystem-Linux /norestart
```

#### 5.3 重启电脑（必须）

```powershell
Restart-Computer
```

#### 5.4 重启后还原 VMware 网络默认设置

1. VMware → 编辑 → 虚拟网络编辑器 → 更改设置
2. 点 **还原默认设置**
3. 重新配置 VMnet8（见阶段 3.1）

---

### 阶段 6：排查 Clash TUN 模式干扰

#### 6.1 识别 TUN 模式干扰

```powershell
# 如果测试时显示 InterfaceAlias: Clash，说明流量走了 TUN
Test-NetConnection -ComputerName 192.168.150.103 -Port 80
```

**异常输出：**
```
InterfaceAlias   : Clash
SourceAddress    : 198.18.0.1
```

#### 6.2 关闭 Clash TUN 模式

- 打开 Clash for Windows / Clash Verge
- 关闭 **TUN Mode**（服务模式/TUN 模式）
- 保留普通代理模式（系统代理）

#### 6.3 配置 TUN 模式直连规则（如需开启 TUN）

在 Clash 配置文件的 `rules` 最前面添加：

```yaml
rules:
  - IP-CIDR,192.168.150.0/24,DIRECT,no-resolve
  # ... 其他规则
```

或在 TUN 配置中排除该网段：

```yaml
tun:
  enable: true
  stack: mixed
  route-exclude-address:
    - 192.168.150.0/24
```

---

### 阶段 7：修复 Docker iptables FORWARD 链（最终解决）

#### 7.1 检查 FORWARD 链策略

```bash
# 虚拟机内执行
iptables -L FORWARD -n --line-numbers | head -20
```

**问题输出（policy DROP）：**
```
Chain FORWARD (policy DROP)
num  target     prot opt source               destination
1    DOCKER-USER  all  --  0.0.0.0/0            0.0.0.0/0
...
14   REJECT     all  --  0.0.0.0/0            0.0.0.0/0  reject-with icmp-port-unreachable
15   REJECT     all  --  0.0.0.0/0            0.0.0.0/0  reject-with icmp-port-unreachable
```

#### 7.2 临时修复（立即生效）

```bash
# 设置 FORWARD 链策略为 ACCEPT
iptables -P FORWARD ACCEPT
```

#### 7.3 删除冲突的 REJECT 规则

```bash
# 删除 FORWARD 链中的 REJECT 规则
iptables -D FORWARD -j REJECT --reject-with icmp-port-unreachable
iptables -D FORWARD -j REJECT --reject-with icmp-port-unreachable

# 验证
iptables -L FORWARD -n --line-numbers | head -20
```

#### 7.4 持久化 iptables 规则

```bash
# 保存 iptables 规则
service iptables save
# 或
iptables-save > /etc/sysconfig/iptables
```

#### 7.5 配置 Docker 开机自动设置 FORWARD ACCEPT

```bash
mkdir -p /etc/systemd/system/docker.service.d
cat > /etc/systemd/system/docker.service.d/override.conf << 'EOF'
[Service]
ExecStartPost=/sbin/iptables -P FORWARD ACCEPT
EOF

systemctl daemon-reload
```

#### 7.6 验证

```bash
# 虚拟机内测试
curl -I http://localhost/
curl -I http://192.168.150.103/
```

```powershell
# 宿主机测试
Test-NetConnection -ComputerName 192.168.150.103 -Port 80
```

---

## 快速诊断流程图

```
访问不了虚拟机 Web 服务
         │
         ▼
┌─────────────────────────────┐
│ 虚拟机内 curl localhost 通吗？│
└─────────────────────────────┘
         │
    通    │           不通
         ▼               │
┌─────────────────┐      ▼
│ 问题在网络层    │  ┌──────────────────┐
└─────────────────┘  │ 检查容器/服务状态 │
         │           └──────────────────┘
         ▼
┌─────────────────────────────┐
│ 宿主机 Test-NetConnection   │
│ Port 22 通吗？              │
└─────────────────────────────┘
         │
   通    │           不通
         ▼               │
┌─────────────────────┐  ▼
│ VMnet8 网卡正常吗？ │  ┌──────────────────────┐
└─────────────────────┘  │ 检查 VMnet8 网卡 IP  │
         │               │ (应为 192.168.150.1) │
   正常  │           异常└──────────────────────┘
         ▼               │
┌──────────────────────┐  ▼
│ 检查 Docker iptables │  ┌─────────────────────┐
│ FORWARD 链策略       │  │ 设置 VMnet8 静态 IP │
└──────────────────────┘  └─────────────────────┘
         │
         ▼
┌──────────────────────────┐
│ iptables -P FORWARD ACCEPT│
└──────────────────────────┘
```

---

## 常见问题速查表

| 现象 | 原因 | 解决方案 |
|------|------|----------|
| ping 通但 curl 超时 | 防火墙拦截 TCP | 检查 Windows/虚拟机防火墙 |
| VMnet8 IP 是 169.254.x.x | DHCP 失败 | 手动设置 192.168.150.1 |
| IP 设置后状态 Invalid | APIPA 冲突 | 用 netsh 强制设置 |
| SSH 通但 80 不通 | Docker FORWARD 链 DROP | `iptables -P FORWARD ACCEPT` |
| Test-NetConnection 走 Clash | TUN 模式劫持 | 关闭 TUN 或添加直连规则 |
| 虚拟机 IP 变成 .128 | DHCP 重新分配 | 设置静态 IP 192.168.150.103 |
| 重启网络后 SSH 断开 | IP 变更 | 用新 IP 重新连接 |
| FORWARD policy DROP | Docker 默认策略 | 持久化 ACCEPT 规则 |

---

## 网络架构参考

```
┌─────────────────────────────────────────────────────────┐
│ 宿主机 (Windows)                                         │
│                                                         │
│  ┌─────────────┐    ┌──────────────────────────────┐   │
│  │ 物理网卡     │    │ VMware Network Adapter VMnet8│   │
│  │ (上网用)     │    │ IP: 192.168.150.1            │   │
│  └─────────────┘    └──────────┬───────────────────┘   │
│                                │                        │
└────────────────────────────────┼────────────────────────┘
                                 │
                                 │ NAT 模式
                                 │
┌────────────────────────────────┼────────────────────────┐
│ 虚拟机 (CentOS)                │                        │
│                                 ▼                        │
│  ┌──────────────────────────────────────┐               │
│  │ ens33 网卡                           │               │
│  │ IP: 192.168.150.103                  │               │
│  │ Gateway: 192.168.150.2 (VMware NAT)  │               │
│  └──────────────┬───────────────────────┘               │
│                 │                                        │
│                 │ Docker 端口映射                        │
│                 ▼                                        │
│  ┌──────────────────────────────────────┐               │
│  │ Docker Container (frontend)          │               │
│  │ 0.0.0.0:80 -> 172.24.0.2:80         │               │
│  └──────────────────────────────────────┘               │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 预防措施

### 1. 避免同时启用 WSL2 和 VMware

- 使用 VMware 时，确保 `bcdedit` 中 `hypervisorlaunchtype` 为 `off`
- 需要使用 WSL2 时，再临时启用

### 2. 持久化网络配置

```bash
# 虚拟机内：持久化 iptables 规则
service iptables save

# 配置 Docker 自动设置 FORWARD ACCEPT
mkdir -p /etc/systemd/system/docker.service.d
cat > /etc/systemd/system/docker.service.d/override.conf << 'EOF'
[Service]
ExecStartPost=/sbin/iptables -P FORWARD ACCEPT
EOF
systemctl daemon-reload
```

### 3. 定期检查网络状态

```bash
# 虚拟机内检查脚本
#!/bin/bash
echo "=== IP 地址 ==="
ip addr show ens33 | grep "inet "
echo "=== 80 端口监听 ==="
ss -tlnp | grep :80
echo "=== FORWARD 链策略 ==="
iptables -L FORWARD -n | head -1
echo "=== Docker 容器状态 ==="
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "frontend|gateway"
```

---

## 相关配置文件路径

| 文件 | 用途 |
|------|------|
| `/etc/sysconfig/network-scripts/ifcfg-ens33` | 虚拟机静态 IP 配置 |
| `/etc/sysconfig/iptables` | iptables 规则持久化 |
| `/etc/systemd/system/docker.service.d/override.conf` | Docker 服务覆盖配置 |
| `C:\Windows\System32\` (管理员 PowerShell) | Windows 防火墙配置 |

---

**文档创建日期**：2026-07-08  
**问题解决日期**：2026-07-08  
**适用环境**：Windows 10 + VMware Workstation + CentOS 7 + Docker
