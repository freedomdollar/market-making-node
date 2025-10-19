package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AccountAssetResponse {
    /*
    {"makerCommission":null,
    "takerCommission":null,
    "buyerCommission":null,
    "sellerCommission":null,
    "canTrade":true,"canWithdraw":true,
    "canDeposit":true,"updateTime":null,
    "accountType":"SPOT",
    "balances":[{"asset":"ZANO","free":"55","locked":"0","available":"55"}],"permissions":["SPOT"]}
     */
    private BigDecimal makerCommission;
    private BigDecimal takerCommission;
    private BigDecimal buyerCommission;
    private BigDecimal sellerCommission;
    private boolean canTrade;
    private boolean canWithdraw;
    private boolean canDeposit;
    private String accountType;
    private List<AccountBalance> balances = new ArrayList<>();

}
