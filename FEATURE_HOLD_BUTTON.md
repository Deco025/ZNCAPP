# 新功能设计文档：按住执行按钮

**版本:** 1.0
**日期:** 2025年7月11日
**作者:** Gemini

## 1. 需求概述

为了增强 App 的交互能力，需要增加一种新的控制方式：**按住执行按钮**。

该按钮区别于项目中现有的“状态切换”按钮，其核心行为如下：
1.  **瞬时动作**: 当用户手指**按住**按钮时，App 向小车发送一个“执行”信号（例如，值为 `1`）。
2.  **自动复位**: 当用户手指**松开**按钮时，App 立即发送一个“停止”信号（例如，值为 `0`）。
3.  **触觉反馈**: 在用户**按下**和**松开**按钮的瞬间，设备应提供一次短暂的振动反馈，以提升操作确认感。

## 2. 设计理念与架构集成

本功能的设计将严格遵循项目现有的 **MVVM 架构**和**单向数据流**原则，确保新代码与现有代码库的风格和质量保持一致。

核心设计思想是：**UI 操作只更新 ViewModel 中的状态，由 ViewModel 的周期性同步任务自动将最新状态发送出去。**

这带来了几个好处：
*   **解耦**: UI 层（按钮的按下/松开事件）与数据层（TCP 发送）完全分离。
*   **健壮性**: 即使某个数据包丢失，后续的同步任务也会在 100 毫秒内发送正确的状态，保证最终一致性。
*   **易于实现**: 我们无需为这个按钮编写任何独立的网络发送逻辑，只需复用 `MainViewModel` 中已有的 `stateSyncTimer` 即可。

## 3. 技术实现步骤

### 第 1 步：通信协议扩展 (`data/models.kt`)

首先，我们需要在 App 与小车约定的 JSON 通信协议中，增加一个用于传递此按钮状态的新字段。

*   **文件**: `app/src/main/java/com/example/znc_app/data/models.kt`
*   **操作**: 在 `CommandData` 数据类中，增加一个名为 `actionButtonHold` 的整型字段。
*   **约定**:
    *   `1`: 代表按钮被按住。
    *   `0`: 代表按钮已松开。

```kotlin
// data/models.kt
@Serializable
data class CommandData(
    // ... 其他现有字段 ...

    @SerialName("action_button_hold")
    val actionButtonHold: Int
)
```
> **[Info]** **协议同步**: 此项变更必须通知小车端负责人，以便其在接收程序中添加对 `action_button_hold` 字段的解析。

### 第 2 步：UI 状态定义 (`viewmodel/MainViewModel.kt`)

我们需要在 `ViewModel` 的 `UiState` 中增加一个状态，用于实时追踪按钮是否被用户按住。

*   **文件**: `app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt`
*   **操作**: 在 `UiState` 数据类中，增加一个名为 `isActionButtonPressed` 的布尔型字段。

```kotlin
// viewmodel/MainViewModel.kt
data class UiState(
    // ... 其他现有字段 ...
    val isActionButtonPressed: Boolean = false
)
```

### 第 3 步：业务逻辑实现 (`viewmodel/MainViewModel.kt`)

`ViewModel` 需要提供方法供 UI 调用，以响应用户的按下和松开操作，并处理副作用（如振动）。

*   **文件**: `app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt`
*   **操作**:
    1.  **创建事件通道**: 定义一个 `ViewEvent` 密封类和一个 `SharedFlow`，用于向 UI 发送一次性的事件（如振动），避免不必要的 UI 重组。
    2.  **实现 `onActionButtonPressed` 方法**: 当被调用时，将 `isActionButtonPressed` 状态更新为 `true`，并通过事件通道发送 `Vibrate` 事件。
    3.  **实现 `onActionButtonReleased` 方法**: 当被调用时，将 `isActionButtonPressed` 状态更新为 `false`，并再次发送 `Vibrate` 事件。

```kotlin
// viewmodel/MainViewModel.kt

// 1. 定义一次性事件
sealed class ViewEvent {
    object Vibrate : ViewEvent()
}

class MainViewModel(...) {
    private val _events = MutableSharedFlow<ViewEvent>()
    val events = _events.asSharedFlow()

    // 2. 实现按下逻辑
    fun onActionButtonPressed() {
        _uiState.update { it.copy(isActionButtonPressed = true) }
        viewModelScope.launch { _events.emit(ViewEvent.Vibrate) }
    }

    // 3. 实现松开逻辑
    fun onActionButtonReleased() {
        _uiState.update { it.copy(isActionButtonPressed = false) }
        viewModelScope.launch { _events.emit(ViewEvent.Vibrate) }
    }
}
```

### 第 4 步：关联数据发送 (`viewmodel/MainViewModel.kt`)

将新的 `isActionButtonPressed` 状态与通信协议中的 `actionButtonHold` 字段关联起来。

*   **文件**: `app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt`
*   **操作**: 在 `sendFullPacket()` 方法中，根据 `isActionButtonPressed` 的值来决定 `actionButtonHold` 的值。

```kotlin
// viewmodel/MainViewModel.kt
private fun sendFullPacket() {
    // ...
    val commandData = CommandData(
        // ... 其他字段的映射 ...
        actionButtonHold = if (currentState.isActionButtonPressed) 1 else 0
    )
    // ...
}
```

