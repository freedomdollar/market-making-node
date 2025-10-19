package com.zanable.marketmaking.bot.beans;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BotDashNew {
    private int status = 500;
    private String message = "Error";

    private BigDecimal mainWalletZanoBalance = BigDecimal.ZERO;
    private BigDecimal mainWalletFusdBalance = BigDecimal.ZERO;
    private BigDecimal mexcUsdtBalance = BigDecimal.ZERO;
    private BigDecimal mexcFusdBalance = BigDecimal.ZERO;
    private BigDecimal mexcZanoBalance = BigDecimal.ZERO;
}
