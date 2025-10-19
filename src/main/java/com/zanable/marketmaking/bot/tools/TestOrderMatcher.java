package com.zanable.marketmaking.bot.tools;

import com.zanable.marketmaking.bot.beans.market.Exchange;
import com.zanable.marketmaking.bot.beans.market.OrderBookOrder;
import com.zanable.marketmaking.bot.beans.market.OrderType;
import com.zanable.marketmaking.bot.services.OrderBookAggregatorService;

import java.math.BigDecimal;

public class TestOrderMatcher {
    public static void main(String[] args) {

        OrderBookAggregatorService aggregatorService = new OrderBookAggregatorService();
        aggregatorService.init();

        for (int i=8; i<=16; i++) {
            OrderBookOrder ask = new OrderBookOrder(
                    BigDecimal.valueOf(i),
                    BigDecimal.valueOf(3),
                    OrderType.ASK,
                    Exchange.COINEX
            );
            OrderBookAggregatorService.addOrderToQueue(ask);
        }
        for (int i=1; i<=10; i++) {
            OrderBookOrder bid = new OrderBookOrder(
                    BigDecimal.valueOf(i),
                    BigDecimal.valueOf(i),
                    OrderType.BID,
                    Exchange.COINEX
            );
            OrderBookAggregatorService.addOrderToQueue(bid);
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println(OrderBookAggregatorService.getAsksAggregated());
        System.out.println(OrderBookAggregatorService.getBidsAggregated());

        aggregatorService.destroy();

    }
}
