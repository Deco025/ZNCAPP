# TCP 通信协议 JSON 化重构方案

本文档旨在对齐 Android App（发送端）与下位机（接收端）在 TCP 通信协议从“固定11字节”到“JSON字符串”重构过程中的技术实现方案。

## 1. 总体目标

放弃当前固定长度的二进制协议，切换到使用带换行符 `\n` 分隔的 JSON 字符串进行通信，以增强协议的可读性、健壮性和未来扩展性。

## 2. App 端（发送端）方案

*   **数据格式**：将原有的11字节数据包，转换为一个 JSON 对象。
*   **序列化**：使用 `kotlinx.serialization` 库将 Kotlin 数据对象序列化为 JSON 字符串。
*   **发送逻辑**：在每个 JSON 字符串末尾添加 `\n` 作为消息边界，然后通过 TCP Socket 发送。
*   **状态同步**：`stateSyncTimer` 将按周期发送完整的状态 JSON。
*   **指令格式（与接收端对齐）**：
    ```json
    {
      "cmd": "update_params",
      "data": {
        "tcp_sign": [1, 0, 2, 0],
        "tj_1": 150,
        "tj_2": 120,
        "tj_3": 1800,
        "image_mod": 2
      }
    }
    ```
    *   `cmd`: 固定为 `"update_params"` 字符串，用于标识指令类型。
    *   `data`: 一个包含所有状态参数的嵌套对象。
*   **接收逻辑**：App 端同样需要准备好接收来自接收端的 JSON 格式响应，例如：
    ```json
    {
      "type": "status_response",
      "data": { ... }
    }
    ```

## 3. 接收端方案（由负责人提供）

# TCP Communication Protocol Refactoring Plan

## 1. Objective

This document outlines a detailed plan to refactor the existing TCP communication protocol. The goal is to migrate from the current rigid, fixed-length 11-byte binary format to a modern, flexible, and extensible JSON-based protocol. This change will dramatically simplify adding new features and improve the robustness and debuggability of the remote control system.

## 2. Protocol Specification

### 2.1. Message Framing

All communication will consist of JSON objects serialized into strings. Each message **MUST** be terminated by a single newline character (`\n`).

### 2.2. Communication Flow (Request-Response)

To ensure clear and robust communication, we will adopt a formal request-response model for status checks.

1.  **Client -> Server (Status Request):** The client sends a request to get the car's current parameter status.
    ```json
    {"cmd": "get_status"}
    ```
2.  **Server -> Client (Status Response):** The C++ application receives this command and immediately replies with a `status_response` message containing the current values.
    ```json
    {
      "type": "status_response",
      "data": { ... }
    }
    ```
3.  **Client -> Server (Parameter Update):** The client sends a command to update one or more parameters.
    ```json
    {
      "cmd": "update_params",
      "data": { ... }
    }
    ```

### 2.3. Data Dictionary

To ensure both sides are perfectly aligned, the `data` object for both `update_params` and `status_response` will use the following fields and types.

| JSON Key | C++ Variable | Data Type | Range/Notes |
| :--- | :--- | :--- | :--- |
| `crossroad_turns` | `TCP_sign` | `array` of `integer` | `[0-255, 0-255, 0-255, 0-255]`. Corresponds to old F1-F4. |
| `lab_threshold_1` | `tj_1` | `integer` | `0-255`. Main LAB threshold. |
| `lab_threshold_2` | `tj_2` | `integer` | `0-255`. Secondary LAB threshold. |
| `speed_override`| `tj_3` | `integer` | `0-3000`. Speed value in cm/s. |
| `image_mode` | `image_mod` | `integer` | `0-4`. Selects which debug image to view. |
| `network_delay`| (new) | `integer` | `ms`. Optional, for client-side diagnostics. The C++ app will ignore this value. |

## 3. Receiver-Side Implementation Plan (This C++ Application)

