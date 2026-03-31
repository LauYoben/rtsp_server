package com.yoben.tcp.rtspserver.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

/**
 * @Description RTSP TCP 交错传输实现。
 * @Author Yoben
 * @Since 2026-03-10 15:27:00
 */
public class InterleavedRtpTransport implements RtpTransport {

    private final Channel controlChannel;
    private final int rtpChannel;
    private final int rtcpChannel;

    public InterleavedRtpTransport(Channel controlChannel, int rtpChannel, int rtcpChannel) {
        this.controlChannel = controlChannel;
        this.rtpChannel = rtpChannel;
        this.rtcpChannel = rtcpChannel;
    }

    @Override
    public void sendRtp(com.yoben.tcp.rtspserver.rtp.RtpPacket packet) {
        controlChannel.writeAndFlush(encodeInterleaved(rtpChannel, packet.toBytes()));
    }

    @Override
    public void sendRtcp(byte[] rtcpPayload) {
        controlChannel.writeAndFlush(encodeInterleaved(rtcpChannel, rtcpPayload));
    }

    @Override
    public String transportHeaderValue() {
        return "RTP/AVP/TCP;unicast;interleaved=" + rtpChannel + "-" + rtcpChannel;
    }

    @Override
    public void close() {
    }

    private ByteBuf encodeInterleaved(int channelId, byte[] payload) {
        ByteBuf buffer = Unpooled.buffer(payload.length + 4);
        buffer.writeByte('$');
        buffer.writeByte(channelId);
        buffer.writeShort(payload.length);
        buffer.writeBytes(payload);
        return buffer;
    }
}
