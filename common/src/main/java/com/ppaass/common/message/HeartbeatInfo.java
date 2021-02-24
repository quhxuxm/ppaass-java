package com.ppaass.common.message;

public class HeartbeatInfo {
    private final String id;
    private final Long utcDateTime;

    public HeartbeatInfo(String id, Long utcDateTime) {
        this.id = id;
        this.utcDateTime = utcDateTime;
    }

    public String getId() {
        return id;
    }

    public Long getUtcDateTime() {
        return utcDateTime;
    }
}
