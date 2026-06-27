# Agent 工作前置文档

> 本文档面向与本项目协作的 AI Agent。每次新对话开始时，Agent 应先阅读本文档，以了解必须遵守的工作流程与规范。本文档**只描述工作流程**，不涉及项目本身的技术细节与功能理解。

---

## 一、环境与工具

### 1.1 部署环境
- **部署位置**：虚拟机 `192.168.150.103`
- **部署方式**：通过 `docker-compose` 命令拉起（注意命令带横杠，即 `docker-compose`，而非 `docker compose`）
- **服务清单**：mysql、redis、user-service、gateway-service、frontend、prometheus、grafana、tempo

### 1.2 本地 Java 17
- **安装路径**：`E:\javaJdk17`（系统默认 java 可能是 1.8，必须显式指定 17）
- **验证命令**：
  ```powershell
  E:\javaJdk17\bin\java -version
  ```

### 1.3 本地编译
- **后端项目根目录**：`e:\workspace_work\CampusShare\backend`
- **编译命令**（PowerShell）：
  ```powershell
  $env:JAVA_HOME = "E:\javaJdk17"
  $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
  cd e:\workspace_work\CampusShare\backend
  mvn clean compile -DskipTests
  ```
- **判定标准**：输出包含 `[INFO] BUILD SUCCESS` 即视为编译通过；若出现 `BUILD FAILURE` 或 `ERROR`，必须先修复再继续。
- **注意**：PowerShell 中 Maven 输出可能夹杂 CLIXML 进度信息，需用 `findstr /C:"BUILD" /C:"ERROR"` 过滤确认结果。
- 项目根目录已提供 `compile.ps1` / `compile.bat` 脚本，可直接调用。

---

## 二、代码修改工作流（每次修复/开发必须遵守）

```
定位问题 → 修改代码 → 本地编译验证 → 推送 GitHub → 写 changelog/更新 docs → 返回重启命令
```

### 2.1 修改前
- 先用 `read_file` / `search_content` / `search_file` 充分了解相关代码，不要凭猜测修改。
- 对要修改的文件，若近 5 条消息内未读取过，应先 `read_file` 再编辑，避免上下文过期导致 `replace_in_file` 失败。
- 优先使用 `replace_in_file` 做精准修改，避免大范围重写用户文件。

### 2.2 修改后、推送前（必须）
1. **本地编译验证**：运行上述 Java 17 编译命令，确认 `BUILD SUCCESS`。
   - 编译不通过时，不得推送；须修复后重新编译，最多重试 3 次，仍失败则停下询问用户。
2. **检查改动范围**：确认只改动了必要的文件，没有误改。

### 2.3 推送 GitHub
- **远程仓库**：`git@github.com:Xstly1014/CampusShare.git`，分支 `master`
- **Commit message 语言**：**必须用英文**（如 `Fix post list response unwrapping`）。
- **推送流程**（PowerShell）：
  ```powershell
  cd e:\workspace_work\CampusShare
  git add -A
  git commit -m "<英文 commit message>"
  git push origin master
  ```
- **Git 安全红线**：
  - 不要修改 git config
  - 不要执行 `push --force`、`hard reset` 等破坏性操作
  - 不要加 `--no-verify` 跳过钩子
  - 除非用户明确要求，不要主动 commit/push；通常在完成一轮修复后按工作流推送。

### 2.4 推送后（文档与总结）
- **解决了一个大问题** → 在 `changelog/` 下新建文件，文件名格式 `YYYY-MM-DD-简短英文描述.md`，内容用**中文**写（问题、根因、修复方案、改动文件、总结表）。
- **做了一次功能更新** → 更新 `docs/` 下对应的项目文档（如 `PRD.md`、`api-docs.md`、`tech-design.md`、`deployment.md` 等）。
- changelog 与 docs 的更新应随代码改动一并 commit 推送。

### 2.5 返回重启命令（必须）
完成一轮修复并推送后，向用户返回在虚拟机上执行的重启命令，格式如下：

```bash
cd /root/CampusShare && git pull origin master
docker-compose up -d --build <改动服务1> <改动服务2>
```

- **只重启改动的服务**，不要把所有服务全部挂掉重启。
- 服务名与 `docker-compose.yml` 中一致，例如：`user-service`、`gateway-service`、`frontend`。
- 若仅改前端 → 重启 `frontend`；若仅改后端某服务 → 重启对应服务；若改了网关白名单 → 重启 `gateway-service`。
- 若改动涉及数据库初始化脚本（`backend/docker/mysql/init.sql`），需提示用户可能要重建 mysql 容器（数据会重置，须提前确认）。

---

## 三、数据初始化接口（调试用）

项目内置了测试数据管理接口，通过网关（8080端口）调用，**无需登录**（已在网关白名单 `/api/admin/`）：

| 接口 | 方法 | 参数 | 说明 |
|------|------|------|------|
| `/api/admin/clear-posts` | POST | 无 | 清空所有帖子及关联数据（posts/post_stars/post_likes/view_history + Redis缓存） |
| `/api/admin/init-test-data` | POST | `postsPerSchool`（默认10） | 为学校1-8生成测试帖子，内容随机，浏览量/点赞/收藏全为0 |

**调试时重置数据流程**（在虚拟机上执行）：
```bash
# 1. 先清空旧数据
curl -X POST http://localhost:8080/api/admin/clear-posts

# 2. 重新生成（每校10条）
curl -X POST "http://localhost:8080/api/admin/init-test-data?postsPerSchool=10"
```

> 注意：这两个接口仅用于开发调试，生产环境应禁用或加权限控制。

---

## 四、快速参考

| 事项 | 关键点 |
|------|--------|
| Java 路径 | `E:\javaJdk17` |
| 编译命令 | `mvn clean compile -DskipTests`（在 `backend/` 下，先设 JAVA_HOME） |
| 编译判定 | `BUILD SUCCESS` |
| Git 远程 | `git@github.com:Xstly1014/CampusShare.git` (master) |
| Commit 语言 | 英文 |
| Changelog 语言 | 中文，存 `changelog/` |
| 文档更新 | 功能更新时改 `docs/` |
| 部署机 | `192.168.150.103` |
| 部署命令 | `docker-compose`（带横杠） |
| 重启原则 | 只重启改动服务，不全量重启 |
