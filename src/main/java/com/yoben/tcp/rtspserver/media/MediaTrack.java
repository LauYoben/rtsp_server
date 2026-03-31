package com.yoben.tcp.rtspserver.media;

import com.yoben.tcp.rtspserver.rtp.RtpPacketizer;
import com.yoben.tcp.rtspserver.rtp.format.AacPacketizer;
import com.yoben.tcp.rtspserver.rtp.format.Av1Packetizer;
import com.yoben.tcp.rtspserver.rtp.format.GenericPacketizer;
import com.yoben.tcp.rtspserver.rtp.format.H264Packetizer;
import com.yoben.tcp.rtspserver.rtp.format.H265Packetizer;
import com.yoben.tcp.rtspserver.rtp.format.JpegPacketizer;
import com.yoben.tcp.rtspserver.rtp.format.Vp8Packetizer;
import com.yoben.tcp.rtspserver.rtp.format.Vp9Packetizer;

/**
 * @Description 媒体轨道元数据记录类型。
 * @Author Yoben
 * @Since 2026-03-22 18:20:00
 */
public record MediaTrack(
        int trackId,
        String mediaType,
        MediaCodec codec,
        int frameRate,
        String control,
        int clockRate,
        int channels,
        String fmtpOverride) {

    public MediaTrack(int trackId, String mediaType, MediaCodec codec, int frameRate, String control) {
        this(trackId, mediaType, codec, frameRate, control, codec.clockRate(), 1, null);
    }

    public RtpPacketizer createPacketizer() {
        return switch (codec) {
            case H264 -> new H264Packetizer(codec.payloadType());
            case H265 -> new H265Packetizer(codec.payloadType());
            case MJPEG -> new JpegPacketizer(codec.payloadType());
            case VP8 -> new Vp8Packetizer(codec.payloadType());
            case VP9 -> new Vp9Packetizer(codec.payloadType());
            case AV1 -> new Av1Packetizer(codec.payloadType());
            case AAC -> new AacPacketizer(codec.payloadType());
            case MPEG4_ES, MPEG2_TS -> new GenericPacketizer(codec.payloadType());
        };
    }
}