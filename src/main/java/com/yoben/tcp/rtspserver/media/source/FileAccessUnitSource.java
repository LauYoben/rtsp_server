package com.yoben.tcp.rtspserver.media.source;

import com.yoben.tcp.rtspserver.media.MediaCodec;
import com.yoben.tcp.rtspserver.media.MediaSession;
import com.yoben.tcp.rtspserver.media.MediaTrack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description 基于文件的访问单元数据源实现。
 * @Author Yoben
 * @Since 2026-03-17 10:40:00
 */
public class FileAccessUnitSource implements AccessUnitSource {

    private final Map<Path, List<byte[]>> cache = new ConcurrentHashMap<>();

    @Override
    public byte[] nextAccessUnit(MediaSession session, MediaTrack track, long frameIndex) {
        Path sourceFile = session.sourceFile();
        if (sourceFile == null) {
            throw new IllegalStateException("Missing source file for session: " + session.path());
        }
        List<byte[]> units = cache.computeIfAbsent(sourceFile, path -> loadUnits(path, track.codec()));
        if (units.isEmpty()) {
            throw new IllegalStateException("No access units parsed from file: " + sourceFile);
        }
        return units.get((int) (frameIndex % units.size()));
    }

    @Override
    public List<byte[]> bootstrapAccessUnits(MediaSession session, MediaTrack track) {
        if (track.codec() != MediaCodec.H264 || session.sourceFile() == null) {
            return List.of();
        }
        List<byte[]> units = cache.computeIfAbsent(session.sourceFile(), path -> loadUnits(path, track.codec()));
        List<byte[]> bootstrap = new ArrayList<>();
        byte[] sps = null;
        byte[] pps = null;
        byte[] idr = null;
        for (byte[] unit : units) {
            int nalType = h264NalType(unit);
            if (nalType == 7 && sps == null) {
                sps = unit;
            } else if (nalType == 8 && pps == null) {
                pps = unit;
            } else if (nalType == 5) {
                idr = unit;
                break;
            }
        }
        if (sps != null) {
            bootstrap.add(sps);
        }
        if (pps != null) {
            bootstrap.add(pps);
        }
        if (idr != null) {
            bootstrap.add(idr);
        }
        return bootstrap;
    }

    private List<byte[]> loadUnits(Path file, MediaCodec codec) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return switch (codec) {
                case H264, H265 -> splitAnnexB(bytes);
                case MJPEG -> splitJpeg(bytes);
                default -> List.of(bytes);
            };
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read media file: " + file, e);
        }
    }

    private List<byte[]> splitAnnexB(byte[] bytes) {
        List<Integer> starts = new ArrayList<>();
        int index = 0;
        while (index < bytes.length - 3) {
            int startCodeSize = startCodeSize(bytes, index);
            if (startCodeSize > 0) {
                starts.add(index);
                index += startCodeSize;
            } else {
                index++;
            }
        }

        List<byte[]> units = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int startCodeSize = startCodeSize(bytes, start);
            int payloadStart = start + startCodeSize;
            int end = i + 1 < starts.size() ? starts.get(i + 1) : bytes.length;
            if (payloadStart < end) {
                byte[] unit = new byte[end - payloadStart];
                System.arraycopy(bytes, payloadStart, unit, 0, unit.length);
                units.add(unit);
            }
        }
        if (units.isEmpty() && bytes.length > 0) {
            units.add(bytes);
        }
        return units;
    }

    private List<byte[]> splitJpeg(byte[] bytes) {
        List<byte[]> frames = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < bytes.length - 1; i++) {
            if (bytes[i] == (byte) 0xFF && bytes[i + 1] == (byte) 0xD8) {
                start = i;
            }
            if (start >= 0 && bytes[i] == (byte) 0xFF && bytes[i + 1] == (byte) 0xD9) {
                int end = i + 2;
                byte[] frame = new byte[end - start];
                System.arraycopy(bytes, start, frame, 0, frame.length);
                frames.add(frame);
                start = -1;
            }
        }
        if (frames.isEmpty() && bytes.length > 0) {
            frames.add(bytes);
        }
        return frames;
    }

    private int startCodeSize(byte[] bytes, int index) {
        if (index + 3 < bytes.length
                && bytes[index] == 0x00
                && bytes[index + 1] == 0x00
                && bytes[index + 2] == 0x00
                && bytes[index + 3] == 0x01) {
            return 4;
        }
        if (index + 2 < bytes.length
                && bytes[index] == 0x00
                && bytes[index + 1] == 0x00
                && bytes[index + 2] == 0x01) {
            return 3;
        }
        return 0;
    }

    private int h264NalType(byte[] unit) {
        return unit.length == 0 ? -1 : unit[0] & 0x1F;
    }
}
