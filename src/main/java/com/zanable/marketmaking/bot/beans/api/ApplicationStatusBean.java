package com.zanable.marketmaking.bot.beans.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationStatusBean {
    private boolean appStarted = false;
    private boolean zanoDaemonActive = false;
    private boolean zanoWalletServiceActive = false;
    private boolean walletHasAlias = false;
    private boolean walletHasPendingAlias = false;
    private boolean zanoDexTradingServiceActive = false;
    private boolean zanoDexTradingBotActive = false;
    private boolean zanoCexTradingServiceActive = false;
}
