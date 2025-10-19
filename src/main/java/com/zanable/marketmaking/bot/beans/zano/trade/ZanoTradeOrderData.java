package com.zanable.marketmaking.bot.beans.zano.trade;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
public class ZanoTradeOrderData implements Comparable<ZanoTradeOrderData> {
    private BigDecimal amount;
    private String createdAt;
    private boolean hasNotification;
    private long id;
    private boolean isInstant;
    private BigDecimal left;
    private long pairId;
    private BigDecimal price;
    private String side;
    private String status;
    private String timestamp;
    private BigDecimal total;
    private String type;
    private String updatedAt;
    private ZanoTradeUser user;
    private long connected_order_id;
    private String hex_raw_proposal;
    private transient BigDecimal zanoPrice;

    @Override
    public int compareTo(@NotNull ZanoTradeOrderData o) {
        return this.price.compareTo(o.getPrice());
    }
}
