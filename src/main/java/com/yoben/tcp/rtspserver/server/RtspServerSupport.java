package com.yoben.tcp.rtspserver.server;

import com.yoben.tcp.rtspserver.media.MediaRegistry;
import com.yoben.tcp.rtspserver.media.MediaSession;
import com.yoben.tcp.rtspserver.media.MediaTrack;
import com.yoben.tcp.rtspserver.media.SdpBuilder;
import com.yoben.tcp.rtspserver.media.source.AccessUnitSourceFactory;
import com.yoben.tcp.rtspserver.session.RtspSession;
import com.yoben.tcp.rtspserver.session.RtspSessionManager;
import com.yoben.tcp.rtspserver.session.TransportInfo;
import com.yoben.tcp.rtspserver.session.TransportMode;
import com.yoben.tcp.rtspserver.transport.InterleavedRtpTransport;
import com.yoben.tcp.rtspserver.transport.RtpTransport;
import com.yoben.tcp.rtspserver.transport.UdpRtpTransport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.rtsp.RtspHeaderNames;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * @Description RTSP 服务器共享支持工具类。
 * @Author Yoben
 * @Since 2026-03-11 10:49:00
 */
public class RtspServerSupport {

    public static final String SERVER_NAME = "Netty-RTSP-Server";

    private final RtspSessionManager sessionManager;
    private final MediaRegistry mediaRegistry = new MediaRegistry();
    private final AccessUnitSourceFactory accessUnitSourceFactory = new AccessUnitSourceFactory();

    public RtspServerSupport(RtspSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public Optional<MediaSession> findMediaSession(String uri) {
        return mediaRegistry.find(uri);
    }

    public Optional<MediaTrack> findTrack(String uri) {
        return mediaRegistry.findTrack(uri);
    }

    public String buildSdp(MediaSession mediaSession, String uri) {
        return SdpBuilder.build(mediaSession, uri, accessUnitSourceFactory.create(mediaSession));
    }

    public RtspSession resolveSession(FullHttpRequest request, MediaSession mediaSession) {
        String sessionId = request.headers().get(RtspHeaderNames.SESSION);
        if (sessionId == null) {
            return sessionManager.create(mediaSession);
        }
        String normalized = sessionId.split(";", 2)[0].trim();
        return sessionManager.find(normalized).orElseGet(() -> sessionManager.create(mediaSession));
    }

    public Optional<RtspSession> requireSession(FullHttpRequest request) {
        String sessionHeader = request.headers().get(RtspHeaderNames.SESSION);
        if (sessionHeader == null) {
            return Optional.empty();
        }
        String sessionId = sessionHeader.split(";", 2)[0].trim();
        return sessionManager.find(sessionId);
    }

    public void removeSession(String sessionId) {
        sessionManager.remove(sessionId);
    }

    public RtpTransport createTransport(ChannelHandlerContext ctx, TransportInfo transportInfo) throws InterruptedException {
        if (transportInfo.mode() == TransportMode.TCP_INTERLEAVED) {
            return new InterleavedRtpTransport(
                    ctx.channel(),
                    transportInfo.interleavedRtpChannel(),
                    transportInfo.interleavedRtcpChannel());
        }
        InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
        return new UdpRtpTransport(ctx.channel().eventLoop().parent(), remote, transportInfo.clientRtpPort(), transportInfo.clientRtcpPort());
    }

    public TransportInfo parseTransport(String header) {
        String normalized = header.toLowerCase(Locale.ROOT);
        if (normalized.contains("interleaved=")) {
            int[] channels = parsePair(header, "interleaved=");
            return new TransportInfo(TransportMode.TCP_INTERLEAVED, 0, 0, channels[0], channels[1]);
        }
        int[] ports = parsePair(header, "client_port=");
        return new TransportInfo(TransportMode.UDP_UNICAST, ports[0], ports[1], -1, -1);
    }

    public String stripTrackSuffix(String uri) {
        int queryIndex = uri.indexOf('?');
        String path = queryIndex >= 0 ? uri.substring(0, queryIndex) : uri;
        String query = queryIndex >= 0 ? uri.substring(queryIndex) : "";
        int index = path.lastIndexOf("/trackID=");
        String strippedPath = index > 0 ? path.substring(0, index) : path;
        return strippedPath + query;
    }

    public FullHttpResponse empty(HttpResponseStatus status) {
        return new DefaultFullHttpResponse(HttpVersion.valueOf("RTSP/1.0"), status);
    }

    public FullHttpResponse sdpResponse(String sdp, String contentBase) {
        ByteBuf content = Unpooled.copiedBuffer(sdp, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/sdp");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        response.headers().set(RtspHeaderNames.CONTENT_BASE, contentBase);
        return response;
    }

    public void writeResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        response.headers().set(RtspHeaderNames.SERVER, SERVER_NAME);
        String cSeq = request.headers().get(RtspHeaderNames.CSEQ);
        if (cSeq != null) {
            response.headers().set(RtspHeaderNames.CSEQ, cSeq);
        }
        ctx.writeAndFlush(response);
    }

    private int[] parsePair(String header, String key) {
        int idx = header.toLowerCase(Locale.ROOT).indexOf(key);
        if (idx < 0) {
            throw new IllegalArgumentException("Missing transport parameter: " + key);
        }
        int begin = idx + key.length();
        int end = header.indexOf(';', begin);
        String pair = end > 0 ? header.substring(begin, end) : header.substring(begin);
        String[] values = pair.trim().split("-", 2);
        int first = Integer.parseInt(values[0].trim());
        int second = values.length > 1 ? Integer.parseInt(values[1].trim()) : first + 1;
        return new int[]{first, second};
    }
}