package com.yoben.tcp.rtspserver.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.rtsp.RtspResponseStatuses.METHOD_NOT_ALLOWED;

/**
 * @Description 顶层 RTSP 请求分发处理器。
 * @Author Yoben
 * @Since 2026-03-24 14:39:00
 */
public class RtspServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(RtspServerHandler.class);

    private final RtspServerSupport support;

    public RtspServerHandler(RtspServerSupport support) {
        this.support = support;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        support.writeResponse(ctx, request, support.empty(METHOD_NOT_ALLOWED));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent idle && idle.state() == IdleState.ALL_IDLE) {
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Unhandled RTSP pipeline exception", cause);
        ctx.close();
    }
}