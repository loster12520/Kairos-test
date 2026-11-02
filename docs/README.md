# Kairos 契约文档（M0）

- 状态：M0 冻结于 2026-10-31；同一天 M1 构建底座开始并行落地
- 上游计划：[plan-1.0.md](../.lignting/kairos/plan-1.0.md)
- 方向起点：[direction.md](../direction.md)（其中与生态化冲突的旧非目标尚未修订，见 [ADR 0001](adr/0001-product-positioning.md)）
- 示例与现状：[README.md](../README.md)

这组文档回答的是“Kairos 承诺什么、边界在哪、什么算破坏性变更”，不是功能清单，也不是实现计划。实现进度仍以 plan-1.0 和仓库代码为准；文档里的“当前”指写作时点（2026-10-31），M1 底座在同期落地，涉及构建的现状以 [ADR 0004](adr/0004-gradle-plugin-artifact-variant.md) 与 [support-matrix.md](contracts/support-matrix.md) 的时点说明为准。

## 阅读顺序

1. 先看 [ADR 0001 产品定位](adr/0001-product-positioning.md)：目标用户、承诺边界、与 `direction.md` 的关系；
2. 再看 [ADR 0002 样式条件模型](adr/0002-style-condition-model.md)：三档、条件身份、组合代数；
3. 然后看 [ADR 0003 类名/manifest 契约](adr/0003-classname-manifest-contract.md)：确定性、版本、路径；
4. 构建相关看 [ADR 0004 Gradle 插件与 artifact variant](adr/0004-gradle-plugin-artifact-variant.md)；
5. 运行时/SSR 相关看 [ADR 0005 SSR 增强模型](adr/0005-ssr-enhancement-model.md)；
6. 查具体 API 与支持范围看 `contracts/` 下四份契约。

## 文件索引

| 文件 | 内容 | 何时读 |
|---|---|---|
| [adr/0001-product-positioning.md](adr/0001-product-positioning.md) | 定位、用户、阶段、承诺边界 | 判断一个需求该不该做 |
| [adr/0002-style-condition-model.md](adr/0002-style-condition-model.md) | 三档降级、伪类/media/container/supports 身份、轴模型 | 改样式语义或加条件 API |
| [adr/0003-classname-manifest-contract.md](adr/0003-classname-manifest-contract.md) | 类名算法 v1、manifest v3、确定性、升级规则 | 改类名、manifest、merge/prune |
| [adr/0004-gradle-plugin-artifact-variant.md](adr/0004-gradle-plugin-artifact-variant.md) | 插件注入、每 compilation 输出、库样式资产 | 改构建、发布、多模块 |
| [adr/0005-ssr-enhancement-model.md](adr/0005-ssr-enhancement-model.md) | `data-kairos-id`、mount、htmx swap、DOM 一致性 | 改浏览器绑定或 SSR 契约 |
| [contracts/public-api.md](contracts/public-api.md) | 按模块的 API 清单与稳定性 | 引用任何 API 前 |
| [contracts/support-matrix.md](contracts/support-matrix.md) | 实测版本、承诺范围、不支持的组合 | 报兼容性/安装问题 |
| [contracts/non-goals.md](contracts/non-goals.md) | 不做清单、需求归类规则 | 评估新功能 |
| [status.md](status.md) | 对外展示的项目状态与已知限制 |
| [contracts/api-freeze.md](contracts/api-freeze.md) | API freeze 清单与变更规则 |
| [guides/migration.md](guides/migration.md) | 跨版本迁移指南 |
| [guides/quickstart.md](guides/quickstart.md) | 5 分钟快速开始 |
| [guides/concepts.md](guides/concepts.md) | 编译期折叠、三档、条件、确定性 |
| [guides/tutorial-ktor-htmx.md](guides/tutorial-ktor-htmx.md) | Ktor + htmx CRUD 教程 |
| [guides/how-to.md](guides/how-to.md) | 常见任务与排障 |
| [contracts/governance.md](contracts/governance.md) | 变更分级、RFC/ADR、弃用窗口、发布节奏 |
| [contracts/release.md](contracts/release.md) | 发布清单：坐标、步骤、门禁、已知限制 |
| [contracts/security.md](contracts/security.md) | 安全基线：转义、URL 白名单、RawHtml、CSP、CSRF |
| [contracts/errors.md](contracts/errors.md) | 错误码表：构建期错误的稳定标识与修复方向 |
| [contracts/versioning.md](contracts/versioning.md) | SemVer、弃用窗口、manifest/算法版本 | 发版或做破坏性变更 |

## 已知的上游冲突（都已在对应文档记录）

| 冲突 | 记录位置 |
|---|---|
| `direction.md` 仍是“只服务作者、不做生态”，与 plan-1.0 的生态目标冲突 | [ADR 0001](adr/0001-product-positioning.md) §5 |
| `kairos-compiler-plugin` 仍把 `kairos-core` 打包进插件 jar，与“拆出样式契约层”的目标冲突 | [ADR 0004](adr/0004-gradle-plugin-artifact-variant.md) §3 |
| `kairos-core` 包含 Markdown，与“Markdown 不进核心”的目标冲突 | [non-goals.md](contracts/non-goals.md) §8 |
| `merge` 只产出 CSS、不产出合并 manifest | [ADR 0003](adr/0003-classname-manifest-contract.md) §6 |
| `strict` 选项已接线但尚未在折叠失败路径生效 | [public-api.md](contracts/public-api.md) §8.2 |

## 维护规则

- 本目录只描述契约；实现计划写在 `.lignting/kairos/plan-1.0.md`，不要在两处重复维护同一张路线图；
- 已有决策引用 `direction.md` 的对应章节，不整段复制；
- ADR 只追加、不覆盖历史；改变已接受决策时新增 ADR 并在旧 ADR 标注“被替代”；
- 修改任何 `stable` API、类名算法、manifest 字段或支持矩阵，必须同时更新 [public-api.md](contracts/public-api.md)、[versioning.md](contracts/versioning.md) 与 [support-matrix.md](contracts/support-matrix.md)；
- 提交前用脚本检查所有相对链接存在，并搜索占位标记（本目录不应出现未完成内容）。
