package com.yoben.tcp.rtspserver.rtp.format;

import java.util.List;

/**
 * @Description AAC RTP 打包器实现。
 * @Author Yoben
 * @Since 2026-03-24 11:28:00
 */
public final class AacPacketizer extends AbstractPacketizer {

    public AacPacketizer(int payloadType) {
        super(payloadType);
    }

    @Override
    public List<byte[]> packetize(byte[] accessUnit, int mtu) {
        if (accessUnit == null || accessUnit.length == 0) {
            return List.of();
        }
        if (accessUnit.length + 4 > mtu) {
            throw new IllegalArgumentException("AAC access unit exceeds MTU: " + accessUnit.length);
        }
        byte[] payload = new byte[accessUnit.length + 4];
        payload[0] = 0x00;
        payload[1] = 0x10;
        payload[2] = (byte) ((accessUnit.length >> 5) & 0xFF);
        payload[3] = (byte) ((accessUnit.length & 0x1F) << 3);
        System.arraycopy(accessUnit, 0, payload, 4, accessUnit.length);
        return List.of(payload);
    }
}