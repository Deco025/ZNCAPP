# 协议更新 V2：增加拐弯速度并优化命名

**致小车端负责人：**

您好！为了支持新的功能需求，并进一步提高我们通信协议的可读性和可维护性，我们对 App 端进行了更新。这需要您在小车（接收端）的程序中，对 JSON 解析部分做出相应的修改。

## 1. 变更总结

我们对 `update_params` 命令中 `data` 对象内的字段进行了如下优化：

| 旧 JSON 键名 | 新 JSON 键名 | 变更类型 | 备注 |
| :--- | :--- | :--- | :--- |
| `lab_threshold_1` | `b_star` | **重命名** | 对应 UI 上的 `b*` 滑块 |
| `lab_threshold_2` | `a_star` | **重命名** | 对应 UI 上的 `a*` 滑块 |
| `speed_override` | `global_speed` | **重命名** | 对应 UI 上的“全局速度”滑块 |
| (无) | `turn_speed` | **新增** | 对应 UI 上的“拐弯速度”滑块 |

**注意：** `crossroad_turns` 和 `image_mode` 两个字段保持不变。

## 2. 新的 JSON 结构示例

现在，App 发送的 `update_params` 命令中的 `data` 对象将如下所示：

```json
{
  "crossroad_turns": [4, 3, 4, 4],
  "b_star": 147,
  "a_star": 53,
  "global_speed": 1,
  "turn_speed": 120, 
  "image_mode": 2,
  "network_delay": 15
}
```

## 3. 需要您做出的修改

请您在 C++ 代码中，处理 `update_params` 命令的逻辑部分，做出如下调整：

1.  将所有检查和获取旧键名的地方，替换为新的键名。
2.  增加对新键名 `turn_speed` 的检查和获取。

**修改示例（伪代码）：**

```cpp
// 在解析 data 对象的代码块中...

// --- 修改 ---
if (data.contains("b_star")) tj_1 = data["b_star"];
if (data.contains("a_star")) tj_2 = data["a_star"];
if (data.contains("global_speed")) tj_3 = data["global_speed"];

// --- 新增 ---
if (data.contains("turn_speed")) {
    // 请将 data["turn_speed"] 的值赋给对应的变量
    // 例如: turn_speed_variable = data["turn_speed"];
}

// --- 保持不变 ---
if (data.contains("crossroad_turns")) data["crossroad_turns"].get_to(TCP_sign);
if (data.contains("image_mode")) image_mod = data["image_mode"];
```

完成以上修改后，小车端即可正确解析 App 发送的新数据。感谢您的配合！