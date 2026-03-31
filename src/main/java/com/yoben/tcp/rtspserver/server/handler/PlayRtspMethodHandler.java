package com.yoben.tcp.rtspserver.server.handler;

import com.yoben.tcp.rtspserver.server.RtspServerSupport;
import com.yoben.tcp.rtspserver.session.RtspSession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.rtsp.RtspHeaderNames;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;

import java.util.Optional;

/**
 * @Description RTSP PLAY 方法处理器。
 * @Author Yoben
 * @Since 2026-03-29 12:19:00
 */
public class PlayRtspMethodHandler extends AbstractRtspMethodHandler {

    public PlayRtspMethodHandler(RtspServerSupport support) {
        super(support);
    }

    @Override
    protected boolean supports(FullHttpRequest request) {
        return RtspMethods.PLAY.equals(request.method());
    }

    @Override
    protected void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        Optional<RtspSession> session = support.requireSession(request);
        if (session.isEmpty()) {
            support.writeResponse(ctx, request, support.empty(RtspResponseStatuses.SESSION_NOT_FOUND));
            return;
        }
        String seekOffsetHeader = request.headers().get("X-Seek-Offset");
        if (seekOffsetHeader != null && !seekOffsetHeader.isBlank()) {
            session.get().seekRelativeSeconds(parseDouble(seekOffsetHeader, 0.0));
        }
        session.get().start();
        FullHttpResponse response = support.empty(RtspResponseStatuses.OK);
        response.headers().set(RtspHeaderNames.SESSION, session.get().sessionId());
        response.headers().set(RtspHeaderNames.RTP_INFO, "url=" + request.uri() + ";seq=0;rtptime=0");
        support.writeResponse(ctx, request, response);
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
}