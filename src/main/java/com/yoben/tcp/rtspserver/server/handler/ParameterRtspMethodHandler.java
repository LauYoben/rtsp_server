package com.yoben.tcp.rtspserver.server.handler;

import com.yoben.tcp.rtspserver.server.RtspServerSupport;
import com.yoben.tcp.rtspserver.session.RtspSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.rtsp.RtspHeaderNames;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * @Description RTSP 参数消息处理器。
 * @Author Yoben
 * @Since 2026-03-21 16:09:00
 */
public class ParameterRtspMethodHandler extends AbstractRtspMethodHandler {

    public ParameterRtspMethodHandler(RtspServerSupport support) {
        super(support);
    }

    @Override
    protected boolean supports(FullHttpRequest request) {
        return RtspMethods.GET_PARAMETER.equals(request.method())
                || RtspMethods.SET_PARAMETER.equals(request.method())
                || RtspMethods.ANNOUNCE.equals(request.method());
    }

    @Override
    protected void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        Optional<RtspSession> sessionOptional = support.requireSession(request);
        if (sessionOptional.isEmpty()) {
            support.writeResponse(ctx, request, support.empty(RtspResponseStatuses.SESSION_NOT_FOUND));
            return;
        }
        RtspSession session = sessionOptional.get();

        if (RtspMethods.GET_PARAMETER.equals(request.method())) {
            int lastId = parseInt(request.headers().get("X-Live-Last-Id"), 0);
            List<RtspSession.LiveMessage> messages = session.messagesAfter(lastId);
            StringBuilder payload = new StringBuilder();
            int highestId = lastId;
            for (RtspSession.LiveMessage message : messages) {
                payload.append(message.id())
                        .append('|')
                        .append(message.sender())
                        .append('|')
                        .append(message.message().replace("\r", " ").replace("\n", " "))
                        .append("\r\n");
                highestId = Math.max(highestId, message.id());
            }
            ByteBuf content = Unpooled.copiedBuffer(payload.toString(), StandardCharsets.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK, content);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            response.headers().set(RtspHeaderNames.SESSION, session.sessionId());
            response.headers().set("X-Live-Last-Id", Integer.toString(highestId));
            support.writeResponse(ctx, request, response);
            return;
        }

        String body = request.content().toString(StandardCharsets.UTF_8).trim();
        if (!body.isBlank()) {
            session.addLiveMessage("client", body);
        }
        FullHttpResponse response = support.empty(RtspResponseStatuses.OK);
        response.headers().set(RtspHeaderNames.SESSION, session.sessionId());
        support.writeResponse(ctx, request, response);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}