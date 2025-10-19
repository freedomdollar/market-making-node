package com.zanable.marketmaking.bot.beans.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class AppSettingsBean {

    private BigDecimal dexAskMultiplier = BigDecimal.valueOf(1.005);
    private BigDecimal dexBidMultiplier = BigDecimal.valueOf(0.995);
    private BigDecimal zanoSellPercent = BigDecimal.valueOf(100);
    private BigDecimal zanoSellPriceMultiplier = BigDecimal.valueOf(1.005);
    private BigDecimal dexMinimumTokenSellVolume = BigDecimal.valueOf(5000);
    private BigDecimal dexMinimumTokenBuyVolume = BigDecimal.valueOf(5000);
    private BigDecimal dexMaximumTokenSellVolume = BigDecimal.valueOf(10000);
    private BigDecimal dexMaximumTokenBuyVolume = BigDecimal.valueOf(10000);
    private BigDecimal dexPriceMoveThreshold = BigDecimal.valueOf(0.5);

    private boolean zanoSellEnabled = false;
    private boolean fusdBuyEnabled = false;
    private boolean zanoMoveFromWalletEnabled = false;
    private boolean mexcFusdWithdrawEnabled = false;
    private boolean autoStartDexTradeBot = false;

    private BigDecimal zanoMoveToCexThreshold;
    private BigDecimal zanoMoveToCexMinTransfer;
    private BigDecimal fusdMoveToWalletTheshold;
    private BigDecimal fusdMoveToWalletMinTransfer;

    private String mexcApiKey;
    private String mexcApiSecret;
    private String mexcDepositAddressZano;
    private String mexcDepositAddressFusd;
}
