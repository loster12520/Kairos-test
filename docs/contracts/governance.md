# 治理与变更流程

- 状态：M6 治理基线（2026-10-31）
- 相关：[versioning.md](versioning.md)、[release.md](release.md)、[CONTRIBUTING.md](../../CONTRIBUTING.md)

## 1. 变更分级

| 级别 | 例子 | 流程 |
|---|---|---|
| 补丁 | 文档错字、内部重构、bug 修复 | PR + 门禁 |
| 次版本 | 新增 experimental API、新 recipe、新 starter | PR + 门禁 + release notes |
| 主版本 | 公共 API 破坏、类名算法变更、manifest schema 变更 | RFC/ADR + 迁移指南 + 主版本号 |

## 2. RFC / ADR

- 影响方向、公共契约、类名算法、manifest schema 的变更必须先有 ADR（`docs/adr/`）。
- ADR 结构固定：状态、背景、决策、后果、替代方案。
- 已被推翻的 ADR 不删，标记为"已取代"并链接新文档。

## 3. 弃用窗口

- 公共 API 弃用后至少保留一个次版本的窗口，弃用注解写清替代品。
- 类名算法变更不属于普通弃用：它必须走样式契约主版本，并提供新旧映射（`api/*.api` 与 manifest 版本同时更新）。

## 4. 发布节奏

- 补丁版本按需；次版本不承诺固定周期；主版本只在迁移指南就绪时发布。
- 每次发布跑 [release.md](release.md) 中的全部门禁，结果写进 release notes。

## 5. 维护者

当前为单人维护。引入第二位维护者时，需要：

- 至少三次被合并的非琐碎 PR；
- 熟悉编译器插件与 manifest 契约；
- 对安全与确定性门禁有维护记录。

## 6. 不做什么

- 不做向后兼容的无限承诺：弃用窗口结束后旧 API 会被删除；
- 不为"通用框架"扩边界（见 [non-goals.md](non-goals.md)）；
- 不接受无法用测试或验收脚本证明的"优化"。
