package com.yoben.tcp.rtspserver.media;

import com.yoben.tcp.rtspserver.media.source.AccessUnitSource;

/**
 * @Description RTSP SDP 响应构建器。
 * @Author Yoben
 * @Since 2026-03-26 11:25:00
 */
public final class SdpBuilder {

    private SdpBuilder() {
    }

    public static String build(MediaSession session, String controlBase, AccessUnitSource source) {
        StringBuilder builder = new StringBuilder()
                .append("v=0\r\n")
                .append("o=- 0 0 IN IP4 127.0.0.1\r\n")
                .append("s=").append(session.sessionName()).append("\r\n")
                .append("t=0 0\r\n")
                .append("a=control:").append(controlBase).append("\r\n");

        for (MediaTrack track : session.tracks()) {
            builder.append("m=")
                    .append(track.mediaType())
                    .append(" 0 RTP/AVP ")
                    .append(track.codec().payloadType())
                    .append("\r\n")
                    .append("c=IN IP4 0.0.0.0\r\n")
                    .append("a=rtpmap:")
                    .append(track.codec().payloadType())
                    .append(' ')
                    .append(track.codec().encodingName())
                    .append('/')
                    .append(track.clockRate());
            if ("audio".equals(track.mediaType()) && track.channels() > 1) {
                builder.append('/').append(track.channels());
            }
            builder.append("\r\n");

            String baseFmtp = track.fmtpOverride() != null && !track.fmtpOverride().isBlank()
                    ? track.fmtpOverride()
                    : track.codec().fmtp();
            String fmtp = source.describeFmtp(session, track, baseFmtp);
            if (fmtp != null && !fmtp.isBlank()) {
                builder.append("a=fmtp:")
                        .append(track.codec().payloadType())
                        .append(' ')
                        .append(fmtp)
                        .append("\r\n");
            }
            if ("video".equals(track.mediaType())) {
                builder.append("a=framerate:")
                        .append(track.frameRate())
                        .append("\r\n");
            }
            builder.append("a=control:")
                    .append(appendTrackControl(controlBase, track.control()))
                    .append("\r\n");
        }
        return builder.toString();
    }

    private static String appendTrackControl(String controlBase, String trackControl) {
        if (controlBase.endsWith("/")) {
            return controlBase + trackControl;
        }
        int queryIndex = controlBase.indexOf('?');
        if (queryIndex < 0) {
            return controlBase + "/" + trackControl;
        }
        String path = controlBase.substring(0, queryIndex);
        String query = controlBase.substring(queryIndex);
        return path + "/" + trackControl + query;
    }
}