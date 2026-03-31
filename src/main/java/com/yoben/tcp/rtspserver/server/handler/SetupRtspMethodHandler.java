package com.yoben.tcp.rtspserver.server.handler;

import com.yoben.tcp.rtspserver.media.MediaSession;
import com.yoben.tcp.rtspserver.media.MediaTrack;
import com.yoben.tcp.rtspserver.server.RtspServerSupport;
import com.yoben.tcp.rtspserver.session.RtspSession;
import com.yoben.tcp.rtspserver.session.TransportInfo;
import com.yoben.tcp.rtspserver.transport.RtpTransport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.rtsp.RtspHeaderNames;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @Description RTSP SETUP 方法处理器。
 * @Author Yoben
 * @Since 2026-03-12 15:24:00
 */
public class SetupRtspMethodHandler extends AbstractRtspMethodHandler {

    private static final Logger log = LoggerFactory.getLogger(SetupRtspMethodHandler.class);

    public SetupRtspMethodHandler(RtspServerSupport support) {
        super(support);
    }

    @Override
    protected boolean supports(FullHttpRequest request) {
        return RtspMethods.SETUP.equals(request.method());
    }

    @Override
    protected void handle(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Optional<MediaTrack> trackOptional = support.findTrack(request.uri());
        if (trackOptional.isEmpty()) {
            support.writeResponse(ctx, request, support.empty(RtspResponseStatuses.NOT_FOUND));
            return;
        }

        String transportHeader = request.headers().get(RtspHeaderNames.TRANSPORT);
        if (transportHeader == null || transportHeader.isBlank()) {
            support.writeResponse(ctx, request, support.empty(RtspResponseStatuses.BAD_REQUEST));
            return;
        }

        try {
            TransportInfo transportInfo = support.parseTransport(transportHeader);
            MediaTrack track = trackOptional.get();
            MediaSession mediaSession = support.findMediaSession(support.stripTrackSuffix(request.uri())).orElseThrow();
            RtspSession session = support.resolveSession(request, mediaSession);
            RtpTransport transport = support.createTransport(ctx, transportInfo);
            session.bindTrack(track, transport, ctx.channel().eventLoop());

            FullHttpResponse response = support.empty(RtspResponseStatuses.OK);
            response.headers().set(RtspHeaderNames.SESSION, session.sessionId() + ";timeout=60");
            response.headers().set(RtspHeaderNames.TRANSPORT, transport.transportHeaderValue());
            support.writeResponse(ctx, request, response);
        } catch (Exception ex) {
            log.error("SETUP failed for uri={} transport={}", request.uri(), transportHeader, ex);
            support.writeResponse(ctx, request, support.empty(RtspResponseStatuses.INTERNAL_SERVER_ERROR));
        }
    }
}