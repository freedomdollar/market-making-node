package com.zanable.marketmaking.bot.beans.market.api;

import com.zanable.marketmaking.bot.beans.market.OrderBookOrder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.SortedSet;

@Getter
@Setter
@ToString
public class OrderBookResponse {
    private int status = 500;
    private String msg = "D/N";
    private BigDecimal lowestAsk;
    private BigDecimal weightedAsk;
    private BigDecimal lastUsedAsk;
    private BigDecimal askVolume;
    private BigDecimal askVolumeAverage;
    private BigDecimal lastUsedAskVolume;

    private BigDecimal highestBid;
    private BigDecimal weightedBid;
    private BigDecimal lastUsedBid;
    private BigDecimal bidVolume;
    private BigDecimal bidVolumeAverage;
    private BigDecimal lastUsedBidVolume;

    private Timestamp lastUpdate;
    private SortedSet<OrderBookOrder> asks;
    private SortedSet<OrderBookOrder> bids;
}
