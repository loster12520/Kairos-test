# 执行报告 · kairos · Phase 1 · R1

> 层级：权杖
> 核心目的：把编译期折叠从"在 IR 里看得到"推进到"真的替换掉"——这是整套方向的第一次真实往返，失败则方向要改
> 日期：2026-10-29

## 1. 做了什么

| 文件 | 改了什么 |
|---|---|
| `kairos-runtime/src/commonMain/kotlin/dev/kairos/runtime/Style.kt` | 新增样式原语与声明模型：`Declaration`、`StyleScope.declaration(property, value)`、`css { }` 的运行时降级实现、类名算法 `classNameFor`（FNV-1a 64，取 8 位十六进制，前缀 `c`）、运行时登记表 `KairosRuntimeRegistry` |
| `kairos-compiler-plugin/src/main/kotlin/dev/kairos/compiler/KairosPluginRegistrar.kt` | 插件入口：`CompilerPluginRegistrar`（`supportsK2 = true`）、`CommandLineProcessor`（选项 `outputDir`）、在 `registerDisposable` 里落盘（IR 扩展会跑多次，产物只写一次） |
| `kairos-compiler-plugin/src/main/kotlin/dev/kairos/compiler/KairosIrGenerationExtension.kt` | 折叠逻辑：识别 `css { }` 调用 → 取 lambda 体 → 按源码顺序读取 `declaration(prop, value)` 的字符串字面量 → 替换为常量类名字符串；任何留在可判定子集外的形状都记入报告并原样保留（静默降级） |
| `kairos-compiler-plugin/src/main/kotlin/dev/kairos/compiler/KairosCollector.kt` | 规则累积与产物写出：按类名排序去重、写出 `kairos.css`（全部包在 `@layer kairos` 内）、`kairos.map.json`（v1 字段冻结）、`kairos-report.txt`（折叠与降级诊断） |
| `kairos-compiler-plugin/build.gradle.kts` | 把运行时的 JVM 类**打进插件 jar**（编译器加载插件时没有 classpath），使插件与运行时共用同一份类名算法 |
| `demo/src/commonMain/kotlin/Page.kt` | 三个样例：可折叠的 `heroClass`、枚举驱动的 `themeClass`、以及捕获运行时值的 `runtimeColorClass`（降级对照） |
| `demo/src/jsMain/kotlin/Main.kt`、`demo/src/jvmMain/kotlin/JvmMain.kt` | JS 侧走折叠路径并打印运行时登记数；JVM 侧**故意不挂插件**，作为运行时降级参照 |
| `demo/build.gradle.kts` | Kotlin/JS 目标挂 `-Xplugin` + `-P plugin:dev.kairos:outputDir=…`；新增验证入口 `runJvmReference`（JavaExec）与 `runJsTierOne`（node） |
| `tools/verify.mjs` | 验收脚本：7 条检查，含一份**第三方重新实现的类名算法**用于交叉校验，以及两次干净构建的确定性比对 |
| `settings.gradle.kts` / `gradle.properties` | 三模块工程（runtime / compiler-plugin / demo）；`kotlin.compiler.execution.strategy=in-process` 便于插件热改 |

## 2. 验证结果

| 验收条目 | 命令 | 结果 |
|---|---|---|
| A1a 折叠后运行时登记表为空 | `node tools/verify.mjs` | **通过** — `folds=0` |
| A1b 折叠后的类名是产物中的字面量 | `node tools/verify.mjs` | **通过** — `hero=cde58b731 c644b24a3`，且 bundle 中出现 `'cde58b731 c644b24a3'` |
| A2 类名可由声明复算（第三方实现） | `node tools/verify.mjs` | **通过** — 4 条规则逐条复算一致 |
| A3 两次干净构建逐字节一致 | `node tools/verify.mjs --rebuild` | **通过** — css `871bd33a365226f6…`、map `6b9d08451d200225…` 两次相同 |
| A4a 不可折叠块产出相同类名 | `node tools/verify.mjs` | **通过** — `downgrade=cde58b731`，与折叠规则同名 |
| A4b 降级只增加登记、不新增 CSS | `node tools/verify.mjs` | **通过** — `registry=1`，CSS 规则数不变 |
| A5 CSS 与 map 一致且在 kairos 层内 | `node tools/verify.mjs` | **通过** — 4 条规则、`@layer kairos` 包裹、按类名排序 |
| 折叠版与降级版逐字符相同（端到端） | `gradle :demo:runJvmReference :demo:runJsTierOne` | **通过** — JVM 输出 `hero=cde58b731 c644b24a3 / theme=c156fdd4d`，JS 折叠输出完全相同；JS 追加 `folds=0` / `downgrade=cde58b731` / `foldsAfterDowngrade=1` |