All changes will be confined to the TCP handling thread within `ImageProcess::init()` in `cargo/_inc/road/imgprocess.cpp`.

### Step 3.1: Message Buffering & Framing

The core `while(true)` loop for each client connection will be modified. It will no longer assume each `recv()` call yields a complete packet.

```cpp
// Inside the client-handling std::thread lambda
std::string buffer; // Buffer to accumulate incoming data
char recv_chunk[512];

while (true) {
    int recvResult = recv(clientSocket, recv_chunk, sizeof(recv_chunk), 0);
    
    if (recvResult > 0) {
        buffer.append(recv_chunk, recvResult); // Append new data to buffer

        size_t pos;
        // Process all complete messages (ending in '\n') in the buffer
        while ((pos = buffer.find('\n')) != std::string::npos) {
            std::string json_message = buffer.substr(0, pos);
            buffer.erase(0, pos + 1);

            // ---> Go to Step 3.2: Parse and process json_message
        }
    } else {
        // ... handle disconnection or error ...
        break;
    }
}
```

### Step 3.2: JSON Parsing and Data Handling

For each extracted `json_message`, we will parse it and safely extract the data.

```cpp
// This block goes inside the while loop from Step 3.1

try {
    auto json_obj = nlohmann::json::parse(json_message);

    // Check for command type and handle it
    if (json_obj.contains("cmd")) {
        std::string cmd = json_obj["cmd"];

        if (cmd == "update_params" && json_obj.contains("data")) {
            auto data = json_obj["data"];
            std::unique_lock<std::mutex> lock(ListenThread_mutex);
            
            if (data.contains("crossroad_turns")) data["crossroad_turns"].get_to(TCP_sign);
            if (data.contains("lab_threshold_1")) tj_1 = data["lab_threshold_1"];
            if (data.contains("lab_threshold_2")) tj_2 = data["lab_threshold_2"];
            if (data.contains("speed_override"))  tj_3 = data["speed_override"];
            if (data.contains("image_mode"))      image_mod = data["image_mode"];
        }
        else if (cmd == "get_status") {
            // ---> Go to Step 3.3: Format and send response
        }
    }

} catch (const nlohmann::json::parse_error& e) {
    LOG_W << "JSON parse error: " << e.what() << LOG_END;
}
```
This replaces the entire `if (recvResult == 11 ...)` block with robust, key-based data extraction.

### Step 3.3: Response Formatting

When handling the `get_status` command, the server will lock the mutex, read the current state, and send it back as a JSON string.

```cpp
// Logic for handling "get_status"
nlohmann::json response_json;
response_json["type"] = "status_response";

{
    std::unique_lock<std::mutex> lock(ListenThread_mutex);
    response_json["data"]["crossroad_turns"] = TCP_sign;
    response_json["data"]["lab_threshold_1"] = tj_1;
    response_json["data"]["lab_threshold_2"] = tj_2;
    response_json["data"]["speed_override"] = tj_3;
    response_json["data"]["image_mode"] = image_mod;
}

std::string response_str = response_json.dump() + "\n";
send(clientSocket, response_str.c_str(), response_str.length(), 0);
```

## 4. Summary of Benefits

*   **Extensibility**: Adding a new parameter requires only adding a new key to the JSON, with no risk of breaking existing clients.
*   **Readability**: The protocol becomes human-readable, making debugging on both client and server sides vastly simpler.
*   **Robustness**: The protocol is no longer dependent on magic numbers and fixed byte positions. Using `try-catch` and `contains()` checks makes the receiver resilient to malformed or incomplete messages.

---
**(请将下方替换为接收端的方案)**

...

