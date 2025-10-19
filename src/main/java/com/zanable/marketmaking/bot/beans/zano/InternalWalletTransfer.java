package com.zanable.marketmaking.bot.beans.zano;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Getter
@Setter
@Builder
@ToString
public class InternalWalletTransfer {
    private int id;
    private String requester;
    private String comment;
    private String txid;
    private int status;
    private Timestamp timestamp;
    private BigDecimal amount;
    private String address;
}
