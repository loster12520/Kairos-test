# Kairos 发布契约

- 状态：M6 发布清单 v1（2026-10-31）
- 相关：[versioning.md](versioning.md)、[support-matrix.md](support-matrix.md)、[public-api.md](public-api.md)

## 1. 版本与坐标

- `group = "dev.kairos"`，当前 `version = "0.1.0"`；1.0 之前按 `0.x` 语义，1.0 之后按 [versioning.md](versioning.md)。
- 发布构件：

| 坐标 | 类型 | 说明 |
|---|---|---|
| `dev.kairos:kairos-core` | KMP（jvm + js） | 样式 DSL、节点模型、序列化、响应式、Markdown |
| `dev.kairos:kairos-browser` | KMP（js） | DOM 绑定、挂载、keyed list |
| `dev.kairos:kairos-htmx` | JVM | `page()` / `fragment()` / htmx 属性 |
| `dev.kairos:kairos-cli` | JVM | `why/report/merge/prune/init/check/doctor/coverage` |
| `dev.kairos:kairos-compiler-plugin` | JVM | K2 编译器插件（由 Gradle 插件按坐标解析） |
| `dev.kairos:kairos-gradle-plugin` | Gradle 插件 | `id("dev.kairos")`，同时发布 plugin marker |
| `dev.kairos:kairos-bom` | platform | 统一版本约束 |

## 2. 发布步骤

1. `./gradlew -PframeworkOnly=true publishToMavenLocal`（示例与 clean-room consumer 的前提）。
2. 跑完整门禁（见 §3），全部通过才进入下一步。
3. 本地仓库验证：`./gradlew -PframeworkOnly=true publishAllPublicationsToLocalBuildRepository`，检查 `build/repo` 中有 jar/pom/module/plugin marker。
4. Maven Central / Gradle Plugin Portal：**需要账号、签名密钥与 Sonatype 命名空间**，当前环境无法执行。配置好 `maven-publish` 的仓库与签名后，命令与第 3 步相同，只是目标仓库不同。

## 3. 发布门禁

| 检查 | 命令 | 当前结果 |
|---|---|---|
| 产物级 | `node tools/verify.mjs --rebuild` | 17/17 |
| 浏览器级 | `node tools/browser-check.mjs` | 9/9 |
| 条件样式（真实浏览器） | `node tools/style-browser-check.mjs` | 5/5 |
| 运行时（keyed list） | `node tools/runtime-check.mjs` | 3/3 |
| clean-room consumer | `node tools/consumer-check.mjs` | 5/5 |
| 组件库二进制消费 | `node tools/library-check.mjs` | 5/5 |
| starter 生成与构建 | `node tools/starter-check.mjs` | 5/5 |
| 文档站（自举） | `node tools/docs-check.mjs` | 5/5 |
| 端到端（博客） | `bash tools/e2e-ci.sh` | 20/20 |
| 可访问性（axe） | `node tools/a11y-check.mjs` | 0 serious/critical |
| 级联契约（第三方 CSS） | `node tools/cascade-check.mjs` | 5/5 |
| 跨浏览器 | `node tools/cross-browser-check.mjs` | Chromium + Firefox 通过（本机）；WebKit 在 CI job |
| 跨目录确定性 | `node tools/repro-check.mjs` | 3/3 |
| 体积/时间预算 | `node tools/benchmark.mjs` | 通过 |
| 二进制兼容 | `./gradlew apiCheck` | 通过 |
| 单元测试 | `./gradlew :kairos-core:jvmTest :kairos-gradle-plugin:test` | 通过 |

## 4. 发布说明与文档

- API freeze 以 `apiValidation` 的 dump 为准：`api/<module>.api` 变更必须随 release notes 一起提交。
- `@ExperimentalKairosApi` 标注的 API 不承诺二进制兼容，但必须在 release notes 里列出变更。
- 每个公开构件必须带 sources jar（已在 M1 配置）。
- 文档站由 `docs-site` 生成，发布时把 `docs-site/build/site` 部署到静态托管即可。

## 5. 已知环境限制

- Maven Central / Plugin Portal 正式发布未执行（无凭证）；本地仓库发布已验证等价产物。
- 跨机器确定性只在本机两处目录验证（`repro-check`），CI 上可扩展到不同 OS。
- Firefox/WebKit 验收需 CI 环境（本机缺系统库）。
