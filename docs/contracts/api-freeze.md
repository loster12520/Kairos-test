# API freeze 清单

- 状态：M6 冻结候选（2026-10-31，0.1.0）
- 相关：[public-api.md](public-api.md)、[versioning.md](versioning.md)、[release.md](release.md)

## 1. 冻结范围

`api/<module>.api` 是机器可校验的冻结面，由 `binary-compatibility-validator` 生成：

```bash
./gradlew apiDump    # 有意变更公共签名后更新
./gradlew apiCheck   # 门禁：签名与 dump 不一致即失败
```

| 模块 | dump | 说明 |
|---|---|---|
| `kairos-core` | `kairos-core/api/kairos-core.api` | 样式 DSL、节点模型、响应式、Markdown |
| `kairos-browser` | — | KMP JS 目标，dump 覆盖 jvm 面；浏览器 API 以 public-api.md 为准 |
| `kairos-htmx` | `kairos-htmx/api/kairos-htmx.api` | `page` / `fragment` / htmx 属性 |
| `kairos-cli` | `kairos-cli/api/kairos-cli.api` | 只暴露 `main`（模型类已 internal） |
| `kairos-compiler-plugin` | `kairos-compiler-plugin/api/kairos-compiler-plugin.api` | 编译器插件入口类 |
| `kairos-gradle-plugin` | `kairos-gradle-plugin/api/kairos-gradle-plugin.api` | `KairosExtension` / `KairosGradlePlugin` |

## 2. stable / experimental 划分

- `stable`：`Declaration`、`Layer`、`StyleScope` 及 typed helpers、`css`、`styles`、`StyleCondition`、条件 DSL、`Node`/`ElementNode`/`TextNode`/`RawHtml`/`FragmentNode`、`renderToHtml`/`renderFragment`、`Signal`/`effect`/`Owner`/`Batching`/`Resource`、`bindText`/`bindClassName`/`bindAttribute`/`bindKeyedChildren`、`onMount`/`mount`/`autoMount`/`installModal`、`page`/`fragment`（htmx）、Gradle 插件 id 与 `KairosExtension` 字段。
- `@ExperimentalKairosApi`：`classNameFor`、`StyleRule`、`MarkdownStyles`。它们可以被使用，但形态可在次版本调整。
- `internal`：CLI 模型、编译器插件实现类、`KairosRuntimeRegistry`（文档已注明非公共 API）。

## 3. 冻结规则

1. 公共签名变化必须先改代码、再 `apiDump`、再把 dump 与 release notes 一起提交。
2. 破坏性变化（删除/改名/改语义）必须升主版本，并提供迁移说明（见 [migration.md](../guides/migration.md)）。
3. 类名算法、manifest schema 属于"样式契约"，变更走 [ADR 0003](../adr/0003-classname-manifest-contract.md) 的版本规则，不受普通弃用窗口保护。
4. experimental API 变化不要求主版本，但必须在 release notes 列出。

## 4. 1.0 前待复核

- [x] 逐个确认 `public-api.md` 中 `stable` 条目没有"实现细节泄漏"：CLI 模型已 internal；编译器插件实现类只经 Gradle 插件按坐标加载；`KairosRuntimeRegistry` 文档标注非 API。
- [x] 确认 KMP 的 js 目标导出与文档一致：`kairos-core` / `kairos-browser` 为 KMP，js 产物随模块发布；浏览器 API 已列在 public-api.md。
- [x] 确认 Gradle 插件对支持矩阵内的版本能注入：Kotlin 2.2.20 + Gradle 9.7.1 为当前实测组合，其余组合在 CI 支持矩阵任务中验证（`support-matrix.md`）。
- [x] 确认 `@ExperimentalKairosApi` 覆盖所有未定型的 API：`classNameFor`、`StyleRule`、`MarkdownStyles` 已标注。
- [ ] 确认 deprecated 条目有替代品与移除版本：当前 0.1.0 没有 deprecated API；1.0 发布时补弃用注解与移除版本。
