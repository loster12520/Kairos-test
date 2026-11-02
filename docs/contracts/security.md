# Kairos 安全契约

- 状态：M4 安全基线 v1（2026-10-31）
- 相关：[public-api.md](public-api.md)、[ADR 0002](../adr/0002-style-condition-model.md)、[errors.md](errors.md)

Kairos 的默认姿态是"不可信输入默认降级，可信标记必须显式"。下面每一条都有对应的单测或端到端验收。

## 1. 输出转义

- 文本节点输出前转义 `& < >`；属性值额外转义 `"`。序列化器是唯一输出点（`renderToHtml` / `renderFragment`），客户端 `toDom()` 走 `textContent`/`setAttribute`，同样不会把文本当 HTML。
- 服务端与浏览器共用同一份序列化代码，所以"服务端安全、客户端不安全"的分叉不存在。

## 2. URL 协议白名单

- 所有 URL 属性（`href`、`src`、`action`、`formaction`、`poster`、`cite`、`data`、`background`、`xlink:href`）在**写入时**校验（`AttributeDelegate`），并在序列化时**再校验一次**（防止通过 `attributes["href"] = ...` 绕过）。
- 允许：相对路径、`/`、`./`、`../`、`#fragment`、协议相对 `//host`、`http:`、`https:`、`mailto:`、`tel:`。
- 拒绝：`javascript:`、`vbscript:`、`data:`（data URL 可以携带 HTML 文档，即便看起来像图片）。
- 作者代码触发拒绝 = 构建期异常（快速失败）；Markdown 等不可信输入触发拒绝 = **降级为纯文本**，渲染不失败。

## 3. 原始 HTML

- 唯一入口是 `unsafeRawHtml(html)`（`RawHtml` 节点），名字刻意难看，便于 grep 审计。
- 文本、属性、Markdown 都不会产生 `RawHtml`；使用它意味着作者对内容负责。

## 4. Markdown

- 链接与图片 URL 走同一套 URL 白名单；不安全链接降级为标签文本。
- 行内代码、代码块原样转义输出；不执行内嵌 HTML。

## 5. CSP

- `kairos-htmx.page(...)` 接受 `nonce` 参数，给页面引用的每个样式表与脚本打上 nonce，配合 `script-src 'nonce-...'` 使用。
- 博客示例的推荐策略：
  `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`
- `style-src` 允许 `unsafe-inline` 是因为 htmx 会给 indicator 写临时内联样式；Kairos 自身不产出内联样式（验收里断言页面 `[style]` 元素为 0）。
- 同时下发 `X-Content-Type-Options: nosniff` 与 `Referrer-Policy: no-referrer`。

## 6. CSRF

- 服务端每个表单渲染一个 `_csrf` 隐藏字段；所有变更路由在读写数据库前比较该字段，失败返回 403。
- htmx 请求带 `HX-Request` 头，但 CSRF 校验不依赖它；普通表单与 htmx 请求走同一校验。
- 浏览器端可用 `htmxHeader("X-CSRF-Token", token)` 给所有 htmx 请求加自定义头（适合 token 放在页面 meta 里的场景）。

## 7. 已知限制

- 示例的 CSRF token 是进程级单 token，不是 per-session；生产应按会话签发并轮换。
- 没有内建权限/认证模型；路由、会话、鉴权属于应用与 Ktor 的职责。
- `data:` 被整体拒绝，内联图片需要用其他机制（例如 `unsafeRawHtml` 或应用自己的静态资源）。
- CSP 尚未自动注入到所有页面，需要应用在响应头里设置（博客示例已做）。
