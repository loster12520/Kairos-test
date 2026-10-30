# 执行报告 · kairos · Phase 1 · R3

> 层级：权杖
> 核心目的：把"能折叠"升级为"写得像正常代码也能折叠"
> 日期：2026-10-29

## 1. 做了什么

| 文件 | 改动 |
|---|---|
| kairos-runtime Style.kt | 类型化 helper（27 个，全部 inline）、Spacing / Radius 常量刻度、Int.px / Int.pct 单位简写 |
| kairos-compiler-plugin KairosEval.kt | 新增求值模型：Eval.Lit / Eval.Choice、参数绑定、不可折叠异常 |
| kairos-compiler-plugin KairosIrGenerationExtension.kt | 用抽象解释器重写：块体解释、inline 展开、const val 读取、字符串拼接、条件值物化 |
| kairos-compiler-plugin KairosCollector.kt | 报告与映射加入行列号、降级清单、冲突清单 |

## 2. 验证结果

| 条目 | 命令 | 结果 |
|---|---|---|
| A11 helper 全部折叠 | node tools/verify.mjs | 通过 — 降级仅剩 1 条刻意 fixture |
| A12 条件值产出类名常量 | 产物 JS 检查 | 通过 — density.equals(...) ? 'kb816c2615' : 'kb5ada92a4' |
| A13 降级准确归因 | kairos-report.txt | 通过 — Site.kt:274:48 - value is not decidable |
| 规则规模 | 报告 | 47 条原子规则（R1 时为 4 条） |

## 3. 与计划的偏离

| # | 计划 | 实际 | 理由 |
|---|---|---|---|
| 1 | 用 inline 展开解析框架 helper | 改为命名规则（camelCase 转 kebab-case），inline 展开只用于用户自写封装 | 跨模块 inline body 在 IR 中不可见；命名规则零维护 |
| 2 | 条件值直接搬移或重写条件表达式 | 多分支 when 的 IR 包在带临时变量的 block 里，只搬内层会导致后端崩溃 | 整体保留 block，只替换分支结果 |
| 3 | 计划未提 | declaration 的实参列表包含隐式接收者 | 按参数表定位而非固定下标 |

## 4. 影响后续轮次的问题

1. 条件值不能跨 scope 搬移——P2 设计变体原语时必须假定条件表达式只能原地改写。
2. 框架 helper 靠命名规则：将来出现驼峰无法表达的属性（伪类、@supports）需要新的显式原语。
3. 捕获失败没有用户可见反馈——这是 D23 的既定选择，P3 的反查工具是唯一出口。
