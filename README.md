# Netty RTSP 服务端

基于 Netty 的 RTSP/RTP/RTCP 服务端，当前已经可以和本仓库配套的播放器稳定联调，支持本地文件动态发布、UDP 与 RTSP TCP interleaved 两种传输方式。

## 当前能力

- 支持 RTSP `OPTIONS / DESCRIBE / SETUP / PLAY / PAUSE / TEARDOWN`
- 支持 `RTP over UDP` 与 `RTP over RTSP (TCP interleaved)`
- 支持标准 RTCP Sender Report / BYE
- 支持动态文件地址：通过 `file` 查询参数发布本地媒体
- 支持视频轨：`H264`、`H265/HEVC`、`MJPEG`
- 支持音频轨：`AAC`
- 对 MP4 文件可自动提取视频轨和音频轨，并生成对应 SDP

## 环境

- JDK: `jdk-22.0.2`
- Maven: `maven-3.9.11`

## 构建

```powershell
   mvn -DskipTests clean compile
```

## 启动

```powershell
java -cp target\classes;target\lib\* com.yoben.tcp.rtspserver.RtspServerApplication
```

如果已经打包成 jar，也可以直接运行：

```powershell
java -jar target\rtsp-server-1.0.0.jar
```

默认监听：

- RTSP: `0.0.0.0:8554`

## 地址示例

### 动态发布本地 MP4

```text
rtsp://127.0.0.1:8554/live?file=C:\Users\yoben\Videos\2026-01-29_164312_322.mp4
```

### 固定示例流

```text
rtsp://127.0.0.1:8554/live/h264
rtsp://127.0.0.1:8554/live/h265
rtsp://127.0.0.1:8554/live/mjpeg
```

## 关键实现点

- `src/main/java/com/yoben/tcp/rtspserver/server`
  - RTSP 请求处理、会话管理、Transport 协商
- `src/main/java/com/yoben/tcp/rtspserver/transport`
  - UDP / TCP interleaved RTP 发送
- `src/main/java/com/yoben/tcp/rtspserver/media/source/Mp4AccessUnitSource.java`
  - MP4 音视频轨提取、H264/H265 参数集补齐、AAC SDP 参数生成
- `src/main/java/com/yoben/tcp/rtspserver/session/RtspSession.java`
  - 每个 track 独立节奏调度，避免音视频互相拖慢

## 当前默认行为

- 服务端支持 UDP `SETUP`
- UDP transport 已使用独立 Datagram Bootstrap，避免卡住 RTSP `SETUP`
- MP4 音频轨会优先按真实 AAC 配置生成 `fmtp config`
- H264/H265 会尽量补齐参数集并提高首帧可解码率

## 日志说明

默认保留这些有价值的日志：

- `RTSP server started ...`
- `SETUP failed for uri=... transport=...`
- `Unhandled RTSP pipeline exception ...`

如果出现 UDP/SETUP 问题，优先查看服务端控制台的 `SETUP failed ...` 异常堆栈。

## 说明

当前这版更偏“联调与预览型 RTSP 服务端”，适合本地文件流化、协议验证和播放器联调。如果后续要扩展成生产场景，可以继续补：

- 鉴权
- 更完整的 RTCP 接收端统计
- 多客户端并发策略
- 更丰富的媒体源接入
