package com.yoben.tcp.rtspserver.session;

import com.yoben.tcp.rtspserver.media.MediaSession;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description RTSP 会话管理组件。
 * @Author Yoben
 * @Since 2026-03-19 16:12:00
 */
public class RtspSessionManager {

    private final Map<String, RtspSession> sessions = new ConcurrentHashMap<>();

    public RtspSession create(MediaSession mediaSession) {
        RtspSession session = new RtspSession(mediaSession);
        sessions.put(session.sessionId(), session);
        return session;
    }

    public Optional<RtspSession> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        RtspSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }

    public void closeAll() {
        sessions.values().forEach(RtspSession::close);
        sessions.clear();
    }
}
