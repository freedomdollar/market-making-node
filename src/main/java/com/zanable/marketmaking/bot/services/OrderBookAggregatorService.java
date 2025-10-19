package com.zanable.marketmaking.bot.services;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.market.Exchange;
import com.zanable.marketmaking.bot.beans.market.OrderBookOrder;
import com.zanable.marketmaking.bot.beans.market.OrderType;
import com.zanable.shared.interfaces.ApplicationService;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OrderBookAggregatorService implements ApplicationService {

    private static Gson gson = new Gson();

    // Logger for readable and useful logs
    private final static Logger logger = LoggerFactory.getLogger(OrderBookAggregatorService.class);

    // Single threaded order executor to avoid concurrency problems
    private ScheduledExecutorService orderExecutor = Executors.newSingleThreadScheduledExecutor();
    private static ConcurrentLinkedQueue<OrderBookOrder> orderQueue = new ConcurrentLinkedQueue<OrderBookOrder>();

    // Sets for asks
    private static SortedSet<OrderBookOrder> asksAggregatedTmp = Collections.synchronizedSortedSet(new TreeSet());
    @Getter
    private static SortedSet<OrderBookOrder> asksAggregated = Collections.synchronizedSortedSet(new TreeSet());

    // Sets for bids
    private static SortedSet<OrderBookOrder> bidsAggregatedTmp = Collections.synchronizedSortedSet(new TreeSet());
    @Getter
    private static SortedSet<OrderBookOrder> bidsAggregated = Collections.synchronizedSortedSet(new TreeSet());
    @Getter
    private static Timestamp lastUpdate;

    public OrderBookAggregatorService() {
    }

    @Override
    public void init() {
        logger.info("Starting Order Book Aggregator Service");

        orderExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                while (!orderQueue.isEmpty()) {
                    try {
                        OrderBookOrder order = orderQueue.poll();

                        if (order.getType().equals(OrderType.BID)) {
                            OrderBookOrder returnedOrder = handleBidOrder(order);
                            if (returnedOrder.getVolume().compareTo(BigDecimal.ZERO) > 0) {
                                bidsAggregatedTmp.add(returnedOrder);
                            }
                        } else if (order.getType().equals(OrderType.ASK)) {
                            OrderBookOrder returnedOrder = handleAskOrder(order);
                            if (returnedOrder.getVolume().compareTo(BigDecimal.ZERO) > 0) {
                                asksAggregatedTmp.add(returnedOrder);
                            }
                        } else if (order.getType().equals(OrderType.DELETE)) {
                            ArrayList<OrderBookOrder> deleteList = new ArrayList<>();
                            for (OrderBookOrder orderBookOrder : bidsAggregatedTmp) {
                                if (orderBookOrder.getExchange().equals(order.getExchange())) {
                                    deleteList.add(orderBookOrder);
                                }
                            }
                            for (OrderBookOrder orderBookOrder : asksAggregatedTmp) {
                                if (orderBookOrder.getExchange().equals(order.getExchange())) {
                                    deleteList.add(orderBookOrder);
                                }
                            }
                            for (OrderBookOrder orderBookOrder : deleteList) {
                                asksAggregatedTmp.remove(orderBookOrder);
                                bidsAggregatedTmp.remove(orderBookOrder);
                            }
                        } else if (order.getType().equals(OrderType.COMMIT)) {
                            // Deep copy the sets to avoid concurrency problems
                            SortedSet<OrderBookOrder> newAskSet = Collections.synchronizedSortedSet(new TreeSet());
                            newAskSet.addAll(asksAggregatedTmp);
                            asksAggregated = newAskSet;

                            SortedSet<OrderBookOrder> newBidSet = Collections.synchronizedSortedSet(new TreeSet());
                            newBidSet.addAll(bidsAggregatedTmp);
                            bidsAggregated = newBidSet;
                        }
                        lastUpdate = new Timestamp(System.currentTimeMillis());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }, 0, 1, TimeUnit.MILLISECONDS);
    }

    /**
     * Add order to single threaded queue
     * @param order
     */
    public static void addOrderToQueue(OrderBookOrder order) {
        orderQueue.add(order);
    }

    /**
     * Delete all orders for a specific exchange
     * @param exchange
     */
    public static void deleteOrders(Exchange exchange) {
        orderQueue.add(new OrderBookOrder(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderType.DELETE,
                exchange
        ));
    }

    /**
     * Commit the sets being updated by the matching engine
     */
    public static void commitSets() {
        orderQueue.add(new OrderBookOrder(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderType.COMMIT,
                Exchange.MOOT
        ));
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Match a bid order
     * @param order
     * @return order with updated status
     */
    private OrderBookOrder handleBidOrder(OrderBookOrder order) {
        if (asksAggregatedTmp.isEmpty()) {
            return order;
        }
        // Compare the price
        if (order.getPrice().compareTo(asksAggregatedTmp.first().getPrice()) >= 0) {
            // Bid order collided with the ask

            // If the bid volume is greater than the ask volume, eat the whole ask
            if (order.getVolume().compareTo(asksAggregatedTmp.first().getVolume()) > 0) {
                // Make the bid eat the ask
                order.setVolume(order.getVolume().subtract(asksAggregatedTmp.first().getVolume()));

                // First ask has been eaten
                // logger.info("Deleting order: " + asksAggregatedTmp.first());
                asksAggregatedTmp.remove(asksAggregatedTmp.first());

                // Now the order should still have a positive volume
                return handleBidOrder(order);
            } else if (order.getVolume().compareTo(asksAggregatedTmp.first().getVolume()) == 0) {
                // Make the bid eat the ask
                // logger.info("Deleting order: " + asksAggregatedTmp.first());
                asksAggregatedTmp.remove(asksAggregatedTmp.first());
                // Order has been eaten
                order.setVolume(BigDecimal.ZERO);
                // logger.info("Zero volume order: " + order);
                return order;
            } else {
                // Ask has been eaten
                OrderBookOrder firstBid = asksAggregatedTmp.first();
                firstBid.setVolume(firstBid.getVolume().subtract(order.getVolume()));
                // Order has been eaten
                order.setVolume(BigDecimal.ZERO);
                // logger.info("Zero volume order: " + order);
                return order;
            }
        }
        return order;
    }

    /**
     * Match an ask order
     * @param order
     * @return order with updated status
     */
    private OrderBookOrder handleAskOrder(OrderBookOrder order) {
        if (bidsAggregatedTmp.isEmpty()) {
            return order;
        }
        // Compare the price
        if (order.getPrice().compareTo(bidsAggregatedTmp.first().getPrice()) <= 0) {
            // Bid order collided with the ask

            if (order.getVolume().compareTo(bidsAggregatedTmp.first().getVolume()) > 0) {
                // Make the bid eat the ask
                order.setVolume(order.getVolume().subtract(bidsAggregatedTmp.first().getVolume()));

                // First bid has been eaten
                bidsAggregatedTmp.remove(bidsAggregatedTmp.first());
                // logger.info("Deleting order: " + asksAggregatedTmp.first());

                // Now the order should still have a positive volume
                return handleAskOrder(order);
            } else if (order.getVolume().compareTo(bidsAggregatedTmp.first().getVolume()) == 0) {
                // Make the bid eat the ask
                bidsAggregatedTmp.remove(bidsAggregatedTmp.first());
                // Order has been eaten
                order.setVolume(BigDecimal.ZERO);
                // logger.info("Zero volume order: " + order);
                return order;
            } else {
                // Ask has been eaten
                OrderBookOrder firstBid = bidsAggregatedTmp.first();
                firstBid.setVolume(firstBid.getVolume().subtract(order.getVolume()));
                // Order has been eaten
                order.setVolume(BigDecimal.ZERO);
                // logger.info("Zero volume order: " + order);
                return order;
            }
        }
        return order;
    }

    /**
     * Get a sum of the volume up to a certain price
     * @param type
     * @param priceTarget
     * @return Volume
     */
    public static BigDecimal getPartialVolume(OrderType type, BigDecimal priceTarget) {
        BigDecimal volume = BigDecimal.ZERO;

        if (type.equals(OrderType.BID)) {
            for (OrderBookOrder orderBookOrder : bidsAggregated) {
                if (orderBookOrder.getPrice().compareTo(priceTarget) >= 0) {
                    volume = volume.add(orderBookOrder.getVolume());
                }
            }
        } else if (type.equals(OrderType.ASK)) {
            for (OrderBookOrder orderBookOrder : asksAggregated) {
                if (orderBookOrder.getPrice().compareTo(priceTarget) <= 0) {
                    volume = volume.add(orderBookOrder.getVolume());
                }
            }
        }
        return volume;
    }

    /**
     * Fired when the service shuts down
     */
    @Override
    public void destroy() {
        logger.info("Stopping Order Book Aggregator Service");
        orderExecutor.shutdown();
    }
}
