package com.yoben.tcp.rtspserver.rtp.format;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description H265 RTP 打包器实现。
 * @Author Yoben
 * @Since 2026-03-23 16:06:00
 */
public class H265Packetizer extends AbstractPacketizer {

    public H265Packetizer(int payloadType) {
        super(payloadType);
    }

    @Override
    public List<byte[]> packetize(byte[] accessUnit, int mtu) {
        List<byte[]> nalUnits = extractAnnexBNalUnits(accessUnit);
        if (nalUnits.isEmpty()) {
            nalUnits = List.of(accessUnit);
        }

        List<byte[]> packets = new ArrayList<>();
        for (byte[] nalUnit : nalUnits) {
            if (nalUnit.length <= mtu) {
                packets.add(nalUnit);
                continue;
            }
            packets.addAll(fragmentNalUnit(nalUnit, mtu));
        }
        return packets;
    }

    private List<byte[]> fragmentNalUnit(byte[] nalUnit, int mtu) {
        List<byte[]> packets = new ArrayList<>();
        byte header0 = nalUnit[0];
        byte header1 = nalUnit[1];
        byte fuIndicator0 = (byte) ((header0 & 0x81) | (49 << 1));
        byte fuIndicator1 = header1;
        byte fuHeader = (byte) ((header0 >> 1) & 0x3F);
        int maxPayload = mtu - 3;
        int offset = 2;
        boolean start = true;
        while (offset < nalUnit.length) {
            int size = Math.min(maxPayload, nalUnit.length - offset);
            byte[] fragment = new byte[size + 3];
            fragment[0] = fuIndicator0;
            fragment[1] = fuIndicator1;
            fragment[2] = fuHeader;
            if (start) {
                fragment[2] |= (byte) 0x80;
            }
            if (offset + size >= nalUnit.length) {
                fragment[2] |= 0x40;
            }
            System.arraycopy(nalUnit, offset, fragment, 3, size);
            packets.add(fragment);
            offset += size;
            start = false;
        }
        return packets;
    }

    private List<byte[]> extractAnnexBNalUnits(byte[] accessUnit) {
        List<byte[]> nalUnits = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < accessUnit.length - 3; i++) {
            int startCodeSize = startCodeSize(accessUnit, i);
            if (startCodeSize > 0) {
                starts.add(i);
                i += startCodeSize - 1;
            }
        }
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int payloadStart = start + startCodeSize(accessUnit, start);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : accessUnit.length;
            if (payloadStart < end) {
                byte[] nalUnit = new byte[end - payloadStart];
                System.arraycopy(accessUnit, payloadStart, nalUnit, 0, nalUnit.length);
                nalUnits.add(nalUnit);
            }
        }
        return nalUnits;
    }

    private int startCodeSize(byte[] bytes, int index) {
        if (index + 3 < bytes.length
                && bytes[index] == 0x00
                && bytes[index + 1] == 0x00
                && bytes[index + 2] == 0x00
                && bytes[index + 3] == 0x01) {
            return 4;
        }
        if (index + 2 < bytes.length
                && bytes[index] == 0x00
                && bytes[index + 1] == 0x00
                && bytes[index + 2] == 0x01) {
            return 3;
        }
        return 0;
    }
}
