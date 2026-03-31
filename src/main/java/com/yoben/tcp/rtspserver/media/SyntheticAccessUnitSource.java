package com.yoben.tcp.rtspserver.media;

import java.nio.charset.StandardCharsets;

/**
 * @Description 用于生成媒体的合成访问单元数据源。
 * @Author Yoben
 * @Since 2026-03-29 09:08:00
 */
public class SyntheticAccessUnitSource {

    public byte[] nextAccessUnit(MediaTrack track, long frameIndex) {
        return switch (track.codec()) {
            case H264 -> createH264(frameIndex);
            case H265 -> createH265(frameIndex);
            case MJPEG -> createJpeg(frameIndex);
            case AAC, VP8, VP9, AV1, MPEG4_ES, MPEG2_TS -> createGeneric(track.codec().name(), frameIndex);
        };
    }

    private byte[] createH264(long frameIndex) {
        byte[] payload = ("H264-FRAME-" + frameIndex).getBytes(StandardCharsets.US_ASCII);
        byte[] data = new byte[payload.length + 1];
        data[0] = 0x65;
        System.arraycopy(payload, 0, data, 1, payload.length);
        return data;
    }

    private byte[] createH265(long frameIndex) {
        byte[] payload = ("H265-FRAME-" + frameIndex).getBytes(StandardCharsets.US_ASCII);
        byte[] data = new byte[payload.length + 2];
        data[0] = 0x26;
        data[1] = 0x01;
        System.arraycopy(payload, 0, data, 2, payload.length);
        return data;
    }

    private byte[] createJpeg(long frameIndex) {
        byte[] payload = ("JPEG-FRAME-" + frameIndex).getBytes(StandardCharsets.US_ASCII);
        byte[] data = new byte[payload.length + 4];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        System.arraycopy(payload, 0, data, 2, payload.length);
        data[data.length - 2] = (byte) 0xFF;
        data[data.length - 1] = (byte) 0xD9;
        return data;
    }

    private byte[] createGeneric(String codec, long frameIndex) {
        return (codec + "-FRAME-" + frameIndex).getBytes(StandardCharsets.US_ASCII);
    }
}