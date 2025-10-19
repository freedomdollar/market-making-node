package com.zanable.marketmaking.bot.beans.market.api;

import com.zanable.marketmaking.bot.beans.ZanoWalletMeta;
import com.zanable.marketmaking.bot.beans.market.Exchange;
import com.zanable.marketmaking.bot.beans.zano.GetWalletBalanceResponse;
import com.zanable.marketmaking.bot.beans.zano.trade.ZanoTradeOrderData;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.json.simple.JSONArray;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@ToString
public class MmStatusResponse {
    private int status;
    private String msg;
    private String floatTokenAssetId;
    private String walletAlias;
    private ZanoTradeOrderData[] activeOrders;
    private BigDecimal currentAskPrice;
    private BigDecimal currentBidPrice;
    private BigDecimal currentZanoPriceAverage;
    private BigDecimal currentZanoPriceAskWeighted;
    private BigDecimal currentZanoPriceBidWeighted;
    private BigDecimal currentAskVolume;
    private BigDecimal currentBidVolume;
    private BigDecimal averageAskVolume;
    private BigDecimal averageBidVolume;
    private long lastCoinexActivityTimestamp;
    private long lastMexcActivityTimestamp;
    private ConcurrentHashMap<Exchange, BigDecimal> currentZanoExchangeBids = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Exchange, BigDecimal> currentZanoExchangeAsks = new ConcurrentHashMap<>();

    private BigDecimal floatTokensIssued;
    private BigDecimal zanoInMarketMakerWallet;
    private BigDecimal floatTokensInMarketMakerWallet;
    private BigDecimal floatTokensInCirculation;
    private BigDecimal zanoInColleteralWallet;
    private BigDecimal zanoInColleteralWalletValue;
    private BigDecimal floatTokensCoverageRatio;

    private JSONArray last20trades = new JSONArray();
    private JSONArray buySellStats = new JSONArray();

    private boolean tradingOpen;

    private HashMap<String, ZanoWalletMeta> walletsAssetBalanceMap = new HashMap<>();
    private HashMap<String, String> appSettings = new HashMap<>();

    private long pairId;

}