---
 // tcp初始化
    ListenThread = std::thread([this]() {
        while (true) {
            int ClientID = -1;
            int ClientSocket = accept(ListenSocket, NULL, NULL);
            if (ClientSocket == -1) {
                LOG_I << "Accept failed" << LOG_END;
                close(ListenSocket);
                return 1;
            }
            for (uint64_t i = 0; i < 64; i++) {
                if ((ListenID & ((uint64_t)0x01 << i)) == 0) {
                    ClientID = (int)i;
                    {
                        std::unique_lock<std::mutex> lock(ListenThread_mutex);
                        ListenID |= ((uint64_t)0x01 << i);
                    }
                    break;
                }
            }
            if (ClientID != -1) {
                std::thread([this, ClientSocket, ClientID]() {
                    int clientSocket = ClientSocket;
                    int clientID = ClientID;

                    LOG_I << "Client " << clientID << " connected." << LOG_END;

                    std::string buffer; // Buffer to accumulate incoming data
                    char recv_chunk[512];

                    while (true) {
                        int recvResult = recv(clientSocket, recv_chunk, sizeof(recv_chunk), 0);
                        
                        if (recvResult > 0) {
                            buffer.append(recv_chunk, recvResult); // Append new data to buffer

                            size_t pos;
                            // Process all complete messages (ending in '\n') in the buffer
                            while ((pos = buffer.find('\n')) != std::string::npos) {
                                std::string json_message = buffer.substr(0, pos);
                                buffer.erase(0, pos + 1);

                                try {
                                    auto json_obj = nlohmann::json::parse(json_message);

                                    // Check for command type and handle it
                                    if (json_obj.contains("cmd")) {
                                        std::string cmd = json_obj["cmd"];

                                        if (cmd == "update_params" && json_obj.contains("data")) {
                                            auto data = json_obj["data"];
                                            std::unique_lock<std::mutex> lock(ListenThread_mutex);
                                            
                                            if (data.contains("crossroad_turns")) data["crossroad_turns"].get_to(TCP_sign);
                                            if (data.contains("lab_threshold_1")) tj_1 = data["lab_threshold_1"];
                                            if (data.contains("lab_threshold_2")) tj_2 = data["lab_threshold_2"];
                                            if (data.contains("speed_override"))  tj_3 = data["speed_override"];
                                            if (data.contains("image_mode"))      image_mod = data["image_mode"];
                                        }
                                        else if (cmd == "get_status") {
                                            nlohmann::json response_json;
                                            response_json["type"] = "status_response";
                                            
                                            {
                                                std::unique_lock<std::mutex> lock(ListenThread_mutex);
                                                response_json["data"]["crossroad_turns"] = TCP_sign;
                                                response_json["data"]["lab_threshold_1"] = tj_1;
                                                response_json["data"]["lab_threshold_2"] = tj_2;
                                                response_json["data"]["speed_override"] = tj_3;
                                                response_json["data"]["image_mode"] = image_mod;
                                            }
                                            
                                            std::string response_str = response_json.dump() + "\n";
                                            // --- START: Roo added debug logging ---
                                            LOG_I << "Attempting to send response: " << response_str << LOG_END;
                                            int bytes_sent = send(clientSocket, response_str.c_str(), response_str.length(), 0);
                                            LOG_I << "Send result: " << bytes_sent << " bytes sent." << LOG_END;
                                            // --- END: Roo added debug logging ---
                                        }
                                    }
                                } catch (const nlohmann::json::parse_error& e) {
                                    LOG_W << "JSON parse error: " << e.what() << " on message: " << json_message << LOG_END;
                                }
                            }
                        } else if (recvResult == 0) {
                            LOG_I << "Client " << clientID << " disconnected." << LOG_END;
                            break;
                        } else {
                            LOG_I << "Receive failed for client " << clientID << " with error: " << strerror(errno) << LOG_END;
                            break;
                        }
                    }

                    {
                        std::unique_lock<std::mutex> lock(ListenThread_mutex);
                        ListenID &= ~((uint64_t)0x01 << clientID);
                    }
                    // 关闭客户端套接字
                    close(clientSocket);
                }).detach();
            } else {
                close(ClientSocket);
            }
        }
    });