### 第 5 步：UI 组件实现 (`ui/ColorScreen.kt` 或其他)

我们需要创建一个新的 Composable 组件，它能够精确地检测用户的“按下”和“松开”手势。

*   **文件**: 建议在 `app/src/main/java/com/example/znc_app/ui/` 目录下创建 `ActionButton.kt` 或直接在 `ColorScreen.kt` 中实现。
*   **核心技术**: 使用 `Modifier.pointerInput` 和 `detectTapGestures` 来捕获底层的触摸事件。

```kotlin
// ui/ColorScreen.kt (示例)
@Composable
fun ActionButton(
    isPressed: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { /* 此处为空，因为逻辑由 pointerInput 处理 */ },
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onPress()       // 调用 ViewModel 的按下方法
                    tryAwaitRelease() // 阻塞协程直到手指松开
                    onRelease()     // 调用 ViewModel 的松开方法
                }
            )
        }
    ) {
        Text(if (isPressed) "执行中..." else "按住执行")
    }
}
```

### 第 6 步：振动反馈实现 (`MainActivity.kt`)

最后，在 UI 层监听来自 `ViewModel` 的 `Vibrate` 事件，并调用系统服务来执行振动。

1.  **权限**: 在 `AndroidManifest.xml` 中确保已声明振动权限。
    ```xml
    <uses-permission android:name="android.permission.VIBRATE" />
    ```
2.  **监听与执行**: 在 `MainActivity.kt` 或任何包含该按钮的顶层 Composable 中，使用 `LaunchedEffect` 来收集事件流。

```kotlin
// MainActivity.kt
@Composable
fun MainScreen(viewModel: MainViewModel) {
    // ...
    val context = LocalContext.current

    // 使用 LaunchedEffect 监听一次性事件
    LaunchedEffect(key1 = Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ViewEvent.Vibrate -> {
                    // 在这里执行振动代码
                    // ...
                }
            }
        }
    }
    // ...
}
```

## 4. 总结

该方案通过最小化和高内聚的修改，无缝地将一个具有复杂交互（按住/松开/振动）的新功能集成到现有架构中。它完全重用了项目的核心数据同步机制，保证了代码的健壮性和可维护性。

---

## 5. 详细开发任务清单 (To-do List)

为了方便开发，以下是本方案的具体实施步骤清单。

### 准备工作
- [ ] **协议同步**: 与小车端负责人沟通，确保他们知晓需要在接收端程序中增加对新字段 `action_button_hold` 的解析。

### 数据层 (Data Layer)
- [ ] **修改数据模型**: 打开 `app/src/main/java/com/example/znc_app/data/models.kt` 文件，在 `CommandData` 数据类中增加 `@SerialName("action_button_hold") val actionButtonHold: Int` 字段。

### 逻辑层 (ViewModel Layer)
- [ ] **更新 UI 状态**: 打开 `app/src/main/java/com/example/znc_app/viewmodel/MainViewModel.kt` 文件，在 `UiState` 数据类中增加 `val isActionButtonPressed: Boolean = false` 字段。
- [ ] **实现事件通道**: 在 `MainViewModel.kt` 中，定义 `ViewEvent` 密封类和 `private val _events = MutableSharedFlow<ViewEvent>()` 以及其公共的 `val events`。
- [ ] **实现业务逻辑**: 在 `MainViewModel` 中，创建 `onActionButtonPressed()` 和 `onActionButtonReleased()` 两个公共方法，用于更新状态和发送 `Vibrate` 事件。
- [ ] **关联数据发送**: 在 `MainViewModel` 的 `sendFullPacket()` 方法中，更新 `CommandData` 的创建逻辑，增加 `actionButtonHold = if (currentState.isActionButtonPressed) 1 else 0` 的映射。

### UI 层 (View Layer)
- [ ] **声明振动权限**: 在 `app/src/main/AndroidManifest.xml` 文件中，确认或添加 `<uses-permission android:name="android.permission.VIBRATE" />`。
- [ ] **创建新目录 (建议)**: 在 `app/src/main/java/com/example/znc_app/ui/`下创建新目录 `components`。
- [ ] **实现 UI 组件**: 在 `ui/components/` 目录下创建一个新文件 `ActionButton.kt`，在其中实现 `ActionButton` Composable，使用 `Modifier.pointerInput(Unit) { detectTapGestures(...) }` 来处理按下和松开事件。
- [ ] **集成 UI 组件**: 在 `ui/ColorScreen.kt` (或任何需要此按钮的界面) 中，调用你创建的 `ActionButton`，并将其 `isPressed`、`onPress`、`onRelease` 参数与 `ViewModel` 的状态和方法进行绑定。
- [ ] **实现振动反馈**: 在 `MainActivity.kt` 或顶层 Composable 中，使用 `LaunchedEffect` 来收集 `viewModel.events` 事件流。当收到 `ViewEvent.Vibrate` 时，调用系统的振动服务 (建议使用 `VibrationEffect` 以获得更好的触觉反馈)。

### 测试与验证
- [ ] **UI 测试**: 编译并运行 App，验证“按住执行”按钮的文本在按下/松开时是否正确变化，以及是否能感受到振动反馈。
- [ ] **联调测试**: 与小车端联调，通过服务器日志确认在按下/松开按钮时，`action_button_hold` 字段的值是否在 `1` 和 `0` 之间正确切换。
