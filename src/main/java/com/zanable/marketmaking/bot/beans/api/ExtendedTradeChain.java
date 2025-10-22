package com.zanable.marketmaking.bot.beans.api;

import com.zanable.marketmaking.bot.beans.market.SimplifiedTrade;
import com.zanable.marketmaking.bot.enums.TradeType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Builder
@ToString
public class ExtendedTradeChain {
    /*
    int decimals = rs.getInt("decimals");
                BigDecimal tokenAmount = new BigDecimal(rs.getString("token_amount")).movePointLeft(decimals);
                trade.put("tokensTraded", tokenAmount);
                BigDecimal zanoTraded = new BigDecimal(rs.getString("zano_amount")).movePointLeft(12);
                trade.put("zanoTraded", zanoTraded);
                trade.put("ticker", rs.getString("ticker"));
                trade.put("zanoUsdtPrice", rs.getString("zano_usdt_price"));
                trade.put("type", rs.getString("type"));
                trade.put("timestamp", rs.getString("timestamp"));
                trade.put("myOrderId", rs.getLong("my_order_id"));
                trade.put("otherOrderId", rs.getLong("other_order_id"));
     */
    private int decimals;
    private BigDecimal tokensTraded;
    private BigDecimal zanoTraded;
    private String ticker;
    private BigDecimal zanoUsdtPrice;
    private TradeType type;
    private Timestamp timestamp;
    private long timestampLong;
    private long myOrderId;
    private long otherOrderId;
    private UUID seqId;

    private SimplifiedTrade zanoSellOrder;
    private SimplifiedTrade fusdBuyOrder;
    private SimplifiedTrade fusdSellOrder;
    private SimplifiedTrade zanoBuyOrder;
}
