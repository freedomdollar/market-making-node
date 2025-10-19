package com.zanable.marketmaking.bot.beans;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AppStatusBean {
    private long startTime;
    private boolean databaseReady = false;
    private boolean daemonStarted = false;
    private boolean walletStarted = false;
    private boolean dexTraderDependencyOk = false;
    private boolean dexTraderReady = false;
    private boolean cexTraderDependencyOk = false;
    private boolean cexTraderReady = false;
    private int status;
    private List<String> errorMessages = new ArrayList<>();

    public AppStatusBean(long startTime) {
        this.startTime = startTime;
    }
}
