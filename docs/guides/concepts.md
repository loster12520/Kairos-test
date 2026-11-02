# 核心概念

Kairos 只有四个需要记住的概念：**编译期折叠**、**三档优化**、**条件作用域**、**确定性产物**。

## 1. 编译期折叠

`css { ... }` 是一个真函数，但编译器插件会把它替换成类名常量，并把声明写进样式表：

```kotlin
val card = css {                         // 产物：val card = "kb3f78..."
    backgroundColor("#ffffff")
    borderRadius("14px")
    padding("18px")
}
```

浏览器拿到的 JS 里没有 `backgroundColor` 调用，只有类名字符串。

## 2. 三档优化（值从哪来）

| 档 | 值取决于 | 产物 | 运行时开销 |
|---|---|---|---|
| ① | 可穷举状态（enum 模式轴） | 每个状态一个静态类 | 零 |
| ② | 不可穷举输入，但分支值是常量 | 两个类都在样式表里，运行时只换类名 | 一次 class 写 |
| ③ | 运行时才算得出的值 | CSS 自定义属性 | 一次属性写 |

三档之间**只有优化程度差异，没有行为差异**，这是"静默降级"合法的唯一依据。

块内 `if`/`when` 走档 ②，采用**按属性拆分的轴模型**：k 个独立二元条件产生 `1 + 2k` 个类，而不是 `2^k` 个组合。

## 3. 条件作用域（声明何时生效）

伪类、伪元素、media、container、`@supports` 是独立维度，条件进入类名身份：

```kotlin
val button = css {
    backgroundColor("#ffffff")
    hover { backgroundColor("#f2f3f7") }
    media("(max-width: 640px)") { padding("6px 10px") }
}
```

条件组各自一个类；`css { }` 返回空格分隔的类名列表。

## 4. 级联与确定性

- 层序固定：`kairos.reset < kairos.base < kairos.variant < kairos.override`，优先级只由层决定
- 同层同属性（或 shorthand/longhand 重叠）是编译错误，不靠书写顺序
- 同一份源码在不同目录构建，CSS / manifest / report 逐字节一致（`tools/repro-check.mjs`）

## 5. 运行时模型

- 状态：`signal` / `computed` / `effect`，`Owner` 负责释放
- DOM：`data-kairos-mount` 声明行为，`autoMount()` 在 htmx swap 后重扫
- 列表：`bindKeyedChildren` 按 key 复用节点
