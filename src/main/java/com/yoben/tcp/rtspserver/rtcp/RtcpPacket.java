package com.yoben.tcp.rtspserver.rtcp;

/**
 * @Description RTCP 报文接口定义。
 * @Author Yoben
 * @Since 2026-03-16 15:18:00
 */
public interface RtcpPacket {

    byte[] toBytes();
}
