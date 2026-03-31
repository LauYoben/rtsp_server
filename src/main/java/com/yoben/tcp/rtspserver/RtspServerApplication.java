package com.yoben.tcp.rtspserver;

import com.yoben.tcp.rtspserver.server.RtspServer;

/**
 * @Description 桌面端 RTSP 服务器应用启动入口。
 * @Author Yoben
 * @Since 2026-03-26 14:36:00
 */
public final class RtspServerApplication {

    private RtspServerApplication() {
    }

    public static void main(String[] args) throws InterruptedException {
        int port = Integer.parseInt(System.getProperty("rtsp.port", "8554"));
        new RtspServer(port).start();
    }
}
