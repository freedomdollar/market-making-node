package com.zanable.marketmaking.bot.beans.market;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class OrderBookOrder implements Comparable<OrderBookOrder>, Serializable {
    private BigDecimal price;
    private BigDecimal volume;
    private OrderType type;
    private Exchange exchange;

    @Override
    public int compareTo(@NotNull OrderBookOrder o) {
        if (this.hashCode() == o.hashCode()) {
            return 0;
        }

        if (this.type.equals(OrderType.BID)) {
            if (o.price.compareTo(this.price) == 0) {
                if (o.volume.compareTo(this.volume) == 0) {
                    if (o.exchange.equals(this.exchange)) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else {
                    return o.volume.compareTo(this.volume);
                }
            }
            return o.price.compareTo(this.price);
        } else {
            if (o.price.compareTo(this.price) == 0) {
                if (o.volume.compareTo(this.volume) == 0) {
                    if (o.exchange.equals(this.exchange)) {
                        return 0;
                    } else {
                        return -1;
                    }
                } else {
                    return o.volume.compareTo(this.volume);
                }
            }
            return this.price.compareTo(o.price);
        }
    }
}
