package com.yoben.tcp.rtspserver.rtp.format;

import java.util.List;

/**
 * @Description 通用 RTP 打包器实现。
 * @Author Yoben
 * @Since 2026-03-15 10:43:00
 */
public class GenericPacketizer extends AbstractPacketizer {

    public GenericPacketizer(int payloadType) {
        super(payloadType);
    }

    @Override
    public List<byte[]> packetize(byte[] accessUnit, int mtu) {
        return split(accessUnit, mtu);
    }
}
