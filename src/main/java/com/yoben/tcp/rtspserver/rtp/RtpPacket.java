package com.yoben.tcp.rtspserver.rtp;

import java.nio.ByteBuffer;

/**
 * @Description RTP 数据包记录类型。
 * @Author Yoben
 * @Since 2026-03-18 18:26:00
 */
public record RtpPacket(
        int payloadType,
        int sequenceNumber,
        long timestamp,
        int ssrc,
        boolean marker,
        byte[] payload) {

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(12 + payload.length);
        buffer.put((byte) 0x80);
        buffer.put((byte) ((marker ? 0x80 : 0x00) | (payloadType & 0x7F)));
        buffer.putShort((short) sequenceNumber);
        buffer.putInt((int) timestamp);
        buffer.putInt(ssrc);
        buffer.put(payload);
        return buffer.array();
    }
}
