package com.yoben.tcp.rtspserver.rtp.format;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description JPEG RTP 打包器实现。
 * @Author Yoben
 * @Since 2026-03-27 09:11:00
 */
public class JpegPacketizer extends AbstractPacketizer {

    public JpegPacketizer(int payloadType) {
        super(payloadType);
    }

    @Override
    public List<byte[]> packetize(byte[] accessUnit, int mtu) {
        List<byte[]> packets = new ArrayList<>();
        int maxPayload = mtu - 8;
        int offset = 0;
        while (offset < accessUnit.length) {
            int size = Math.min(maxPayload, accessUnit.length - offset);
            byte[] packet = new byte[size + 8];
            packet[1] = (byte) ((offset >> 16) & 0xFF);
            packet[2] = (byte) ((offset >> 8) & 0xFF);
            packet[3] = (byte) (offset & 0xFF);
            packet[4] = 1;
            packet[5] = 40;
            System.arraycopy(accessUnit, offset, packet, 8, size);
            packets.add(packet);
            offset += size;
        }
        return packets;
    }
}
