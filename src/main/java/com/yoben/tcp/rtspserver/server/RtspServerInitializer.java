package com.yoben.tcp.rtspserver.server;

import com.yoben.tcp.rtspserver.server.handler.DescribeRtspMethodHandler;
import com.yoben.tcp.rtspserver.server.handler.OptionsRtspMethodHandler;
import com.yoben.tcp.rtspserver.server.handler.ParameterRtspMethodHandler;
import com.yoben.tcp.rtspserver.server.handler.PauseRtspMethodHandler;
import com.yoben.tcp.rtspserver.server.handler.PlayRtspMethodHandler;
import com.yoben.tcp.rtspserver.server.handler.SetupRtspMethodHandler;
import com.yoben.tcp.rtspserver.server.handler.TeardownRtspMethodHandler;
import com.yoben.tcp.rtspserver.session.RtspSessionManager;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.rtsp.RtspDecoder;
import io.netty.handler.codec.rtsp.RtspEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;

/**
 * @Description RTSP 服务器连接的 Netty Channel 初始化器。
 * @Author Yoben
 * @Since 2026-03-28 17:44:00
 */
public class RtspServerInitializer extends ChannelInitializer<SocketChannel> {

    private final RtspSessionManager sessionManager;

    public RtspServerInitializer(RtspSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        RtspServerSupport support = new RtspServerSupport(sessionManager);
        ch.pipeline()
                .addLast(new RtspEncoder())
                .addLast(new RtspDecoder())
                .addLast(new HttpObjectAggregator(1024 * 1024))
                .addLast(new IdleStateHandler(0, 0, 90, TimeUnit.SECONDS))
                .addLast(new OptionsRtspMethodHandler(support))
                .addLast(new DescribeRtspMethodHandler(support))
                .addLast(new SetupRtspMethodHandler(support))
                .addLast(new PlayRtspMethodHandler(support))
                .addLast(new PauseRtspMethodHandler(support))
                .addLast(new TeardownRtspMethodHandler(support))
                .addLast(new ParameterRtspMethodHandler(support))
                .addLast(new RtspServerHandler(support));
    }
}
