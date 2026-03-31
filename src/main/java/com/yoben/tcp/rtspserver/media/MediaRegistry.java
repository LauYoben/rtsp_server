package com.yoben.tcp.rtspserver.media;

import com.yoben.tcp.rtspserver.media.source.Mp4AccessUnitSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * @Description 媒体会话注册与源选择组件。
 * @Author Yoben
 * @Since 2026-03-14 12:10:00
 */
public class MediaRegistry {

    private static final Map<String, MediaSession> SESSIONS = Map.of(
            "/live/h264", singleTrack("/live/h264", "H264 live", MediaCodec.H264),
            "/live/h265", singleTrack("/live/h265", "H265 live", MediaCodec.H265),
            "/live/mjpeg", singleTrack("/live/mjpeg", "Motion JPEG live", MediaCodec.MJPEG),
            "/live/mpeg4", singleTrack("/live/mpeg4", "MPEG4-ES live", MediaCodec.MPEG4_ES),
            "/live/mp2t", singleTrack("/live/mp2t", "MPEG2-TS live", MediaCodec.MPEG2_TS),
            "/live/vp8", singleTrack("/live/vp8", "VP8 live", MediaCodec.VP8),
            "/live/vp9", singleTrack("/live/vp9", "VP9 live", MediaCodec.VP9),
            "/live/av1", singleTrack("/live/av1", "AV1 live", MediaCodec.AV1)
    );

    public Optional<MediaSession> find(String target) {
        ParsedTarget parsed = parseTarget(target);
        MediaSession dynamic = resolveDynamicLive(parsed);
        if (dynamic != null) {
            return Optional.of(dynamic);
        }
        return Optional.ofNullable(SESSIONS.get(parsed.path()));
    }

    public Optional<MediaTrack> findTrack(String target) {
        ParsedTarget parsed = parseTarget(target);
        int idx = parsed.path().lastIndexOf("/trackID=");
        if (idx < 0) {
            return Optional.empty();
        }
        String sessionPath = parsed.path().substring(0, idx);
        String sessionTarget = rebuildTarget(sessionPath, parsed.queryString());
        int trackId = Integer.parseInt(parsed.path().substring(idx + "/trackID=".length()));
        return find(sessionTarget)
                .flatMap(session -> session.tracks().stream().filter(track -> track.trackId() == trackId).findFirst());
    }

    private MediaSession resolveDynamicLive(ParsedTarget parsed) {
        if (!"/live".equals(parsed.path())) {
            return null;
        }
        String fileValue = parsed.queryParams().get("file");
        if (fileValue == null || fileValue.isBlank()) {
            return null;
        }

        Path sourceFile = Paths.get(fileValue).normalize();
        if (!Files.exists(sourceFile) || Files.isDirectory(sourceFile)) {
            return null;
        }

        String name = sourceFile.getFileName() != null ? sourceFile.getFileName().toString() : sourceFile.toString();
        List<MediaTrack> tracks;
        if (name.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            tracks = Mp4AccessUnitSource.describeTracks(sourceFile, parsed.queryParams().get("codec"));
        } else {
            MediaCodec codec = resolveCodec(sourceFile, parsed.queryParams().get("codec"));
            tracks = List.of(new MediaTrack(0, "video", codec, 25, "trackID=0"));
        }
        if (tracks.isEmpty()) {
            return null;
        }

        return new MediaSession(
                rebuildTarget(parsed.path(), parsed.queryString()),
                "File live - " + name,
                tracks,
                sourceFile,
                false);
    }

    private MediaCodec resolveCodec(Path sourceFile, String codecParam) {
        if (codecParam != null && !codecParam.isBlank()) {
            return switch (codecParam.trim().toLowerCase(Locale.ROOT)) {
                case "h264", "avc" -> MediaCodec.H264;
                case "h265", "hevc" -> MediaCodec.H265;
                case "mjpeg", "jpeg", "jpg" -> MediaCodec.MJPEG;
                case "mpeg4", "mp4v-es" -> MediaCodec.MPEG4_ES;
                case "mp2t", "mpeg2ts", "ts" -> MediaCodec.MPEG2_TS;
                case "vp8" -> MediaCodec.VP8;
                case "vp9" -> MediaCodec.VP9;
                case "av1" -> MediaCodec.AV1;
                default -> throw new IllegalArgumentException("Unsupported codec: " + codecParam);
            };
        }

        String fileName = sourceFile.getFileName() == null ? sourceFile.toString() : sourceFile.getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".h264") || lower.endsWith(".264") || lower.endsWith(".avc")) {
            return MediaCodec.H264;
        }
        if (lower.endsWith(".h265") || lower.endsWith(".265") || lower.endsWith(".hevc")) {
            return MediaCodec.H265;
        }
        if (lower.endsWith(".mjpg") || lower.endsWith(".mjpeg") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaCodec.MJPEG;
        }
        if (lower.endsWith(".mp4")) {
            return Mp4AccessUnitSource.probeCodec(sourceFile);
        }
        if (lower.endsWith(".ts") || lower.endsWith(".m2ts")) {
            return MediaCodec.MPEG2_TS;
        }
        throw new IllegalArgumentException("Cannot infer codec from file: " + sourceFile + ". Please add ?codec=h264|h265|mjpeg...");
    }

    private ParsedTarget parseTarget(String target) {
        String normalized = normalizeTarget(target);
        int queryIndex = normalized.indexOf('?');
        String path = queryIndex >= 0 ? normalized.substring(0, queryIndex) : normalized;
        String query = queryIndex >= 0 ? normalized.substring(queryIndex + 1) : "";
        return new ParsedTarget(path, query, parseQuery(query));
    }

    private static String normalizeTarget(String target) {
        try {
            URI uri = URI.create(target);
            if (uri.getScheme() != null) {
                String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
                return uri.getQuery() == null ? path : path + "?" + uri.getQuery();
            }
        } catch (IllegalArgumentException ignored) {
        }
        return target;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private static String rebuildTarget(String path, String query) {
        return query == null || query.isBlank() ? path : path + "?" + query;
    }

    private static MediaSession singleTrack(String path, String name, MediaCodec codec) {
        return new MediaSession(path, name, List.of(new MediaTrack(0, "video", codec, 25, "trackID=0")), null, true);
    }

    private record ParsedTarget(String path, String queryString, Map<String, String> queryParams) {
    }
}