# ZNC App 开发者交接文档

## 1. 项目概述

本项目是一个用于通过 TCP/IP 协议控制智能小车的 Android 应用程序。

它最初是一个基于旧的 Android View 和原始二进制协议的简单应用。在2025年7月，我们对其进行了**一次彻底的、从内到外的现代化重构**，旨在提高代码的可维护性、健壮性和未来可扩展性。

本文档旨在为后续接手的开发者提供一份清晰的路线图，帮助您快速理解当前架构，并安全、高效地进行二次开发。

---

## 2. 技术栈

*   **语言**: [Kotlin](https://kotlinlang.org/) (100%)
*   **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (完全声明式 UI)
*   **架构**: **MVVM (Model-View-ViewModel)**，严格遵循单向数据流。
*   **异步处理**: [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-guide.html) (ViewModel 中的所有后台任务)
*   **序列化**: [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (用于 JSON 对象和数据类之间的转换)
*   **网络**: Java TCP Sockets

---

## 3. 架构解析 (必读)

理解本项目的架构是进行任何修改的基础。我们严格遵循 MVVM 设计模式。

![MVVM Architecture](https://user-images.githubusercontent.com/135955/209773957-03816a30-4963-4a63-b873-3e4b31a19615.png)

### 3.1. 分层职责

*   **View (UI 层)**
    *   **位置**: [`app/src/main/java/com/example/znc_app/ui/`](app/src/main/java/com/example/znc_app/ui/)
    *   **职责**: **只负责展示数据，不包含任何业务逻辑。** 所有的 UI 组件 (Composable 函数) 都是“无状态”的，它们接收数据并显示，然后将用户的操作事件（如点击、滑动）通知给 `ViewModel`。
    *   **核心**: `MainActivity.kt`, `ColorScreen.kt`, `SelectScreen.kt` 等。

*   **ViewModel (逻辑/状态层)**
    *   **位置**: [`app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt`](app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt)
    *   **职责**: **这是整个 App 的“大脑”和“唯一真理来源”**。它负责：
        1.  持有并管理所有 UI 状态 (`UiState`)。
        2.  响应来自 UI 的事件（如 `onConnectButtonClicked()`）。
        3.  执行所有的业务逻辑（如启动/停止心跳）。
        4.  调用 Model 层来执行数据 I/O 操作。
    *   **注意**: **90% 的新功能开发或逻辑修改，都应该从这个文件开始。**

*   **Model (数据层)**
    *   **位置**: [`app/src/main/java/com/example/znc_app/data/`](app/src/main/java/com/example/znc_app/data/)
    *   **职责**: 负责所有数据的“进”和“出”。
    *   **核心**:
        *   [`TcpRepository.kt`](app/src/main/java/com/example/znc_app/data/TcpRepository.kt): 数据仓库。它封装了所有的数据操作细节，对 `ViewModel` 提供清晰的接口（如 `connect()`, `disconnect()`, `sendUpdateCommand()`）。它还负责管理本地数据存取 (`SharedPreferences`)。
        *   [`TCPCommunicator.java`](app/src/main/java/com/example/znc_app/ui/theme/TCPCommunicator.java): 底层的网络引擎。**这个文件经过了血泪的调试和重构，请不要轻易修改它的线程模型**。它使用一个 `ExecutorService` 来有序地处理一次性网络任务，并用一个独立的 `Thread` 来处理阻塞式的读取。
        *   [`models.kt`](app/src/main/java/com/example/znc_app/data/models.kt): 定义了与服务器通信的 JSON 数据结构。

### 3.2. 单向数据流

数据在 App 中是单向流动的，这使得状态变化可预测且易于调试。

1.  **用户操作**: 用户在 **UI** 上点击按钮。
2.  **事件上报**: UI 调用 **ViewModel** 的相应方法 (e.g., `onButtonSelected()`)。
3.  **状态更新/业务逻辑**: **ViewModel** 更新内部的 `UiState`，UI 会自动响应新状态并重绘。如果需要网络操作，ViewModel 会调用 **Repository** 的方法。
4.  **数据 I/O**: **Repository** 调用底层的 **TCPCommunicator** 发送数据。
5.  **数据返回**: **TCPCommunicator** 的接收线程收到数据，通过回调通知 **Repository**。
6.  **事件上报**: **Repository** 将收到的数据放入 `SharedFlow` 中。
7.  **状态更新**: **ViewModel** 的 `collect` 观察到 `SharedFlow` 的新事件，更新 `UiState`。
8.  **UI 刷新**: UI 自动响应 `UiState` 的变化并重绘。

---

## 4. 通信协议

我们当前使用**换行符 (`\n`) 分隔的 JSON 字符串**作为通信协议。

*   **协议文档**: 协议的变更历史和细节记录在 [`PROTOCOL_REFACTOR_PLAN.md`](PROTOCOL_REFACTOR_PLAN.md) 和 [`PROTOCOL_UPDATE_V2.md`](PROTOCOL_UPDATE_V2.md) 中。
*   **数据模型定义**: App 端所有用于通信的 JSON 结构，都在 [`app/src/main/java/com/example/znc_app/data/models.kt`](app/src/main/java/com/example/znc_app/data/models.kt) 中定义。

### 4.1. App -> 小车 的核心数据结构

命令格式为 `{"cmd": "update_params", "data": {...}}`，其中 `data` 对象结构如下：
```kotlin
// Defined in data/models.kt
@Serializable
data class CommandData(
    @SerialName("crossroad_turns")
    val crossroadTurns: List<Int>,

    @SerialName("b_star")
    val bStar: Int,

    @SerialName("a_star")
    val aStar: Int,

    @SerialName("global_speed")
    val globalSpeed: Int,

    @SerialName("turn_speed")
    val turnSpeed: Int,

    @SerialName("image_mode")
    val imageMode: Int,

    @SerialName("network_delay")
    val networkDelay: Int
)
```

### 4.2. 关键配置！

在 [`TcpRepository.kt`](app/src/main/java/com/example/znc_app/data/TcpRepository.kt) 中，我们使用的 `Json` 实例经过了特殊配置：
`private val json = Json { encodeDefaults = true }`
`encodeDefaults = true` 这个设置是**必须的**，它确保了即使 `data class` 中的字段值等于其默认值（例如 `"cmd": "update_params"`），该字段也会被包含在最终的 JSON 字符串中。**请勿修改此配置**。

---

## 5. 如何接手开发 (从这里开始)

### 5.1. 如果你想增加一个新的滑块/参数...

假设你想增加一个名为“循迹灵敏度” (`tracking_sensitivity`) 的新滑块。请严格遵循以下步骤：

1.  **修改数据模型 (Model)**:
    *   打开 [`app/src/main/java/com/example/znc_app/data/models.kt`](app/src/main/java/com/example/znc_app/data/models.kt)。
    *   在 `CommandData` 这个 `data class` 中，增加一个新的字段：
        ```kotlin
        @SerialName("tracking_sensitivity")
        val trackingSensitivity: Int,
        ```

2.  **修改状态模型 (ViewModel)**:
    *   打开 [`app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt`](app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt)。
    *   找到 `ColorScreenState`，将其中的 `sliders` 和 `modeValues` 列表的长度从 `4` 改为 `5`。
        ```kotlin
        // 从...
        val sliders: List<Float> = listOf(0f, 0f, 0f, 0f),
        val modeValues: List<List<Float>> = List(4) { listOf(0f, 0f, 0f, 0f) },
        // 改为...
        val sliders: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
        val modeValues: List<List<Float>> = List(4) { listOf(0f, 0f, 0f, 0f, 0f) },
        ```

3.  **修改 UI (View)**:
    *   打开 [`app/src/main/java/com/example/znc_app/ui/ColorScreen.kt`](app/src/main/java/com/example/znc_app/ui/ColorScreen.kt)。
    *   在 `ColorSlidersSection` 中，给 `sliderLabels` 列表增加新的标签：
        ```kotlin
        val sliderLabels = listOf("b*", "a*", "全局速度", "拐弯速度", "循迹灵敏度")
        ```
    *   UI 会自动创建出第五个滑块。

4.  **连接数据与 UI (ViewModel)**:
    *   回到 [`app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt`](app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt)。
    *   在 `sendFullPacket` 方法中，更新 `CommandData` 的创建逻辑，将新的滑块值映射到新的数据字段：
        ```kotlin
        val commandData = CommandData(
            // ... 其他字段保持不变 ...
            turnSpeed = sliders.getOrElse(3) { 0f }.toInt(),
            trackingSensitivity = sliders.getOrElse(4) { 0f }.toInt(), // <-- 新增映射
            imageMode = colorState.activeMode,
            // ...
        )
        ```

5.  **通知小车端**:
    *   更新 [`PROTOCOL_UPDATE_V2.md`](PROTOCOL_UPDATE_V2.md) 或创建 V3 文档，将协议变更（增加了 `tracking_sensitivity` 字段）清晰地告知小车端负责人。

### 5.2. 注意事项 (血泪教训)

*   **不要轻易修改线程模型**: [`TCPCommunicator.java`](app/src/main/java/com/example/znc_app/ui/theme/TCPCommunicator.java) 的线程模型是当前 App 得以稳定运行的基石。请勿轻易改回 `new Thread()` 或其他模式。
*   **坚持单向数据流**: 所有的状态变更都应该由 `MainViewModel` 发起。UI 层只做展示和事件上报。
*   **协议同步**: 任何对 `models.kt` 的修改，都必须与小车端负责人沟通，并更新相关文档。