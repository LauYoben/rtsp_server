package com.yoben.tcp.rtspserver.transport;

import com.yoben.tcp.rtspserver.rtp.RtpPacket;

/**
 * @Description RTP 传输接口定义。
 * @Author Yoben
 * @Since 2026-03-14 18:32:00
 */
public interface RtpTransport extends AutoCloseable {

    void sendRtp(RtpPacket packet);

    void sendRtcp(byte[] rtcpPayload);

    String transportHeaderValue();

    @Override
    void close();
}
