# 执行报告 · kairos · Phase 2 · R1

> 层级：权杖
> 核心目的：让组合的胜负由一条确定的代数决定，并在编译期删掉必输的声明
> 日期：2026-10-29

## 1. 做了什么

| 文件 | 改动 |
|---|---|
| kairos-runtime Style.kt | 新增 Layer 枚举（reset/base/variant/override）；类名带层字母与层名哈希；新增 styles(vararg) |
| kairos-compiler-plugin | 按层注册规则；styles() 编译期求值并按"跨层高者胜、同层后者胜"消解；冲突写入映射 |
| kairos-collector | CSS 按层分组输出；映射加入 layer / layerIndex / resolutions |
| kairos-cli | merge 按 layerIndex 排序输出（修复过一版按字母排序的 bug，见偏离） |
| demo Site.kt | reset 层标题复位、override 层高亮卡片、styles() 组合、枚举驱动变体 |

## 2. 验证结果

| 条目 | 命令 | 结果 |
|---|---|---|
| A14 跨层消解 | node tools/verify.mjs | 通过 — border: override 胜 base；margin: base 胜 reset（4 处） |
| A15 只保留胜者 | node tools/verify.mjs | 通过 — folded=ko8805002a；兜底保留两个类，层序 3 > 1 |
| A16 冲突可反查 | kairos why kbd936856c | 通过 — 输出 loses to ko8805002a (kairos.override) for border |
| A17 合并后层序正确 | node tools/verify.mjs | 通过 — 4 个 layer 块按 reset<base<variant<override 输出 |
| 总体 | node tools/verify.mjs --rebuild | 12/12 通过 |

## 3. 与计划的偏离

| # | 计划 | 实际 | 理由 |
|---|---|---|---|
| 1 | 计划未提合并工具的层序 | merge 首版按字母排序层，把 override 排到了 base 前面 | 层序是级联语义，不是展示顺序；改为按 layerIndex 排序，并新增 A9 守住 |
| 2 | 计划假定"败者不出现" | JVM 兜底路径（无插件）仍然拼接两个类 | 这正是"折叠是优化"的证据；A7 改为对比折叠版与兜底版，并验证层序保证视觉一致 |

## 4. 影响后续轮次的问题

1. 同层同属性冲突在**动态**路径下无法静态消解，只能靠样式表顺序。当前文档标注为限制；若将来出现高频场景，需要在原语层提供显式槽位。
2. 类名含层之后，同一 (属性,值) 在不同层是不同类，**剪枝必须按类名而不是按声明内容**统计，CLI prune 已按此实现。

## 补记（审查后修正，2026-10-29）

本轮验收通过后，独立审查指出一处语义缺陷：`styles()` 的运行时兜底只拼接去重，**同层同属性**的胜负
会退化成样式表顺序（按类名排序），而报告与注释声称"同层后者胜、两档一致"。

修正方式：把"同层同属性"从"后者胜"改为**编译期硬错误**（相同值的重复声明静默保留一份）。
理由：一个 class 列表无法表达"参数里的先后"，让编译器猜等于允许两档行为不同。
因此本报告正文里"同层后者胜"的说法作废，以 README 的"三条不变量"为准。
