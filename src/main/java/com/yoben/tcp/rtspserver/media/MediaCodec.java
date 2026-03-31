package com.yoben.tcp.rtspserver.media;

/**
 * @Description 支持的媒体编码枚举。
 * @Author Yoben
 * @Since 2026-03-10 09:05:00
 */
public enum MediaCodec {
    H264("H264", 90000, 96, "profile-level-id=42e01f;packetization-mode=1"),
    H265("H265", 90000, 97, "profile-space=0;profile-id=1;tier-flag=0;level-id=120"),
    MJPEG("JPEG", 90000, 26, null),
    MPEG4_ES("MP4V-ES", 90000, 98, "profile-level-id=1"),
    MPEG2_TS("MP2T", 90000, 33, null),
    VP8("VP8", 90000, 99, null),
    VP9("VP9", 90000, 100, "profile-id=0"),
    AV1("AV1X", 90000, 101, "profile=0;level-idx=8;tier=0"),
    AAC("MPEG4-GENERIC", 48000, 110, null);

    private final String encodingName;
    private final int clockRate;
    private final int payloadType;
    private final String fmtp;

    MediaCodec(String encodingName, int clockRate, int payloadType, String fmtp) {
        this.encodingName = encodingName;
        this.clockRate = clockRate;
        this.payloadType = payloadType;
        this.fmtp = fmtp;
    }

    public String encodingName() {
        return encodingName;
    }

    public int clockRate() {
        return clockRate;
    }

    public int payloadType() {
        return payloadType;
    }

    public String fmtp() {
        return fmtp;
    }
}