产物 JS 的关键片段（`demo/build/compileSync/js/main/productionExecutable/kotlin/kairos-demo.js`）：

```js
function heroClass() {
  return 'cde58b731 c644b24a3';
}
function themeClass(theme) {
  var tmp;
  switch (theme.o_1) {
    case 0: tmp = 'c6070d608'; break;
    case 1: tmp = 'c156fdd4d'; break;
    default: noWhenBranchMatchedException(); break;
  }
  return tmp;
}
```

枚举分支仍然存在（那是作者自己写的 `when`），但**原语没有引入任何额外的样式计算**：每个分支只是一个字符串字面量。

## 3. 与计划的偏离

| # | 计划 | 实际 | 理由 |
|---|---|---|---|
| 1 | S1：插件以 JVM 依赖方式引用运行时库，共享类名算法 | 改为把运行时的 JVM 类**打包进插件 jar** | 编译器加载插件时没有 classpath，普通依赖在插件运行时不可见；不打包就只能复制一份算法，直接违反"同一份实现"的契约 |
| 2 | S2：为声明提取器补独立单元测试 | 未写独立单测，改由端到端验收覆盖 | 提取器的输入是 IR，脱离编译器构造 IR 的成本高于收益；实际由 A1/A2/A4/A5 四条端到端检查覆盖它的每条分支（可折叠 / 不可折叠） |
| 3 | S5：用 Node 比对 JS 折叠版与 JVM 降级版 | JS 侧改为在验收脚本里做**第三方算法复算**，JVM↔JS 的逐字符比对保留为两个 Gradle 任务的输出比对 | 复算能同时证明"跨语言一致"，且不依赖 JVM 环境启动；两种都在做，只是自动化的那一层换了实现 |
| 4 | S4：删除输出目录后连续两次构建 | 需额外加 `--rerun-tasks` 才能触发插件重跑 | 插件在编译期写的产物**不是 Gradle 已知的任务输出**，删除目录不会让编译任务失效（已记为影响后续轮次的问题） |
| 5 | 计划未提及 | 新增 `kairos-report.txt` 诊断产物 | IR 形状与预期不符时（本轮遇到两处），没有诊断就只能靠猜；该产物在第 3 次迭代里直接定位了"实参列表带隐式接收者"的问题 |

## 4. 影响后续轮次的问题

1. **插件产物不是 Gradle 任务输出**（最重要）：删掉 `demo/build/kairos` 不会让编译任务失效，第二次构建会直接 UP-TO-DATE，插件不跑、CSS 不更新。P3 做开发体验时必须先修这一条，否则会出现"改了样式但页面不变"的静默过期——而这正是契约 4（产物确定性）最容易被误判为"不确定"的场景。
2. **IR 的实参列表带隐式接收者**：`IrCall.arguments` 对 `declaration(property, value)` 返回 3 个元素（首个是 dispatch receiver）。当前用"取最后两个"规避，属于对编译器实现细节的依赖；若 Kotlin 升级后形状变化，应改用具名符号比对（`pluginContext.referenceFunctions`）。已记入 `kairos-report.txt`。
3. **类名哈希空间**：8 位十六进制 = 32 bit。按生日界，一万条规则时碰撞概率约 1%；本轮已有碰撞检测（同哈希不同声明会写入报告），但在 P4 跨模块聚合前应把类名扩到 12 位或改用声明序号，否则"跨模块无重复无遗漏"这条验收不可靠。
4. **样式 API 的 DX 过低**：`css { declaration("color", "#111111") }` 是刻意的 R1 形态（保证 IR 形状可判定）。P2 必须补类型化 helper，并解决"helper 名 → CSS 属性名"的映射来源问题（要么注解 + 插件读注解，要么让 helper 保持 `inline` 由插件展开其 body）。
5. **Kotlin daemon 会缓存插件 classpath**：本轮用 `-P kotlin.compiler.execution.strategy=in-process` 规避；对外发布时需要给出 Gradle 侧的正式接入方式（`KotlinCompilerPluginSupportPlugin`），否则用户会遇到"改了插件不生效"。
6. **`@layer kairos` 是占位**：当前所有规则进同一个层。Q9 的两段式（类别全序 + 同层编译期消解）尚未落地，P2 要把层名从单层拆成类别层，届时要检查 layer 顺序是否影响已产出的确定性哈希（应该不影响，但需要一次回归）。
