package com.zanable.marketmaking.bot.beans;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Getter
@Setter
@ToString
public class WalletTransaction {
    private String txId;
    private String walletIdent;
    private BigDecimal amount;
    private String assetId;
    private String remoteAddress;
    private String remoteAlias;
    private String ticker;
    private String fullName;
    private Timestamp timestamp;
    private long height;
    private boolean isIncome;
    private boolean isMining;
    private int subTransferIndex;
    private long transactionIndex;
    private long swapId;
}
