package com.yoben.tcp.rtspserver.media.source;

import com.yoben.tcp.rtspserver.media.MediaCodec;
import com.yoben.tcp.rtspserver.media.MediaSession;
import com.yoben.tcp.rtspserver.media.MediaTrack;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.mp4parser.boxes.iso14496.part14.ESDescriptorBox;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.AudioSpecificConfig;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.DecoderConfigDescriptor;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.ESDescriptor;
import org.mp4parser.boxes.iso14496.part15.AvcConfigurationBox;
import org.mp4parser.boxes.iso14496.part15.HevcConfigurationBox;
import org.mp4parser.boxes.iso14496.part15.HevcDecoderConfigurationRecord;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Sample;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mp4AccessUnitSource implements AccessUnitSource {

    private static final Logger log = LoggerFactory.getLogger(Mp4AccessUnitSource.class);
    private static final byte[] START_CODE = new byte[]{0x00, 0x00, 0x00, 0x01};
    private static final int MIN_USEFUL_AAC_SAMPLE_BYTES = 4;

    private final Map<Path, Mp4FileData> cache = new ConcurrentHashMap<>();

    public static MediaCodec probeCodec(Path file) {
        try {
            Movie movie = MovieCreator.build(file.toString());
            Track track = movie.getTracks().stream()
                    .filter(candidate -> "vide".equals(candidate.getHandler()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No video track found in mp4 file: " + file));
            return mapVideoCodec(track.getSampleEntries().get(0).getType());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse mp4 file: " + file, e);
        }
    }

    public static List<MediaTrack> describeTracks(Path file, String codecParam) {
        try {
            Movie movie = MovieCreator.build(file.toString());
            List<MediaTrack> tracks = new ArrayList<>();
            int trackId = 0;

            Track video = movie.getTracks().stream()
                    .filter(candidate -> "vide".equals(candidate.getHandler()))
                    .findFirst()
                    .orElse(null);
            if (video != null) {
                MediaCodec videoCodec = codecParam != null && !codecParam.isBlank()
                        ? mapOverrideCodec(codecParam)
                        : mapVideoCodec(video.getSampleEntries().get(0).getType());
                int frameRate = estimateFrameRate(video);
                tracks.add(new MediaTrack(trackId++, "video", videoCodec, frameRate, "trackID=0"));
            }

            List<Track> audioTracks = movie.getTracks().stream()
                    .filter(candidate -> "soun".equals(candidate.getHandler()))
                    .toList();
            Track audio = findSupportedAudioTrack(audioTracks);
            if (audio != null) {
                MediaTrack audioTrack = createAudioTrack(trackId, audio);
                if (audioTrack != null) {
                    tracks.add(audioTrack);
                }
            }

            return tracks;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect mp4 tracks: " + file, e);
        }
    }

    @Override
    public byte[] nextAccessUnit(MediaSession session, MediaTrack track, long frameIndex) {
        Mp4TrackData trackData = requireTrackData(session, track);
        int sampleIndex = playbackSampleIndex(trackData, frameIndex);
        return readAccessUnit(trackData, sampleIndex);
    }

    @Override
    public List<byte[]> bootstrapAccessUnits(MediaSession session, MediaTrack track) {
        return List.of();
    }

    @Override
    public String describeFmtp(MediaSession session, MediaTrack track, String currentFmtp) {
        Mp4TrackData data = requireTrackData(session, track);
        if (track.codec() == MediaCodec.H264) {
            if (data.h264Sps() == null || data.h264Pps() == null) {
                return currentFmtp;
            }
            String profileLevelId = data.h264Sps().length >= 4
                    ? String.format(Locale.ROOT, "%02X%02X%02X", data.h264Sps()[1] & 0xFF, data.h264Sps()[2] & 0xFF, data.h264Sps()[3] & 0xFF)
                    : "42E01F";
            String sprop = Base64.getEncoder().encodeToString(data.h264Sps()) + "," + Base64.getEncoder().encodeToString(data.h264Pps());
            return "packetization-mode=1;profile-level-id=" + profileLevelId + ";sprop-parameter-sets=" + sprop;
        }

        if (track.codec() == MediaCodec.H265) {
            List<String> entries = new ArrayList<>();
            if (data.h265Vps() != null) {
                entries.add("sprop-vps=" + Base64.getEncoder().encodeToString(data.h265Vps()));
            }
            if (data.h265Sps() != null) {
                entries.add("sprop-sps=" + Base64.getEncoder().encodeToString(data.h265Sps()));
            }
            if (data.h265Pps() != null) {
                entries.add("sprop-pps=" + Base64.getEncoder().encodeToString(data.h265Pps()));
            }
            return entries.isEmpty() ? currentFmtp : String.join(";", entries);
        }

        return currentFmtp;
    }

    @Override
    public long frameIntervalMillis(MediaSession session, MediaTrack track, long frameIndex) {
        Mp4TrackData data = requireTrackData(session, track);
        if (data.sampleDurationsMillis().isEmpty()) {
            return AccessUnitSource.super.frameIntervalMillis(session, track, frameIndex);
        }
        return Math.max(1, data.sampleDurationsMillis().get(playbackSampleIndex(data, frameIndex)));
    }

    @Override
    public long frameIntervalNanos(MediaSession session, MediaTrack track, long frameIndex) {
        Mp4TrackData data = requireTrackData(session, track);
        if (data.sampleDurationsNanos().isEmpty()) {
            return AccessUnitSource.super.frameIntervalNanos(session, track, frameIndex);
        }
        return Math.max(1L, data.sampleDurationsNanos().get(playbackSampleIndex(data, frameIndex)));
    }

    @Override
    public long timestampIncrement(MediaSession session, MediaTrack track, long frameIndex) {
        Mp4TrackData data = requireTrackData(session, track);
        if (data.rtpTimestampIncrements().isEmpty()) {
            return AccessUnitSource.super.timestampIncrement(session, track, frameIndex);
        }
        return Math.max(1, data.rtpTimestampIncrements().get(playbackSampleIndex(data, frameIndex)));
    }

    @Override
    public long frameTimestamp(MediaSession session, MediaTrack track, long frameIndex, long timestampBase) {
        Mp4TrackData data = requireTrackData(session, track);
        int playbackCount = playbackSampleCount(data);
        if (playbackCount <= 0 || data.presentationTimestampOffsets().isEmpty()) {
            return AccessUnitSource.super.frameTimestamp(session, track, frameIndex, timestampBase);
        }
        long loopCount = Math.floorDiv(frameIndex, playbackCount);
        int sampleIndex = playbackSampleIndex(data, frameIndex);
        long sampleOffset = data.presentationTimestampOffsets().get(sampleIndex);
        return timestampBase + loopCount * data.playbackCycleTimestampSpan() + sampleOffset;
    }

    @Override
    public long playbackStartFrameIndex(MediaSession session, MediaTrack track) {
        return 0L;
    }

    private Mp4TrackData requireTrackData(MediaSession session, MediaTrack track) {
        Path sourceFile = session.sourceFile();
        if (sourceFile == null) {
            throw new IllegalStateException("Missing source file for mp4 session: " + session.path());
        }
        Mp4FileData fileData = cache.computeIfAbsent(sourceFile, this::loadFileData);
        Mp4TrackData trackData = fileData.tracks().get(track.trackId());
        if (trackData == null || trackData.samples().isEmpty()) {
            throw new IllegalStateException("No samples parsed from mp4 file for track " + track.trackId() + ": " + sourceFile);
        }
        return trackData;
    }

    private Mp4FileData loadFileData(Path file) {
        try {
            Movie movie = MovieCreator.build(file.toString());
            Map<Integer, Mp4TrackData> trackMap = new HashMap<>();
            int trackId = 0;

            Track video = movie.getTracks().stream().filter(candidate -> "vide".equals(candidate.getHandler())).findFirst().orElse(null);
            if (video != null) {
                MediaCodec codec = mapVideoCodec(video.getSampleEntries().get(0).getType());
                trackMap.put(trackId++, loadVideoTrackData(video, codec));
            }

            List<Track> audioTracks = movie.getTracks().stream()
                    .filter(candidate -> "soun".equals(candidate.getHandler()))
                    .toList();
            Track audio = findSupportedAudioTrack(audioTracks);
            if (audio != null) {
                trackMap.put(trackId, loadAudioTrackData(audio));
            }

            return new Mp4FileData(trackMap);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load mp4 track data: " + file, e);
        }
    }

    private Mp4TrackData loadVideoTrackData(Track track, MediaCodec codec) {
        int nalLengthSize = resolveNalLengthSize(track, codec);
        byte[] h264Sps = null;
        byte[] h264Pps = null;
        byte[] h265Vps = null;
        byte[] h265Sps = null;
        byte[] h265Pps = null;
        if (codec == MediaCodec.H264) {
            List<AvcConfigurationBox> boxes = track.getSampleEntries().get(0).getBoxes(AvcConfigurationBox.class);
            if (!boxes.isEmpty()) {
                AvcConfigurationBox box = boxes.get(0);
                if (!box.getSequenceParameterSets().isEmpty()) {
                    h264Sps = copy(box.getSequenceParameterSets().get(0).duplicate());
                }
                if (!box.getPictureParameterSets().isEmpty()) {
                    h264Pps = copy(box.getPictureParameterSets().get(0).duplicate());
                }
            }
        } else if (codec == MediaCodec.H265) {
            List<HevcConfigurationBox> boxes = track.getSampleEntries().get(0).getBoxes(HevcConfigurationBox.class);
            if (!boxes.isEmpty()) {
                HevcDecoderConfigurationRecord record = boxes.get(0).getHevcDecoderConfigurationRecord();
                for (HevcDecoderConfigurationRecord.Array array : record.getArrays()) {
                    int nalUnitType = array.nal_unit_type;
                    for (byte[] nal : array.nalUnits) {
                        if (nalUnitType == 32 && h265Vps == null) {
                            h265Vps = nal;
                        } else if (nalUnitType == 33 && h265Sps == null) {
                            h265Sps = nal;
                        } else if (nalUnitType == 34 && h265Pps == null) {
                            h265Pps = nal;
                        }
                    }
                }
            }
        }

        List<Sample> samples = track.getSamples();
        List<Long> sampleDurationsMillis = new ArrayList<>(samples.size());
        List<Long> sampleDurationsNanos = new ArrayList<>(samples.size());
        List<Long> rtpTimestampIncrements = new ArrayList<>(samples.size());
        List<Long> presentationTimestampOffsets = new ArrayList<>(samples.size());
        fillTimings(track, codec.clockRate(), sampleDurationsMillis, sampleDurationsNanos, rtpTimestampIncrements, presentationTimestampOffsets);

        int firstSyncSampleIndex = -1;
        long[] syncSamples = track.getSyncSamples();
        if (syncSamples != null && syncSamples.length > 0) {
            int syncIndex = (int) syncSamples[0] - 1;
            if (syncIndex >= 0 && syncIndex < samples.size()) {
                firstSyncSampleIndex = syncIndex;
            }
        }
        if (firstSyncSampleIndex < 0 && !samples.isEmpty()) {
            firstSyncSampleIndex = 0;
        }

        if (codec == MediaCodec.H264 && (h264Sps == null || h264Pps == null) && firstSyncSampleIndex >= 0) {
            byte[][] derived = deriveH264ParameterSets(readAccessUnit(track, codec, nalLengthSize, firstSyncSampleIndex));
            if (h264Sps == null) {
                h264Sps = derived[0];
            }
            if (h264Pps == null) {
                h264Pps = derived[1];
            }
        }
        if (codec == MediaCodec.H265 && (h265Vps == null || h265Sps == null || h265Pps == null) && firstSyncSampleIndex >= 0) {
            byte[][] derived = deriveH265ParameterSets(readAccessUnit(track, codec, nalLengthSize, firstSyncSampleIndex));
            if (h265Vps == null) {
                h265Vps = derived[0];
            }
            if (h265Sps == null) {
                h265Sps = derived[1];
            }
            if (h265Pps == null) {
                h265Pps = derived[2];
            }
        }

        int startSampleIndex = firstSyncSampleIndex >= 0 ? firstSyncSampleIndex : 0;
        List<Long> normalizedPresentationOffsets = normalizePresentationOffsets(presentationTimestampOffsets, rtpTimestampIncrements, startSampleIndex);
        long playbackCycleTimestampSpan = computePlaybackCycleTimestampSpan(normalizedPresentationOffsets, rtpTimestampIncrements, startSampleIndex);
        return new Mp4TrackData(track, codec, nalLengthSize, samples, startSampleIndex, firstSyncSampleIndex,
                sampleDurationsMillis, sampleDurationsNanos, rtpTimestampIncrements, normalizedPresentationOffsets, playbackCycleTimestampSpan,
                h264Sps, h264Pps, h265Vps, h265Sps, h265Pps);
    }

    private Mp4TrackData loadAudioTrackData(Track track) {
        List<Sample> samples = track.getSamples();
        List<Long> sampleDurationsMillis = new ArrayList<>(samples.size());
        List<Long> sampleDurationsNanos = new ArrayList<>(samples.size());
        List<Long> rtpTimestampIncrements = new ArrayList<>(samples.size());
        List<Long> presentationTimestampOffsets = new ArrayList<>(samples.size());
        int clockRate = Math.max(8000, (int) Math.round(track.getTrackMetaData().getTimescale()));
        fillTimings(track, clockRate, sampleDurationsMillis, sampleDurationsNanos, rtpTimestampIncrements, presentationTimestampOffsets);

        int startSampleIndex = 0;
        while (startSampleIndex < samples.size()) {
            if (samples.get(startSampleIndex).asByteBuffer().remaining() > MIN_USEFUL_AAC_SAMPLE_BYTES) {
                break;
            }
            startSampleIndex++;
        }
        if (startSampleIndex >= samples.size()) {
            startSampleIndex = 0;
        }

        List<Long> normalizedPresentationOffsets = normalizePresentationOffsets(presentationTimestampOffsets, rtpTimestampIncrements, startSampleIndex);
        long playbackCycleTimestampSpan = computePlaybackCycleTimestampSpan(normalizedPresentationOffsets, rtpTimestampIncrements, startSampleIndex);
        return new Mp4TrackData(track, MediaCodec.AAC, 0, samples, startSampleIndex, -1,
                sampleDurationsMillis, sampleDurationsNanos, rtpTimestampIncrements, normalizedPresentationOffsets, playbackCycleTimestampSpan,
                null, null, null, null, null);
    }

    private void fillTimings(Track track, int clockRate, List<Long> sampleDurationsMillis, List<Long> sampleDurationsNanos,
            List<Long> rtpTimestampIncrements, List<Long> presentationTimestampOffsets) {
        long[] sampleDurations = track.getSampleDurations();
        long timescale = Math.max(1L, Math.round(track.getTrackMetaData().getTimescale()));
        List<Long> compositionOffsets = resolveCompositionOffsets(track, sampleDurations.length, clockRate, timescale);
        long decodeTimestamp = 0L;
        for (int i = 0; i < sampleDurations.length; i++) {
            long sampleDuration = sampleDurations[i];
            long millis = Math.max(1L, Math.round(sampleDuration * 1000.0 / timescale));
            long nanos = Math.max(1L, Math.round(sampleDuration * 1_000_000_000.0 / timescale));
            long rtpTicks = Math.max(1L, Math.round(sampleDuration * (double) clockRate / timescale));
            sampleDurationsMillis.add(millis);
            sampleDurationsNanos.add(nanos);
            rtpTimestampIncrements.add(rtpTicks);
            long compositionOffset = i < compositionOffsets.size() ? compositionOffsets.get(i) : 0L;
            presentationTimestampOffsets.add(decodeTimestamp + compositionOffset);
            decodeTimestamp += rtpTicks;
        }
    }

    private List<Long> normalizePresentationOffsets(List<Long> offsets, List<Long> durations, int startSampleIndex) {
        if (offsets.isEmpty()) {
            return List.of();
        }
        long base = offsets.get(Math.max(0, Math.min(startSampleIndex, offsets.size() - 1)));
        List<Long> normalized = new ArrayList<>(offsets.size());
        for (long offset : offsets) {
            normalized.add(Math.max(0L, offset - base));
        }
        return normalized;
    }

    private long computePlaybackCycleTimestampSpan(List<Long> offsets, List<Long> durations, int startSampleIndex) {
        if (offsets.isEmpty() || durations.isEmpty()) {
            return 1L;
        }
        long maxEnd = 0L;
        for (int i = Math.max(0, startSampleIndex); i < offsets.size() && i < durations.size(); i++) {
            maxEnd = Math.max(maxEnd, offsets.get(i) + Math.max(1L, durations.get(i)));
        }
        return Math.max(1L, maxEnd);
    }

    private List<Long> resolveCompositionOffsets(Track track, int sampleCount, int clockRate, long timescale) {
        List<Long> offsets = new ArrayList<>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            offsets.add(0L);
        }
        try {
            var method = track.getClass().getMethod("getCompositionTimeEntries");
            Object entriesObj = method.invoke(track);
            if (!(entriesObj instanceof List<?> entries) || entries.isEmpty()) {
                return offsets;
            }
            int sampleIndex = 0;
            for (Object entry : entries) {
                int count = ((Number) entry.getClass().getMethod("getCount").invoke(entry)).intValue();
                long rawOffset = ((Number) entry.getClass().getMethod("getOffset").invoke(entry)).longValue();
                long rtpOffset = Math.round(rawOffset * (double) clockRate / Math.max(1L, timescale));
                for (int i = 0; i < count && sampleIndex < sampleCount; i++) {
                    offsets.set(sampleIndex++, rtpOffset);
                }
            }
        } catch (Exception ignored) {
        }
        return offsets;
    }

    private int resolveNalLengthSize(Track track, MediaCodec codec) {
        if (codec == MediaCodec.H264) {
            List<AvcConfigurationBox> boxes = track.getSampleEntries().get(0).getBoxes(AvcConfigurationBox.class);
            if (!boxes.isEmpty()) {
                return boxes.get(0).getLengthSizeMinusOne() + 1;
            }
        }
        if (codec == MediaCodec.H265) {
            List<HevcConfigurationBox> boxes = track.getSampleEntries().get(0).getBoxes(HevcConfigurationBox.class);
            if (!boxes.isEmpty()) {
                return boxes.get(0).getLengthSizeMinusOne() + 1;
            }
        }
        return 4;
    }

    private byte[] readAccessUnit(Mp4TrackData trackData, int sampleIndex) {
        return readAccessUnit(trackData.track(), trackData.codec(), trackData.nalLengthSize(), sampleIndex);
    }

    private byte[] readAccessUnit(Track track, MediaCodec codec, int nalLengthSize, int sampleIndex) {
        ByteBuffer buffer = track.getSamples().get(sampleIndex).asByteBuffer().duplicate();
        return switch (codec) {
            case H264, H265 -> toAnnexB(buffer, nalLengthSize);
            default -> copy(buffer);
        };
    }

    private byte[] toAnnexB(ByteBuffer buffer, int nalLengthSize) {
        List<byte[]> nalUnits = new ArrayList<>();
        int totalLength = 0;
        while (buffer.remaining() >= nalLengthSize) {
            int nalLength = readNalLength(buffer, nalLengthSize);
            if (nalLength <= 0 || buffer.remaining() < nalLength) {
                break;
            }
            byte[] nal = new byte[nalLength];
            buffer.get(nal);
            nalUnits.add(nal);
            totalLength += START_CODE.length + nalLength;
        }
        if (nalUnits.isEmpty()) {
            return copy(buffer);
        }
        byte[] accessUnit = new byte[totalLength];
        int offset = 0;
        for (byte[] nalUnit : nalUnits) {
            System.arraycopy(START_CODE, 0, accessUnit, offset, START_CODE.length);
            offset += START_CODE.length;
            System.arraycopy(nalUnit, 0, accessUnit, offset, nalUnit.length);
            offset += nalUnit.length;
        }
        return accessUnit;
    }

    private int readNalLength(ByteBuffer buffer, int nalLengthSize) {
        int value = 0;
        for (int i = 0; i < nalLengthSize; i++) {
            value = (value << 8) | (buffer.get() & 0xFF);
        }
        return value;
    }

    private byte[] copy(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private byte[][] deriveH264ParameterSets(byte[] accessUnit) {
        byte[] sps = null;
        byte[] pps = null;
        if (accessUnit == null) {
            return new byte[][]{null, null};
        }
        for (byte[] nalUnit : splitAnnexB(accessUnit)) {
            if (nalUnit.length == 0) {
                continue;
            }
            int nalType = nalUnit[0] & 0x1F;
            if (nalType == 7 && sps == null) {
                sps = nalUnit;
            } else if (nalType == 8 && pps == null) {
                pps = nalUnit;
            }
        }
        return new byte[][]{sps, pps};
    }

    private byte[][] deriveH265ParameterSets(byte[] accessUnit) {
        byte[] vps = null;
        byte[] sps = null;
        byte[] pps = null;
        if (accessUnit == null) {
            return new byte[][]{null, null, null};
        }
        for (byte[] nalUnit : splitAnnexB(accessUnit)) {
            if (nalUnit.length < 2) {
                continue;
            }
            int nalType = (nalUnit[0] >> 1) & 0x3F;
            if (nalType == 32 && vps == null) {
                vps = nalUnit;
            } else if (nalType == 33 && sps == null) {
                sps = nalUnit;
            } else if (nalType == 34 && pps == null) {
                pps = nalUnit;
            }
        }
        return new byte[][]{vps, sps, pps};
    }

    private byte[] wrapSingleNal(byte[] nal) {
        byte[] bytes = new byte[START_CODE.length + nal.length];
        System.arraycopy(START_CODE, 0, bytes, 0, START_CODE.length);
        System.arraycopy(nal, 0, bytes, START_CODE.length, nal.length);
        return bytes;
    }

    private List<byte[]> splitAnnexB(byte[] accessUnit) {
        List<byte[]> nalUnits = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < accessUnit.length - 3; i++) {
            int startCodeSize = startCodeSize(accessUnit, i);
            if (startCodeSize > 0) {
                starts.add(i);
                i += startCodeSize - 1;
            }
        }
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int payloadStart = start + startCodeSize(accessUnit, start);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : accessUnit.length;
            if (payloadStart < end) {
                byte[] nalUnit = new byte[end - payloadStart];
                System.arraycopy(accessUnit, payloadStart, nalUnit, 0, nalUnit.length);
                nalUnits.add(nalUnit);
            }
        }
        return nalUnits;
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

    private int playbackSampleIndex(Mp4TrackData trackData, long frameIndex) {
        int count = playbackSampleCount(trackData);
        return trackData.startSampleIndex() + (count == 0 ? 0 : (int) (frameIndex % count));
    }

    private int playbackSampleCount(Mp4TrackData trackData) {
        return Math.max(1, trackData.samples().size() - trackData.startSampleIndex());
    }

    private String appendFmtp(String base, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return base;
        }
        if (base == null || base.isBlank()) {
            return suffix;
        }
        return base + ";" + suffix;
    }

    private static MediaCodec mapVideoCodec(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "avc1", "avc3" -> MediaCodec.H264;
            case "hvc1", "hev1" -> MediaCodec.H265;
            case "mp4v" -> MediaCodec.MPEG4_ES;
            case "vp08" -> MediaCodec.VP8;
            case "vp09" -> MediaCodec.VP9;
            case "av01" -> MediaCodec.AV1;
            case "jpeg", "mjpg" -> MediaCodec.MJPEG;
            default -> throw new IllegalArgumentException("Unsupported mp4 video codec: " + type);
        };
    }

    private static MediaCodec mapOverrideCodec(String codecParam) {
        return switch (codecParam.trim().toLowerCase(Locale.ROOT)) {
            case "h264", "avc" -> MediaCodec.H264;
            case "h265", "hevc" -> MediaCodec.H265;
            case "mjpeg", "jpeg", "jpg" -> MediaCodec.MJPEG;
            case "mpeg4", "mp4v-es" -> MediaCodec.MPEG4_ES;
            case "vp8" -> MediaCodec.VP8;
            case "vp9" -> MediaCodec.VP9;
            case "av1" -> MediaCodec.AV1;
            default -> throw new IllegalArgumentException("Unsupported codec override: " + codecParam);
        };
    }

    private static MediaTrack createAudioTrack(int trackId, Track track) {
        try {
            int fallbackSampleRate = Math.max(8000, (int) Math.round(track.getTrackMetaData().getTimescale()));
            int fallbackChannels = resolveChannelCount(track);
            AacTrackParameters parameters = resolveAacTrackParameters(track, fallbackSampleRate, fallbackChannels);
            String fmtp = "streamtype=5;profile-level-id=1;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=" + parameters.configHex();
            return new MediaTrack(trackId, "audio", MediaCodec.AAC, 0, "trackID=" + trackId,
                    parameters.sampleRate(), parameters.channels(), fmtp);
        } catch (Exception e) {
            log.warn("Failed to expose AAC track, skipping audio: {}", e.getMessage());
            return null;
        }
    }

    private static Track findSupportedAudioTrack(List<Track> audioTracks) {
        for (Track track : audioTracks) {
            if (isAacTrack(track)) {
                return track;
            }
        }
        return null;
    }

    private static String describeAudioTrackTypes(List<Track> audioTracks) {
        return audioTracks.stream()
                .map(Mp4AccessUnitSource::sampleEntryType)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static String sampleEntryType(Track track) {
        if (track.getSampleEntries() == null || track.getSampleEntries().isEmpty()) {
            return "<none>";
        }
        return track.getSampleEntries().get(0).getType();
    }

    private static boolean isAacTrack(Track track) {
        return track.getSampleEntries() != null
                && !track.getSampleEntries().isEmpty()
                && "mp4a".equalsIgnoreCase(track.getSampleEntries().get(0).getType());
    }

    private static int resolveChannelCount(Track track) {
        Object entry = track.getSampleEntries().get(0);
        try {
            Method method = entry.getClass().getMethod("getChannelCount");
            Object value = method.invoke(entry);
            if (value instanceof Number number) {
                return Math.max(1, number.intValue());
            }
        } catch (Exception ignored) {
        }
        return 2;
    }

    private static int estimateFrameRate(Track track) {
        long[] sampleDurations = track.getSampleDurations();
        long timescale = Math.max(1L, Math.round(track.getTrackMetaData().getTimescale()));
        if (sampleDurations.length == 0) {
            return 25;
        }
        long total = 0;
        int count = Math.min(sampleDurations.length, 32);
        for (int i = 0; i < count; i++) {
            total += sampleDurations[i];
        }
        double averageSeconds = (total / (double) count) / timescale;
        return (int) Math.max(1, Math.round(1.0 / Math.max(averageSeconds, 0.001)));
    }

    private static AacTrackParameters resolveAacTrackParameters(Track track, int fallbackSampleRate, int fallbackChannels) {
        try {
            Object entry = track.getSampleEntries().get(0);
            List<ESDescriptorBox> boxes = ((org.mp4parser.boxes.sampleentry.SampleEntry) entry).getBoxes(ESDescriptorBox.class);
            if (!boxes.isEmpty()) {
                ESDescriptor descriptor = boxes.get(0).getEsDescriptor();
                if (descriptor != null) {
                    DecoderConfigDescriptor decoderConfig = descriptor.getDecoderConfigDescriptor();
                    if (decoderConfig != null) {
                        AudioSpecificConfig audioSpecificConfig = decoderConfig.getAudioSpecificInfo();
                        if (audioSpecificConfig != null && audioSpecificConfig.getConfigBytes() != null && audioSpecificConfig.getConfigBytes().length > 0) {
                            int sampleRate = audioSpecificConfig.getSamplingFrequency() > 0 ? audioSpecificConfig.getSamplingFrequency() : fallbackSampleRate;
                            int channels = audioSpecificConfig.getChannelConfiguration() > 0 ? audioSpecificConfig.getChannelConfiguration() : fallbackChannels;
                            return new AacTrackParameters(sampleRate, Math.max(1, channels), toHex(audioSpecificConfig.getConfigBytes()));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new AacTrackParameters(fallbackSampleRate, Math.max(1, fallbackChannels), buildAacLcConfigHex(fallbackSampleRate, fallbackChannels));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02X", value & 0xFF));
        }
        return builder.toString();
    }

    private static String buildAacLcConfigHex(int sampleRate, int channels) {
        int sampleRateIndex = switch (sampleRate) {
            case 96000 -> 0;
            case 88200 -> 1;
            case 64000 -> 2;
            case 48000 -> 3;
            case 44100 -> 4;
            case 32000 -> 5;
            case 24000 -> 6;
            case 22050 -> 7;
            case 16000 -> 8;
            case 12000 -> 9;
            case 11025 -> 10;
            case 8000 -> 11;
            case 7350 -> 12;
            default -> 4;
        };
        int audioObjectType = 2;
        int channelConfig = Math.max(1, Math.min(channels, 7));
        int config = (audioObjectType << 11) | (sampleRateIndex << 7) | (channelConfig << 3);
        return String.format(Locale.ROOT, "%04X", config);
    }

    private record AacTrackParameters(int sampleRate, int channels, String configHex) {
    }

    private record Mp4FileData(Map<Integer, Mp4TrackData> tracks) {
    }

    private record Mp4TrackData(
            Track track,
            MediaCodec codec,
            int nalLengthSize,
            List<Sample> samples,
            int startSampleIndex,
            int firstSyncSampleIndex,
            List<Long> sampleDurationsMillis,
            List<Long> sampleDurationsNanos,
            List<Long> rtpTimestampIncrements,
            List<Long> presentationTimestampOffsets,
            long playbackCycleTimestampSpan,
            byte[] h264Sps,
            byte[] h264Pps,
            byte[] h265Vps,
            byte[] h265Sps,
            byte[] h265Pps) {
    }
}