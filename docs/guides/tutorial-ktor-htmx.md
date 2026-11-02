# 教程：Ktor + htmx CRUD

目标：用 Kairos 做一个服务端渲染的增删改查页面，交互由 htmx 负责，样式在编译期生成。

## 1. 生成骨架

```bash
./gradlew -q :kairos-cli:run --args='init blog --template ktor-htmx'
cd blog
```

工程里已经接好 `dev.kairos` 插件、`kairos-core` / `kairos-htmx` 依赖和 Ktor。

## 2. 数据与路由

内存列表足够演示：

```kotlin
data class Item(val id: Long, val title: String)
val items = mutableListOf(Item(1, "first"))

fun Application.routes() = routing {
    get("/") { call.respondText(indexPage(items), ContentType.Text.Html) }
    post("/items") {
        val form = call.receiveParameters()
        items += Item(items.size + 1L, form["title"].orEmpty())
        call.respondText(fragment(itemList(items)), ContentType.Text.Html)
    }
}
```

## 3. 页面与片段共用模板

```kotlin
fun indexPage(items: List<Item>): String {
    val body = div {
        h1 { style = titleStyle; "Items".text }
        div { id = "items"; add(itemListElement(items)) }
        form {
            hxPost = "/items"
            hxTarget = "#items"
            hxSwap = "innerHTML"
            input { name = "title"; style = inputStyle }
            button { type = "submit"; style = buttonStyle; "Add".text }
        }
    }
    return page("Items", listOf("/kairos.css"), body)
}

fun itemList(items: List<Item>): String = fragment(itemListElement(items))
```

片段与整页复用同一棵节点树，这是 Kairos 的核心用法：**一个模板，两种输出**。

## 4. 条件样式与表单状态

```kotlin
val buttonStyle = css {
    backgroundColor("#2563eb")
    color("#ffffff")
    hover { backgroundColor("#1d4ed8") }
    disabled { opacity("0.6") }
}
```

服务端校验失败时返回带错误的表单片段（保留用户输入），htmx 直接替换，页面不跳转。

## 5. 渐进增强

- 表单有 `action` / `method`，JS 不可用时仍能提交
- htmx 可用时只替换片段
- 键盘路径与 `aria-live` 见博客示例的 `#comments` 与 `focusAfterSwap`

## 6. 验收

```bash
node tools/e2e.mjs   # 本仓库的博客示例跑的就是这套模式
```
