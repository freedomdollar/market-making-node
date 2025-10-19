package com.zanable.marketmaking.bot.beans.zano;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ServiceEntry {
    private String body;
    private long flags;
    private String instruction;
    private String security;
    private String service_id;

    public ServiceEntry(String body, long flags, String instruction, String security, String service_id) {
        this.body = body;
        this.flags = flags;
        this.instruction = instruction;
        this.security = security;
        this.service_id = service_id;
    }
}
