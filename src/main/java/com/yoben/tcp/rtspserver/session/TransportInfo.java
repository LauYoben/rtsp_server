package com.yoben.tcp.rtspserver.session;

/**
 * @Description Transport 头协商结果记录类型。
 * @Author Yoben
 * @Since 2026-03-23 09:17:00
 */
public record TransportInfo(
        TransportMode mode,
        int clientRtpPort,
        int clientRtcpPort,
        int interleavedRtpChannel,
        int interleavedRtcpChannel) {
}
