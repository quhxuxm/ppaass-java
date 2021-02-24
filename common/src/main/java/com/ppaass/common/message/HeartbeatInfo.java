package com.ppaass.common.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class HeartbeatInfo {
    private final String id;
    private final Long utcDateTime;

    @JsonCreator
    public HeartbeatInfo(
            @JsonProperty("id")
                    String id,
            @JsonProperty("utcDateTime")
                    Long utcDateTime) {
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
