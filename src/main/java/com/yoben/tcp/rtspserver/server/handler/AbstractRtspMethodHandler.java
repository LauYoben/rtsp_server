package com.yoben.tcp.rtspserver.server.handler;

import com.yoben.tcp.rtspserver.server.RtspServerSupport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.ReferenceCountUtil;

/**
 * @Description RTSP 方法处理器基类。
 * @Author Yoben
 * @Since 2026-03-30 17:41:00
 */
public abstract class AbstractRtspMethodHandler extends ChannelInboundHandlerAdapter {

    protected final RtspServerSupport support;

    protected AbstractRtspMethodHandler(RtspServerSupport support) {
        this.support = support;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (!supports(request)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            handle(ctx, request);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    protected abstract boolean supports(FullHttpRequest request);

    protected abstract void handle(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception;
}
