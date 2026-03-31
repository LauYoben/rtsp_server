package com.yoben.tcp.rtspserver.server;

import com.yoben.tcp.rtspserver.session.RtspSessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description 嵌入式 RTSP 服务器启动组件。
 * @Author Yoben
 * @Since 2026-03-20 11:34:00
 */
public class RtspServer {

    private static final Logger log = LoggerFactory.getLogger(RtspServer.class);

    private final int port;
    private final RtspSessionManager sessionManager = new RtspSessionManager();

    public RtspServer(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new RtspServerInitializer(sessionManager));

            Channel channel = bootstrap.bind(port).sync().channel();
            log.info("RTSP server started on rtsp://0.0.0.0:{}", port);
            channel.closeFuture().sync();
        } finally {
            sessionManager.closeAll();
            bossGroup.shutdownGracefully().sync();
            workerGroup.shutdownGracefully().sync();
        }
    }
}
