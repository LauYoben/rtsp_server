package com.yoben.tcp.rtspserver.server.handler;

import com.yoben.tcp.rtspserver.media.MediaSession;
import com.yoben.tcp.rtspserver.server.RtspServerSupport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import java.util.Optional;

/**
 * @Description RTSP DESCRIBE 方法处理器。
 * @Author Yoben
 * @Since 2026-03-13 10:46:00
 */
public class DescribeRtspMethodHandler extends AbstractRtspMethodHandler {

    public DescribeRtspMethodHandler(RtspServerSupport support) {
        super(support);
    }

    @Override
    protected boolean supports(FullHttpRequest request) {
        return RtspMethods.DESCRIBE.equals(request.method());
    }

    @Override
    protected void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        Optional<MediaSession> mediaSession = support.findMediaSession(request.uri());
        if (mediaSession.isEmpty()) {
            support.writeResponse(ctx, request, support.empty(RtspResponseStatuses.NOT_FOUND));
            return;
        }
        String sdp = support.buildSdp(mediaSession.get(), request.uri());
        support.writeResponse(ctx, request, support.sdpResponse(sdp, request.uri()));
    }
}
