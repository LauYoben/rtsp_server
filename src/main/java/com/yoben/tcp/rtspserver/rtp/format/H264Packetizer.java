package com.yoben.tcp.rtspserver.rtp.format;

import java.util.ArrayList;
import java.util.List;

public class H264Packetizer extends AbstractPacketizer {

    public H264Packetizer(int payloadType) {
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
        byte nalHeader = nalUnit[0];
        byte fuIndicator = (byte) ((nalHeader & 0xE0) | 28);
        byte nalType = (byte) (nalHeader & 0x1F);
        int maxPayload = mtu - 2;
        int offset = 1;
        boolean start = true;
        while (offset < nalUnit.length) {
            int size = Math.min(maxPayload, nalUnit.length - offset);
            byte fuHeader = nalType;
            if (start) {
                fuHeader |= (byte) 0x80;
            }
            if (offset + size >= nalUnit.length) {
                fuHeader |= 0x40;
            }
            byte[] fragment = new byte[size + 2];
            fragment[0] = fuIndicator;
            fragment[1] = fuHeader;
            System.arraycopy(nalUnit, offset, fragment, 2, size);
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