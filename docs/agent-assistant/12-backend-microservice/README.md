# 12 后端微服务设计

> 状态: 草稿
> 最后更新: 2026-06-30

本目录规划 agent-service 微服务的工程实现：模块结构、数据库 schema、配置、定时任务、Docker 接入。

## 文档清单

| 文档 | 主题 |
|------|------|
| [service-structure.md](./service-structure.md) | 工程结构与包划分 |
| [database-schema.md](./database-schema.md) | agent-service 拥有的表与 DDL |
| [configuration.md](./configuration.md) | application.yml 与环境变量 |
| [scheduling-tasks.md](./scheduling-tasks.md) | 定时任务（归档/衰减/证据拉取） |
| [docker-integration.md](./docker-integration.md) | Docker Compose 接入 |

## 设计原则

1. **遵循 AGENT-WORKFLOW.md 第六章铁律**: agent-service 只访问自己的表，跨服务走 Feign。
2. **复用 common 模块**: Result/异常/JWT 常量等复用 campushare-common。
3. **配置外置**: 敏感信息（API Key）走 .env，业务参数走 application.yml。
4. **可观测**: Actuator + Micrometer + OpenTelemetry，与现有服务一致。
5. **独立部署**: agent-service 独立镜像独立容器，不与 user/post 耦合。
