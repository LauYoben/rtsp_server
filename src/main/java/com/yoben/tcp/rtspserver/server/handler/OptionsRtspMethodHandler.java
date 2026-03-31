package com.yoben.tcp.rtspserver.server.handler;

import com.yoben.tcp.rtspserver.server.RtspServerSupport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.rtsp.RtspHeaderNames;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;

/**
 * @Description RTSP OPTIONS 方法处理器。
 * @Author Yoben
 * @Since 2026-03-17 13:51:00
 */
public class OptionsRtspMethodHandler extends AbstractRtspMethodHandler {

    public OptionsRtspMethodHandler(RtspServerSupport support) {
        super(support);
    }

    @Override
    protected boolean supports(FullHttpRequest request) {
        return RtspMethods.OPTIONS.equals(request.method());
    }

    @Override
    protected void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = support.empty(RtspResponseStatuses.OK);
        response.headers().set(RtspHeaderNames.PUBLIC,
                "OPTIONS, DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN, GET_PARAMETER, SET_PARAMETER, ANNOUNCE");
        support.writeResponse(ctx, request, response);
    }
}
