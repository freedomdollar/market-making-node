package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.BigInteger;

@Getter
@Setter
@ToString
public class DepositBean {
    private BigDecimal amount;
    private String coin;
    private String network;
    private int status;
    private String address;
    private String txId;
    private long insertTime;
    private BigInteger unlockConfirm;
    private BigInteger confirmTimes;
    private String memo;
    private String transHash;
    private long updateTime;
    public String netWork;
}
