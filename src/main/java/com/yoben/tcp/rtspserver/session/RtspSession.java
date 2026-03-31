package com.yoben.tcp.rtspserver.session;

import com.yoben.tcp.rtspserver.media.MediaCodec;
import com.yoben.tcp.rtspserver.media.MediaSession;
import com.yoben.tcp.rtspserver.media.MediaTrack;
import com.yoben.tcp.rtspserver.media.source.AccessUnitSource;
import com.yoben.tcp.rtspserver.media.source.AccessUnitSourceFactory;
import com.yoben.tcp.rtspserver.rtcp.RtcpByePacket;
import com.yoben.tcp.rtspserver.rtcp.RtcpSenderReport;
import com.yoben.tcp.rtspserver.rtp.RtpPacket;
import com.yoben.tcp.rtspserver.rtp.RtpPacketizer;
import com.yoben.tcp.rtspserver.transport.RtpTransport;
import io.netty.channel.EventLoop;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RtspSession implements AutoCloseable {

    private static final int RTP_MTU = 1200;
    private static final int MAX_LIVE_MESSAGES = 100;

    private final String sessionId;
    private final MediaSession mediaSession;
    private final AccessUnitSource source;
    private final Map<Integer, TrackRuntime> runtimes = new ConcurrentHashMap<>();
    private final ArrayDeque<LiveMessage> liveMessages = new ArrayDeque<>();
    private final AtomicInteger liveMessageId = new AtomicInteger();

    public RtspSession(MediaSession mediaSession) {
        this.sessionId = Long.toHexString(ThreadLocalRandom.current().nextLong());
        this.mediaSession = mediaSession;
        this.source = new AccessUnitSourceFactory().create(mediaSession);
    }

    public String sessionId() {
        return sessionId;
    }

    public MediaSession mediaSession() {
        return mediaSession;
    }

    public void bindTrack(MediaTrack track, RtpTransport transport, EventLoop eventLoop) {
        TrackRuntime runtime = new TrackRuntime(track, transport, eventLoop);
        TrackRuntime previous = runtimes.put(track.trackId(), runtime);
        if (previous != null) {
            previous.close();
        }
    }

    public void start() {
        runtimes.values().forEach(TrackRuntime::start);
    }

    public void pause() {
        runtimes.values().forEach(TrackRuntime::pause);
    }

    public int addLiveMessage(String sender, String message) {
        synchronized (liveMessages) {
            int id = liveMessageId.incrementAndGet();
            liveMessages.addLast(new LiveMessage(id, sender == null ? "system" : sender, message == null ? "" : message));
            while (liveMessages.size() > MAX_LIVE_MESSAGES) {
                liveMessages.removeFirst();
            }
            return id;
        }
    }

    public List<LiveMessage> messagesAfter(int lastId) {
        synchronized (liveMessages) {
            List<LiveMessage> result = new ArrayList<>();
            for (LiveMessage message : liveMessages) {
                if (message.id() > lastId) {
                    result.add(message);
                }
            }
            return result;
        }
    }

    public void seekRelativeSeconds(double seconds) {
        runtimes.values().forEach(runtime -> runtime.seekRelativeSeconds(seconds));
    }

    @Override
    public void close() {
        runtimes.values().forEach(TrackRuntime::close);
        runtimes.clear();
    }

    public record LiveMessage(int id, String sender, String message) {
    }

    private final class TrackRuntime implements AutoCloseable {
        private final MediaTrack track;
        private final RtpTransport transport;
        private final EventLoop eventLoop;
        private final RtpPacketizer packetizer;
        private final int ssrc = ThreadLocalRandom.current().nextInt();
        private int sequenceNumber = ThreadLocalRandom.current().nextInt(0, 65535);
        private final long timestampBase = ThreadLocalRandom.current().nextLong(0, Integer.MAX_VALUE);
        private long timestamp = timestampBase;
        private long frameIndex;
        private long packetCount;
        private long octetCount;
        private long nextSendTimeNanos;
        private boolean bootstrapSent;
        private ScheduledFuture<?> rtpTask;
        private ScheduledFuture<?> rtcpTask;

        private TrackRuntime(MediaTrack track, RtpTransport transport, EventLoop eventLoop) {
            this.track = track;
            this.transport = transport;
            this.eventLoop = eventLoop;
            this.packetizer = track.createPacketizer();
        }

        private void start() {
            if (!bootstrapSent) {
                frameIndex = Math.max(0L, source.playbackStartFrameIndex(mediaSession, track));
                sendBootstrapIfNeeded();
                bootstrapSent = true;
            }
            if (rtpTask == null || rtpTask.isCancelled()) {
                nextSendTimeNanos = System.nanoTime();
                scheduleNextFrame(0L, TimeUnit.NANOSECONDS);
            }
            if (rtcpTask == null || rtcpTask.isCancelled()) {
                rtcpTask = eventLoop.scheduleAtFixedRate(this::sendSenderReportSafe, 5, 5, TimeUnit.SECONDS);
            }
        }

        private void pause() {
            if (rtpTask != null) {
                rtpTask.cancel(false);
                rtpTask = null;
            }
            nextSendTimeNanos = 0L;
        }

        private void seekRelativeSeconds(double seconds) {
            long deltaFrames = Math.round(seconds * Math.max(1, track.frameRate()));
            frameIndex = Math.max(0L, frameIndex + deltaFrames);
            bootstrapSent = false;
            if (rtpTask != null && !rtpTask.isCancelled()) {
                sendBootstrapIfNeeded();
                bootstrapSent = true;
            }
        }

        private void sendBootstrapIfNeeded() {
            if (track.codec() != MediaCodec.H264 && track.codec() != MediaCodec.H265) {
                return;
            }
            List<byte[]> bootstrapUnits = source.bootstrapAccessUnits(mediaSession, track);
            if (bootstrapUnits.isEmpty()) {
                return;
            }
            long bootstrapTimestamp = timestamp;
            for (byte[] bootstrapUnit : bootstrapUnits) {
                sendAccessUnit(bootstrapUnit, bootstrapTimestamp);
            }
        }

        private void scheduleNextFrame(long delay, TimeUnit unit) {
            rtpTask = eventLoop.schedule(this::sendFrameSafe, Math.max(0L, delay), unit);
        }

        private void sendFrameSafe() {
            try {
                sendFrame();
            } catch (Exception ignored) {
            }
        }

        private void sendFrame() {
            long currentFrameIndex = frameIndex++;
            byte[] accessUnit = source.nextAccessUnit(mediaSession, track, currentFrameIndex);
            long frameTimestamp = source.frameTimestamp(mediaSession, track, currentFrameIndex, timestampBase);
            long timestampIncrement = source.timestampIncrement(mediaSession, track, currentFrameIndex);
            sendAccessUnit(accessUnit, frameTimestamp);
            timestamp = frameTimestamp + timestampIncrement;

            if (rtpTask != null && !rtpTask.isCancelled()) {
                long frameDurationNanos = Math.max(1L, source.frameIntervalNanos(mediaSession, track, currentFrameIndex));
                nextSendTimeNanos = (nextSendTimeNanos == 0L ? System.nanoTime() : nextSendTimeNanos) + frameDurationNanos;
                long delayNanos = Math.max(0L, nextSendTimeNanos - System.nanoTime());
                scheduleNextFrame(delayNanos, TimeUnit.NANOSECONDS);
            }
        }

        private void sendAccessUnit(byte[] accessUnit, long frameTimestamp) {
            var payloads = packetizer.packetize(accessUnit, RTP_MTU);
            for (int i = 0; i < payloads.size(); i++) {
                byte[] payload = payloads.get(i);
                RtpPacket packet = new RtpPacket(
                        track.codec().payloadType(),
                        sequenceNumber++ & 0xFFFF,
                        frameTimestamp,
                        ssrc,
                        i == payloads.size() - 1,
                        payload);
                transport.sendRtp(packet);
                packetCount++;
                octetCount += payload.length;
            }
        }

        private void sendSenderReportSafe() {
            try {
                transport.sendRtcp(new RtcpSenderReport(ssrc, timestamp, packetCount, octetCount).toBytes());
            } catch (Exception ignored) {
            }
        }

        @Override
        public void close() {
            if (rtpTask != null) {
                rtpTask.cancel(false);
            }
            if (rtcpTask != null) {
                rtcpTask.cancel(false);
            }
            try {
                transport.sendRtcp(new RtcpByePacket(ssrc).toBytes());
            } catch (Exception ignored) {
            }
            transport.close();
        }
    }
}