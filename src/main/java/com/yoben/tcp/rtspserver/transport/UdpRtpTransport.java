package com.yoben.tcp.rtspserver.transport;

import com.yoben.tcp.rtspserver.rtp.RtpPacket;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;

/**
 * @Description UDP RTP 传输实现。
 * @Author Yoben
 * @Since 2026-03-18 11:37:00
 */
public class UdpRtpTransport implements RtpTransport {

    private final Channel rtpChannel;
    private final Channel rtcpChannel;
    private final InetSocketAddress clientRtpAddress;
    private final InetSocketAddress clientRtcpAddress;

    public UdpRtpTransport(EventLoopGroup eventLoopGroup, InetSocketAddress clientAddress, int clientRtpPort, int clientRtcpPort)
            throws InterruptedException {
        this.rtpChannel = newDatagramChannel(eventLoopGroup);
        this.rtcpChannel = newDatagramChannel(eventLoopGroup);
        this.clientRtpAddress = new InetSocketAddress(clientAddress.getAddress(), clientRtpPort);
        this.clientRtcpAddress = new InetSocketAddress(clientAddress.getAddress(), clientRtcpPort);
    }

    private Channel newDatagramChannel(EventLoopGroup eventLoopGroup) throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        // Outbound-only transport.
                    }
                });
        return bootstrap.bind(0).sync().channel();
    }

    @Override
    public void sendRtp(RtpPacket packet) {
        rtpChannel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(packet.toBytes()), clientRtpAddress));
    }

    @Override
    public void sendRtcp(byte[] rtcpPayload) {
        rtcpChannel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(rtcpPayload), clientRtcpAddress));
    }

    @Override
    public String transportHeaderValue() {
        InetSocketAddress rtpLocal = (InetSocketAddress) rtpChannel.localAddress();
        InetSocketAddress rtcpLocal = (InetSocketAddress) rtcpChannel.localAddress();
        return "RTP/AVP;unicast;client_port=" + clientRtpAddress.getPort() + "-" + clientRtcpAddress.getPort()
                + ";server_port=" + rtpLocal.getPort() + "-" + rtcpLocal.getPort();
    }

    @Override
    public void close() {
        rtpChannel.close();
        rtcpChannel.close();
    }
}