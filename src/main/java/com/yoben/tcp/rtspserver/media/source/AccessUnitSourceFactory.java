package com.yoben.tcp.rtspserver.media.source;

import com.yoben.tcp.rtspserver.media.MediaSession;
import java.util.Locale;

/**
 * @Description 访问单元数据源工厂组件。
 * @Author Yoben
 * @Since 2026-03-13 17:35:00
 */
public class AccessUnitSourceFactory {

    private final AccessUnitSource syntheticSource = new SyntheticMediaSource();
    private final AccessUnitSource fileSource = new FileAccessUnitSource();
    private final AccessUnitSource mp4Source = new Mp4AccessUnitSource();

    public AccessUnitSource create(MediaSession mediaSession) {
        if (!mediaSession.synthetic() && mediaSession.sourceFile() != null) {
            String fileName = mediaSession.sourceFile().getFileName() == null
                    ? mediaSession.sourceFile().toString()
                    : mediaSession.sourceFile().getFileName().toString();
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
                return mp4Source;
            }
            return fileSource;
        }
        return syntheticSource;
    }
}
