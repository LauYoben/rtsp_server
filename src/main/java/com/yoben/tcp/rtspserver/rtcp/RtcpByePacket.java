package com.yoben.tcp.rtspserver.rtcp;

import java.nio.ByteBuffer;

/**
 * @Description RTCP BYE 报文模型。
 * @Author Yoben
 * @Since 2026-03-12 12:13:00
 */
public class RtcpByePacket implements RtcpPacket {

    private final int ssrc;

    public RtcpByePacket(int ssrc) {
        this.ssrc = ssrc;
    }

    @Override
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put((byte) 0x81);
        buffer.put((byte) 203);
        buffer.putShort((short) 1);
        buffer.putInt(ssrc);
        return buffer.array();
    }
}
