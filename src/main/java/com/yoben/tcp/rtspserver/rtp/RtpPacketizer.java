package com.yoben.tcp.rtspserver.rtp;

import java.util.List;

/**
 * @Description RTP 打包器接口定义。
 * @Author Yoben
 * @Since 2026-03-22 11:31:00
 */
public interface RtpPacketizer {

    List<byte[]> packetize(byte[] accessUnit, int mtu);
}
