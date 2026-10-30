# 执行报告 · kairos · Phase 3 · R1

> 层级：权杖
> 核心目的：让用户封装不损失优化，让"为什么"可以反查
> 日期：2026-10-29

## 1. 做了什么

| 文件 | 改动 |
|---|---|
| kairos-cli/Main.kt | 新增 why / report / merge / prune 四个命令；why 打印源码行与 caret，并双向显示级联胜负 |
| kairos-compiler-plugin | 映射写入 line/column；dev 模式类名后缀；捕获失败噪音收敛 |
| kairos-runtime | devClassNameFor（源码位置可读后缀） |
| demo Site.kt | 用户自写 inline 封装 calloutBorder()，用于验证零残留 |
| demo build.gradle.kts | -PkairosDev 开关 |

## 2. 验证结果

| 条目 | 命令 | 结果 |
|---|---|---|
| A18 用户封装零残留 | node tools/verify.mjs | 通过 — 降级仅 1 条刻意 fixture |
| A19 反查到源码行 | kairos why ko8805002a | 通过 — 输出 Site.kt:129:25 与源码行 |
| A20 级联胜负 | kairos why kbd936856c | 通过 — loses to ko8805002a (kairos.override) for border |
| A21 dev 类名 | -PkairosDev=true 构建 | 待验证（见影响后续） |

## 3. 与计划的偏离

| # | 计划 | 实际 | 理由 |
|---|---|---|---|
| 1 | A21 本应在本轮完成 | dev 开关已接线，但本轮未跑 dev 构建验证 | 时间用于跨模块（P4）；记为遗留项 |
| 2 | 捕获失败原本静默 | 先加了噪音日志，后收敛为结构化过滤器 | 噪音会淹没真问题，D23 的静默指的是不打扰用户，不是不留证据 |

## 4. 影响后续轮次的问题

1. A21（dev 类名）未实跑验证，属未完成项，需在下一轮补一次 dev 构建并检查类名。
2. 反查工具只覆盖单个模块的映射；跨模块后需要 merge 后再 why，当前 CLI 已能读任意映射，但没有"多映射合并查询"。

## 补记（P4 收尾时补做）

A21（dev 类名）已实跑验证：

```
gradle :demo:compileProductionExecutableKotlinJs -PkairosDev=true --rerun-tasks
  .kr8e059234_Site_L61 { margin: 0; }
  .kb08b6c88c_Site_L79 { align-items: center; }
  .kb15b4c8c5_Site_L267 { padding: 24px; }
```

类名后缀同时给出文件与行号，devtools 里可直接读、也可反 grep 回源码；生产构建仍使用内容寻址名字（确定性验收只跑生产构建）。

## 补记（审查后撤除 dev 类名，2026-10-29）

审查发现：dev 模式把源码位置编进类名后，**JVM 渲染路径（无插件）仍用生产名**，
于是 `index.html` 上的类名与 `kairos.css` 里的类名对不上，整个站点错位。

这不是实现 bug，而是设计缺陷：类名一旦依赖"编译期是否运行插件"，就不可能在两条路径上一致。

处理：**撤除 dev 类名**（连带撤除 `-PkairosDev` 开关与 `devClassNameFor`）。
"从一个类名找到源码"这个需求由映射（文件:行:列）+ `kairos why` 满足，且不破坏不变量。
类名长度同时从 32 bit 扩到 48 bit，回应碰撞风险。
