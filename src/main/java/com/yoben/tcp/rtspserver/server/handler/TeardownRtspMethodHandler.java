package com.yoben.tcp.rtspserver.server.handler;

import com.yoben.tcp.rtspserver.server.RtspServerSupport;
import com.yoben.tcp.rtspserver.session.RtspSession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import java.util.Optional;

/**
 * @Description RTSP TEARDOWN 方法处理器。
 * @Author Yoben
 * @Since 2026-03-16 18:29:00
 */
public class TeardownRtspMethodHandler extends AbstractRtspMethodHandler {

    public TeardownRtspMethodHandler(RtspServerSupport support) {
        super(support);
    }

    @Override
    protected boolean supports(FullHttpRequest request) {
        return RtspMethods.TEARDOWN.equals(request.method());
    }

    @Override
    protected void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        Optional<RtspSession> session = support.requireSession(request);
        if (session.isEmpty()) {
            support.writeResponse(ctx, request, support.empty(RtspResponseStatuses.SESSION_NOT_FOUND));
            return;
        }
        support.removeSession(session.get().sessionId());
        support.writeResponse(ctx, request, support.empty(RtspResponseStatuses.OK));
    }
}
