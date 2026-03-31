package com.yoben.tcp.rtspserver.rtp.format;

import com.yoben.tcp.rtspserver.rtp.RtpPacketizer;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description 通用 RTP 打包器基类。
 * @Author Yoben
 * @Since 2026-03-28 14:33:00
 */
abstract class AbstractPacketizer implements RtpPacketizer {

    protected final int payloadType;

    protected AbstractPacketizer(int payloadType) {
        this.payloadType = payloadType;
    }

    protected List<byte[]> split(byte[] payload, int mtu) {
        List<byte[]> packets = new ArrayList<>();
        int offset = 0;
        while (offset < payload.length) {
            int size = Math.min(mtu, payload.length - offset);
            byte[] chunk = new byte[size];
            System.arraycopy(payload, offset, chunk, 0, size);
            packets.add(chunk);
            offset += size;
        }
        return packets;
    }
}
