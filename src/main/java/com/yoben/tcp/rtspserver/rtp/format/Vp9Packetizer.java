package com.yoben.tcp.rtspserver.rtp.format;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description VP9 RTP 打包器实现。
 * @Author Yoben
 * @Since 2026-03-14 15:21:00
 */
public class Vp9Packetizer extends AbstractPacketizer {

    public Vp9Packetizer(int payloadType) {
        super(payloadType);
    }

    @Override
    public List<byte[]> packetize(byte[] accessUnit, int mtu) {
        List<byte[]> packets = new ArrayList<>();
        int maxPayload = mtu - 1;
        int offset = 0;
        boolean first = true;
        while (offset < accessUnit.length) {
            int size = Math.min(maxPayload, accessUnit.length - offset);
            byte[] packet = new byte[size + 1];
            packet[0] = first ? (byte) 0x88 : (byte) 0x80;
            System.arraycopy(accessUnit, offset, packet, 1, size);
            packets.add(packet);
            offset += size;
            first = false;
        }
        return packets;
    }
}
