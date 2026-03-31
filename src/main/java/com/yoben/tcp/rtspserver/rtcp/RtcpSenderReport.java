package com.yoben.tcp.rtspserver.rtcp;

import java.nio.ByteBuffer;
import java.time.Instant;

/**
 * @Description RTCP Sender Report 报文模型。
 * @Author Yoben
 * @Since 2026-03-20 18:23:00
 */
public class RtcpSenderReport implements RtcpPacket {

    private final int ssrc;
    private final long rtpTimestamp;
    private final long packetCount;
    private final long octetCount;

    public RtcpSenderReport(int ssrc, long rtpTimestamp, long packetCount, long octetCount) {
        this.ssrc = ssrc;
        this.rtpTimestamp = rtpTimestamp;
        this.packetCount = packetCount;
        this.octetCount = octetCount;
    }

    @Override
    public byte[] toBytes() {
        Instant now = Instant.now();
        long seconds = now.getEpochSecond() + 2_208_988_800L;
        long fraction = (now.getNano() * 0x1_0000_0000L) / 1_000_000_000L;

        ByteBuffer buffer = ByteBuffer.allocate(28);
        buffer.put((byte) 0x80);
        buffer.put((byte) 200);
        buffer.putShort((short) 6);
        buffer.putInt(ssrc);
        buffer.putInt((int) seconds);
        buffer.putInt((int) fraction);
        buffer.putInt((int) rtpTimestamp);
        buffer.putInt((int) packetCount);
        buffer.putInt((int) octetCount);
        return buffer.array();
    }
}
