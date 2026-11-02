# How-to

## 切换主题

颜色走 CSS 变量，切换只改 `<html>` 的一个类：

```kotlin
val darkTheme = "dark"
bindClassName(document.documentElement!!, dark) { if (it) darkTheme else "" }
```

变量定义在 `theme.css`，Kairos 规则里写 `var(--bg)`。主题切换属于档 ③（变量通道），不是类名穷举。

## 加一个可复用组件

组件就是返回节点的普通函数，props 是参数，slot 也是参数：

```kotlin
fun card(title: String, content: Node): Node = div {
    style = cardStyle
    h3 { style = titleStyle; title.text }
    add(content)
}

card("Stats") { span { "42".text } }   // 或 card("Stats", group { ... })
```

`group { }` 返回 `FragmentNode`，用于贡献多个兄弟节点而不加包裹元素。

## 多模块与组件库

1. 库模块应用 `dev.kairos`，正常 `maven-publish`；
2. 消费方只加坐标依赖；
3. 消费方插件自动收集 jar 里的 `META-INF/kairos/**` 并合并：`./gradlew kairosMergeStyles`。

## 检查产物

```bash
./gradlew -q :kairos-cli:run --args='check --map build/site/kairos.map.json --css build/site/kairos.css'
./gradlew -q :kairos-cli:run --args='coverage --map build/site/kairos.map.json'
./gradlew -q :kairos-cli:run --args='why kb3f78... --map build/site/kairos.map.json'
```

## 排障

| 现象 | 先看 |
|---|---|
| 样式没生效 | `kairos check` 是否通过；类名是否在 CSS 里 |
| 构建报 `KAIROS-STYLE-*` | [错误码](../contracts/errors.md) |
| htmx 片段替换后行为丢失 | 元素是否有 `data-kairos-mount`；是否调用了 `autoMount()` |
| 列表更新后焦点丢失 | 用 `bindKeyedChildren` 而不是全量重建 |
| 样式改动没进产物 | 先 `publishToMavenLocal`；示例工程加 `--refresh-dependencies` |

## 部署

静态模板：`gradle assembleSite` 后把 `build/site` 整个目录部署到任意静态托管。
Ktor 模板：确保 `/kairos.css` 路由能读到 `build/kairos/jvm/main/kairos.css`，并下发 CSP / `nosniff` 头（见 `docs/contracts/security.md`）。
