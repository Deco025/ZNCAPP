# ZNC App 技术说明文档

## 1. 项目概述

ZNC App 是一个基于 Android 平台的物联网 (IoT) 应用，专用于通过 TCP/IP 协议控制 LED 照明设备。

本项目最近完成了从传统 XML 视图到现代声明式 UI (Jetpack Compose) 的重大重构，并全面采用了 MVVM (Model-View-ViewModel) 架构模式，以提升代码的可维护性、可测试性和开发效率。

## 2. 技术栈与架构

### 技术栈

*   **语言**: Kotlin
*   **UI**: Jetpack Compose
*   **异步处理**: Kotlin Coroutines
*   **架构模式**: MVVM (Model-View-ViewModel)

### MVVM 架构

本应用遵循严格的 MVVM 架构，实现了界面 (View) 与业务逻辑 (ViewModel) 的清晰分离，以及业务逻辑与数据源 (Model) 的解耦。

```mermaid
graph TD
    A[View (Composable Screens)] -- User Events --> B(ViewModel);
    B -- State Updates (StateFlow) --> A;
    B -- Requests Data --> C(Model / Repository);
    C -- Provides Data --> B;
```

*   **View (UI 层)**
    *   **实现**: `SelectScreen.kt`, `ColorScreen.kt`
    *   **职责**: 完全由 Composable 函数构成，负责渲染 UI。它观察来自 `ViewModel` 的状态 (`StateFlow`) 并将用户交互事件（如点击、滑动）通知给 `ViewModel`。`View` 本身不包含任何业务逻辑或状态。

*   **ViewModel (视图模型层)**
    *   **实现**: `MainViewModel.kt`
    *   **职责**: 作为 UI 状态的**唯一可信来源 (Single Source of Truth)**。它持有并管理所有与 UI 相关的数据（如连接状态、颜色值、模式等），并以 `StateFlow` 的形式暴露给 `View`。所有业务逻辑，包括用户输入处理、网络通信调度和状态更新，都在这一层完成。

*   **Model (数据层)**
    *   **实现**: `TcpRepository.kt`
    *   **职责**: 提供和管理应用所需的数据。它抽象了数据的来源，将底层的网络通信细节 (`TCPCommunicator`) 和本地数据存储 (如 `SharedPreferences`) 封装起来，为 `ViewModel` 提供一个干净、统一的数据访问接口。

## 3. 核心通信逻辑

应用的通信核心位于 `MainViewModel.kt` 中，其设计目标是实现与硬件设备的可靠、准实时的状态同步。

### 数据包结构

所有与设备的通信都通过一个固定长度为11字节的数据包完成。其结构如下：

| 偏移量 (Offset) | 字节数 (Bytes) | 字段 (Field) | 描述                                     |
| :-------------- | :------------- | :----------- | :--------------------------------------- |
| 0               | 2              | 包头         | 固定为 `0xAA 0x55`，用于数据帧校验。     |
| 2               | 1              | 命令ID       | `0x00` 为查询，`0x01` 为命令。           |
| 3               | 1              | 按钮状态     | 8个独立按钮的开关状态 (bit-mask)。       |
| 4               | 3              | 颜色数据     | RGB颜色值，每个通道1字节。               |
| 7               | 1              | 模式ID       | 当前灯光效果的模式。                     |
| 8               | 3              | 预留         | 未使用，填充为0。                        |

### 状态同步机制 (State Sync Timer)

为了实现流畅的用户体验和“乐观UI”，`MainViewModel` 启动一个每 **100ms** 触发一次的定时器 (`stateSyncTimer`)。

*   **工作原理**: 每次触发时，它都会将当前 App 的完整状态（颜色、模式等）打包成一个数据包并发送到服务器。
*   **设计目的**: 这种“持续推送”的策略确保了即使某些数据包在网络传输中丢失，设备状态最终也会与 App 的状态保持同步，从而达到**最终一致性**。用户在UI上的操作会立即反馈，无需等待服务器确认。

### 命令/查询循环

通信协议并非简单地单向发送命令，而是采用了一种查询/命令交替的循环机制来确保双向同步。

*   **工作原理**: `MainViewModel` 内部维护一个发送计数器 `sendCnt`。每3次发送中：
    1.  前 **2** 次是 **查询** (`commandId = 0x00`)，用于从设备获取最新状态。
    2.  第 **3** 次是 **命令** (`commandId = 0x01`)，用于将 App 的状态强制写入设备。
*   **设计目的**: 这种 `2查1控` 的模式，既能及时获取设备的外部状态变化（例如通过物理开关操作），又能保证 App 的操作最终被执行，实现了可靠且健壮的状态同步。

### 连接管理

*   **并发控制**: 为了防止在连接或断开过程中发生并发操作导致状态不一致，`onConnectButtonClicked` 方法使用了一个 `isBusy` 状态锁。在开始连接操作时，`isBusy` 设置为 `true`，此时UI上的连接按钮会被禁用，直到连接成功或失败后 `isBusy` 才被重置为 `false`。这确保了连接操作的原子性。
*   **资源清理**: 当 `MainViewModel` 生命周期结束时（例如用户离开相关界面），`onCleared` 回调会被触发。在此方法中，必须调用 `repository.disconnect()` 来主动断开 TCP 连接并清理所有相关资源（如定时器和协程），以防止内存泄漏和意外的后台通信。

### 超时机制 (Timeout Timer)

为了处理网络异常或设备离线等情况，`MainViewModel` 实现了一个超时检测机制。

*   **工作原理**: 一个每 **5 秒** 运行一次的定时器 (`timeoutTimer`) 会检查 `lastReceivedTime` 变量，该变量记录了最后一次从服务器成功接收到数据的时间戳。
*   **设计目的**: 如果当前时间与 `lastReceivedTime` 的差值超过了预设的超时阈值（5秒），则认为连接已丢失。ViewModel 会自动调用 `disconnect` 方法并更新 UI 状态，向用户提示连接已断开，从而避免了应用在无响应的连接上无限等待。