# 仓库守则

## 规则

- 复用所有代码组件，类，已有库而非从头构建所有
- 不要为 `app` 中已经存在的行为引入并行实现。
- 在实现非简单改动之前，先在互联网上搜索现有解决方案、参考资料和已有实现，再根据结果适配到当前代码库。
- 仅当不存在合适的扩展点时才创建新类；新类应保持小型化，并仅服务于对应功能范围。
- 配置只能使用 JSON 存储。对于新的配置数据，不要引入 XML、YAML 或 `.properties` 格式。
- 尽可能保持配置 Schema 的向后兼容，并在 PR 说明中验证和记录迁移可能造成的影响。
- 遵守 `app` 内部的包边界：可复用基础设施、Repository、导航、主题和平台服务应放在 `app/src/main/java/com/app/ralaunch/core` 下；面向用户的产品功能流程应放在 `app/src/main/java/com/app/ralaunch/feature` 下。
- 使用现有的依赖注入模式（Koin modules、`core/di/contract` 和 `core/di/service`），不要使用临时的单例实现或隐藏的全局状态。
- 优先扩展现有的 feature 包，而不是创建新的顶层 feature 命名空间。
- 将 `/core/libs` 中的 vendored 代码树（例如 `SDL`、`FNA3D`、`FAudio`、`gl4es`）视为外部代码；除非明确是在进行依赖相关工作，否则避免修改它们。
- 将改动范围限制在当前任务内；避免顺手重构与任务无关的文件。
- 对持久化数据和公开接口保持向后兼容；如果必须进行迁移，应在 PR 中记录相关要求。
- 尽量使用 `kotlin` 的接口而非 `java` 的接口，如果同一份文件中同时有 `java` 以及 `kotlin` 接口，统一升级为 kotlin 接口，只有在跨 `app` `vendor` 边界时进行转换。
- **非必要避免创建 try catch，除非有明确指示**
- 非必要 **避免创建单行 wrapper** ，除非有明确指示，对于比较长的单行代码，更推荐的方式是 **为长代码添加注释而非创建 wrapper**
- 尽量使用 **kotlin lsp** 进行代码检索，重命名等操作

## 编码风格与命名规范

- 遵循 Kotlin 官方代码风格，使用 4 个空格缩进。
- 命名规范：包名使用 `lowercase`，类/对象使用 `PascalCase`，方法/变量使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。
- 保持模块边界清晰：平台/runtime 相关内容放在 `app/core/platform`，应用级基础设施放在 `app/core`，面向用户的功能放在 `app/feature`。
- 在同一包区域内，应明确区分文件职责：Compose 相关代码放在 `.../ui`，ViewModel 和状态协调器放在 `.../vm`，feature 自有的 model 或 DTO 类型放在 `.../model`，契约和接口放在 `.../contract`，具体的 service/manager/repository 实现放在 `.../service`。

## 优先使用的库

| 功能               | 库                          |
|--------------------|-----------------------------|
| Kotlin / Java 日志 | Timber                      |
| Native 日志        | spdlog                      |
| 压缩 / 解压缩      | org.apache.commons.compress |

## Commit 与 Pull Request 规范

- 与现有提交历史的风格保持一致，例如：`feat(scope): ...`、`fix: ...`、`refactor: ...`、`chore: ...`。
- 每个 commit 应聚焦单一目标，并保持内容完整、自包含。
- PR 应包含：修改了什么、为什么修改、如何测试（包括执行的命令）、关联的 issue（如适用），以及 UI 更新对应的截图或视频。
- 对于 Bug 修复，应提供复现信息，包括：设备型号、Android 版本以及相关日志。
