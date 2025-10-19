package com.zanable.marketmaking.bot.beans.Mexc;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@ToString
public class SpotOrderBookResponse {
    private long lastUpdateId;
    private List<BigDecimal[]> bids;
    private List<BigDecimal[]> asks;

}
