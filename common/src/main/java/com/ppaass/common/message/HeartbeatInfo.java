package com.ppaass.common.message;

public class HeartbeatInfo {
    private String id;
    private Long utcDateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUtcDateTime() {
        return utcDateTime;
    }

    public void setUtcDateTime(Long utcDateTime) {
        this.utcDateTime = utcDateTime;
    }
}
