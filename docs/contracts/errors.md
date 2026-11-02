# Kairos 错误码

- 状态：M2 可观测性契约（2026-10-31）
- 相关：[ADR 0002](../adr/0002-style-condition-model.md)、[ADR 0003](../adr/0003-classname-manifest-contract.md)、[public-api.md](public-api.md)

每个构建期错误都以 `kairos[<CODE>]` 的形式出现在报告与异常文本里（实现中写作 `[CODE] <file>:<line>:<column> - <reason>`），并同时写入 manifest 的 `errors` 数组。错误码一经发布不再复用或改变含义；新增错误用新码。

| 错误码 | 含义 | 触发条件 | 修复方向 |
|---|---|---|---|
| `KAIROS-STYLE-001` | 样式块无法折叠 | 块内出现编译器不认识的语句、动态值、无法解析的 helper，或 `css { }` 之外无法折叠的形态 | 检查块内只含常量与已知原语；把动态逻辑移到块外（`when` 选整块） |
| `KAIROS-STYLE-002` | 折叠过程内部错误 | 编译器插件自身异常 | 附错误文本与源码位置提 issue；临时可用 `strict = false` 观察是否只是降级 |
| `KAIROS-STYLE-003` | 空样式块 | `css { }` 没有任何声明 | 删除空块，或在块内补声明 |
| `KAIROS-STYLE-004` | 非法 CSS 片段 | 属性或值含 `;`、`{`、`}` 或换行 | 去掉这些字符；需要复杂值时用 `declaration()` 之前自行校验 |
| `KAIROS-STYLE-005` | 类名碰撞 | 两个不同的（层 + 条件 + 声明）组哈希到同一个类名 | 极罕见；记录源码位置并提 issue，不要手工改名 |
| `KAIROS-STYLE-006` | 组合歧义 | `styles()` 组合中同层同属性写两次，或 shorthand 与 longhand 在同一层重叠（如 `padding` 与 `padding-top`） | 把其中一个换到不同层，或合并成一个样式块 |
| `KAIROS-STYLE-007` | strict 模式禁止降级 | `strict = true` 且 `styles()` 的操作数无法静态解析，只能留给运行时拼接 | 让操作数是编译期可见的样式常量；或关闭该模块的 strict |

## 与 manifest 的关系

- `errors` 非空时，`merge` 必须拒绝该模块（CLI 行为）。
- `coverage.fallbackBlocks` 记录被静默降级的次数；strict 模式下降级会同时产生 `KAIROS-STYLE-007`，因此 `fallbackBlocks` 应为 0。
- 修改错误码或错误文本属于可观测性契约变更：`errors` 的文本不是 API，但错误码是；变更需在 release notes 中列出。
