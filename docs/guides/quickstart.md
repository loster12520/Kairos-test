# 快速开始

从空目录到一个可部署的静态页，5 分钟。

## 1. 生成工程

```bash
./gradlew -PframeworkOnly=true publishToMavenLocal   # 本仓库开发时先发布框架
./gradlew -q :kairos-cli:run --args='init my-app --template static'
cd my-app
```

`kairos init` 支持三个模板：

| 模板 | 用途 |
|---|---|
| `static` | 静态页面：`gradle assembleSite` 产出 `build/site` |
| `ktor-htmx` | Ktor 服务端渲染 + htmx 片段的骨架 |
| `ktor-incremental` | 已有 Ktor 应用只把一个路由接到 Kairos |

## 2. 写页面

`src/main/kotlin/Main.kt` 里的样式块会在编译期折叠成类名：

```kotlin
private val titleStyle: String = css { fontSize("32px") }

val body = div {
    h1 { style = titleStyle; "Hello, Kairos".text }
}
```

## 3. 构建

```bash
gradle assembleSite
```

产物：

```text
build/site/index.html
build/site/kairos.css
build/site/kairos.map.json
```

打开 `index.html` 即可。页面不依赖任何客户端框架，样式是纯 CSS。

## 4. 下一步

- 条件样式（hover / media / container / supports）→ [概念](concepts.md)
- 服务端渲染与 htmx → [教程](tutorial-ktor-htmx.md)
- 常见任务与排障 → [How-to](how-to.md)
