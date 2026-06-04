# SixChess (六子棋)

六子棋在线对战 - 六子连珠，连线对战

## 项目简介

不同于传统五子棋（五子连珠），六子棋需要**六子连珠**才能获胜。棋盘为 15×15，黑棋先手，白棋后手，交替落子。

本项目的服务器运行在 Windows PC 上，Android 客户端通过 ngrok 隧道，可以在不同网络环境下进行联机对战。

## 项目结构

```
SixChess/
├── server/          # Java WebSocket 服务器
├── android/         # Android 客户端
├── start_server.bat # 一键启动服务器 + ngrok
└── README.md
```

## 快速开始

### 1. 启动服务器

```bash
# 进入 server 目录构建
cd server
gradlew build

# 运行服务器
java -jar build/libs/sixchess-server-1.0.0.jar
```

### 2. 启动 ngrok 隧道

```bash
# 在新终端中运行
ngrok http 8080
```

ngrok 会输出一个公网 URL，例如：
```
https://abcd-12-34-56-78.ngrok-free.app
```

### 3. 配置 Android 客户端

修改 `android/app/src/main/res/values/strings.xml` 中的服务器地址：

```xml
<!-- 模拟器测试 -->
<string name="server_url">ws://10.0.2.2:8080</string>

<!-- 真机测试（使用 ngrok URL） -->
<string name="server_url">wss://abcd-12-34-56-78.ngrok-free.app</string>
```

### 4. 构建并安装 Android APK

```bash
cd android
gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 游戏流程

1. **玩家A**：打开 APP，点击"创建房间"，获得 6 位房间号
2. **玩家B**：打开 APP，输入房间号，点击"加入房间"
3. 游戏自动开始，黑棋（创建者）先手
4. 轮流落子，先六子连珠者获胜
5. 游戏结束后可选择复赛（颜色互换）

## 技术栈

| 组件 | 技术 |
|------|------|
| 服务器 | Java 17, Java-WebSocket, Gson |
| 客户端 | Android (Java), OkHttp WebSocket |
| 通信协议 | JSON over WebSocket |
| 公网暴露 | ngrok HTTP 隧道 |

## 协议说明

所有消息格式：`{"type":"...", "data":{...}}`

详见 `server/src/main/java/com/sixchess/server/Message.java`

## 服务器环境要求

- Java 17+
- ngrok（用于公网访问）
- 端口 8080（可自定义）

## 开发

### 服务器

```bash
cd server
gradlew build
java -jar build/libs/sixchess-server-1.0.0.jar
```

### Android 客户端

```bash
cd android
gradlew assembleDebug
```

## 许可证

MIT License