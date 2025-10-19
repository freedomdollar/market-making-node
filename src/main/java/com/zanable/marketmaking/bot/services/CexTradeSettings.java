package com.zanable.marketmaking.bot.services;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.Mexc.AccountAssetResponse;
import com.zanable.marketmaking.bot.beans.Mexc.AccountBalance;
import com.zanable.marketmaking.bot.beans.Mexc.OrderStatus;
import com.zanable.marketmaking.bot.beans.Mexc.SpotOrderResponse;
import com.zanable.marketmaking.bot.beans.market.FusdUsdtBuyReq;
import com.zanable.marketmaking.bot.beans.market.ZanoSellReq;
import com.zanable.marketmaking.bot.exchangeintegration.MexcSpotClient;
import com.zanable.shared.interfaces.ApplicationService;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CexTradeSettings implements ApplicationService {

    private final static Logger logger = LoggerFactory.getLogger(CexTradeSettings.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private static Gson gson = new Gson();
    private static MexcSpotClient mexcSpotClient;

    private static String tradeSymbolZano = "ZANOUSDT";
    private static String tradeSymbolFusd = "FUSDUSDT";

    @Getter
    private static HashMap<String, String> appSettings;

    // Variables set by Database AppSettings
    private static String mexcApiKey = null;
    private static String mexcApiSecret = null;
    private static String mexcDepositAddressZano = null;
    private static String mexcDepositAddressFusd = null;
    private static BigDecimal fusdWithdrawThreshold = BigDecimal.valueOf(200);
    private static BigDecimal zanoMoveThreshold = BigDecimal.valueOf(0);
    private static BigDecimal zanoMinBalanceFloor = BigDecimal.valueOf(0);
    private static BigDecimal fusdMoveThreshold = BigDecimal.valueOf(0);
    private static BigDecimal fusdMinBalanceFloor = BigDecimal.valueOf(0);

    public CexTradeSettings() {
        updateAppSettings();
        mexcSpotClient = new MexcSpotClient(mexcApiKey, mexcApiSecret);
    }

    @Override
    public void init() {

        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    getZanoSellReqs();
                    checkZanoBalance();
                    checkExchangeBalance();
                    processPendingFusdBuyRequests();
                    updateAppSettings();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 5000, 4000, TimeUnit.MILLISECONDS);

    }

    @Override
    public void destroy() {
        scheduledExecutorService.shutdown();
    }

    private void checkZanoBalance() {
        BigInteger zanoBalance = ZanoWalletService.getZanoDataMain().getUnlocked();
        BigDecimal zanoBalanceDecimal = new BigDecimal(zanoBalance).movePointLeft(12);
        // logger.info("Zano balance is " + zanoBalanceDecimal.toPlainString() + ", move threshold is " + zanoMoveThreshold.toPlainString());
        if (zanoMoveThreshold.compareTo(BigDecimal.ZERO) > 0 && zanoBalanceDecimal.compareTo(zanoMoveThreshold) >= 0) {

            BigDecimal zanoToSend = zanoBalanceDecimal.subtract(zanoMinBalanceFloor);
            try {
                logger.info("MexcMoveLogic: Zano balance is " + zanoBalanceDecimal.toPlainString());
                logger.info("MexcMoveLogic: Preparing to send "+zanoToSend.toPlainString()+" ZANO to Mexc. Move threshold is " + zanoBalanceDecimal + " and min balance floor is " + zanoMinBalanceFloor);
                // SendResponse response = ZanoWalletService.sendCoins(mexcDepositAddressZano, zanoToSend.movePointRight(12).longValue(), null);
                /*
                System.out.println(gson.toJson(response));
                if (response.getTxhash() != null) {
                    DatabaseService.insertZanoMove(null, "MainHotwallet", "Mexc", mexcDepositAddressZano, response.getTxhash(), zanoToSend);
                }
                 */
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

    private void checkExchangeBalance() {
        try {
            BigDecimal fUsdBalance = BigDecimal.ZERO;
            AccountAssetResponse accountAssetResponse = mexcSpotClient.getBalanceForAsset();
            if (accountAssetResponse.getBalances() != null) {
                for (AccountBalance balance : accountAssetResponse.getBalances()) {
                    if (balance.getAsset().equals("FUSD")) {
                        fUsdBalance = balance.getAvailable();
                        break;
                    }
                }
            }

            if (fusdMoveThreshold.compareTo(BigDecimal.ZERO) > 0 && fusdMoveThreshold.compareTo(fUsdBalance) > 0) {
                BigDecimal fusdToSend = fUsdBalance.subtract(fusdMinBalanceFloor);
                if (fusdToSend.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        logger.info("Preparing to send "+fusdToSend.toPlainString()+" fUSD from Mexc to main wallet");
                        // SendResponse response = ZanoWalletService.sendCoins(mexcDepositAddressZano, zanoToSend.movePointRight(12).longValue(), null);
                        // System.out.println(gson.toJson(response));
                        // String withdrawRes = mexcSpotClient.withdraw("FUSD", ZanoWalletService.getWalletAddress(), fusdToSend.toPlainString(), "ZANO", "", null, null, null);
                        // System.out.println(withdrawRes);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void getZanoSellReqs() {

        List<ZanoSellReq> getActiveOrders = DatabaseService.getActiveSellOrders();

        for (ZanoSellReq order : getActiveOrders) {
            try {
                if (order.getSeqId() == null) {
                    UUID newUuid = DatabaseService.updateUsdtBuyOrderWithUUID(order.getId());
                    if (newUuid != null) {
                        order.setSeqId(newUuid);
                    }
                    logger.info("Updated ZANO sell order " + order.getId() + " with UUID " + newUuid);
                }
                if (order.getSeqId() == null) {
                    continue;
                }
                System.out.println(gson.toJson(order));

                String orderQueryResp = mexcSpotClient.queryOrder(tradeSymbolZano, order.getCexOrderId(), null);
                OrderStatus orderStatus = gson.fromJson(orderQueryResp, OrderStatus.class);
                if (orderStatus.getOrderId() == null && orderStatus.getStatus() == null) {
                    System.err.println(orderQueryResp);
                    if (orderStatus.getCode() == -2013) {
                        DatabaseService.updateZanoSellOrder(order.getId(), -1);
                    }
                    continue;
                }
                logger.info("Zano sell order " + orderStatus.getOrderId() + " has status " + orderStatus.getStatus());

                if (orderStatus.getStatus().equals("FILLED") || orderStatus.getStatus().equals("CANCELED") ) {
                    System.out.println("Order filled at " + orderStatus.getCummulativeQuoteQty().toPlainString());
                    int status = 2;
                    if (orderStatus.getStatus().equals("FILLED")) {
                        status = 3;
                    }
                    DatabaseService.updateZanoSellOrder(order.getId(), status, orderStatus.getCummulativeQuoteQty(), orderStatus.getOrderId());
                    DatabaseService.insertSimplifiedTrade(null, "ZANO", orderStatus.getExecutedQty(),
                            "USDT", orderStatus.getCummulativeQuoteQty(),
                            "USDT", BigDecimal.ZERO, orderStatus.getTime(), order.getConnectedOrderId(), 2, "SELL", order.getSeqId());
                    // placeFusdBuyOrder(orderStatus.getCummulativeQuoteQty(), order.getConnectedOrderId(), order.getSeqId());
                    if (SettingsService.getAppSettingSafe("enable_fusd_buy") != null && SettingsService.getAppSettingSafe("enable_fusd_buy").equals("1")) {
                        DatabaseService.insertFusdBuyOrder(null, orderStatus.getCummulativeQuoteQty(), order.getConnectedOrderId(), order.getSeqId());
                    }
                } else {
                    DatabaseService.updateZanoSellOrder(order.getId(), 1, orderStatus.getCummulativeQuoteQty(), orderStatus.getOrderId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        List<ZanoSellReq> sells = DatabaseService.getZanoSellOrders();

        for (ZanoSellReq buyBack : sells) {

            logger.info("Got ZANO sell order for " + buyBack.getZanoPrice());
            System.out.println(gson.toJson(buyBack));

            try {
                String placeOrderResponse = mexcSpotClient.placeOrder(
                        tradeSymbolZano,
                        MexcSpotClient.Side.SELL,
                        MexcSpotClient.Type.LIMIT,
                        buyBack.getAmount().toPlainString(),
                        null,
                        buyBack.getZanoPrice().toPlainString(),
                        buyBack.getSeqId().toString().replace("-", ""));

                SpotOrderResponse spotOrderResponse = gson.fromJson(placeOrderResponse, SpotOrderResponse.class);
                if (spotOrderResponse.getOrderId() != null) {
                    System.out.println("Updating order");
                    DatabaseService.updateZanoSellOrder(buyBack.getId(), 1, spotOrderResponse.getOrigQty(), spotOrderResponse.getOrderId(), buyBack.getZanoPrice());
                } else {
                    System.out.println("OrderId was null");
                    System.out.println(placeOrderResponse);
                    if (placeOrderResponse.contains("The minimum transaction volume cannot be less than")) {
                        DatabaseService.updateZanoSellOrder(buyBack.getId(), -1, BigDecimal.ZERO, spotOrderResponse.getOrderId(), buyBack.getZanoPrice());
                    } else if (placeOrderResponse.contains("Oversold")) {
                        // DatabaseService.updateZanoSellOrder(buyBack.getId(), -2);
                    }
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void processPendingFusdBuyRequests() {

        List<FusdUsdtBuyReq> buyReqs = DatabaseService.getFusdBuyOrders();
        for (FusdUsdtBuyReq buyReq : buyReqs) {
            System.out.println("Processing FUSD buy order " + buyReq.getId() + " seq " + buyReq.getSeqId().toString());
            try {
                String placeOrderResponse = mexcSpotClient.placeOrder(
                        tradeSymbolFusd,
                        MexcSpotClient.Side.BUY,
                        MexcSpotClient.Type.MARKET,
                        null,
                        buyReq.getUsdtAmount().setScale(3, RoundingMode.DOWN).toPlainString(),
                        null,
                        UUID.randomUUID().toString().replace("-", ""));

                System.out.println(placeOrderResponse);

                SpotOrderResponse spotOrderResponse = gson.fromJson(placeOrderResponse, SpotOrderResponse.class);
                System.out.println(spotOrderResponse);
                if (spotOrderResponse.getOrderId() != null) {
                    System.out.println("Updating order");
                    Thread.sleep(500);
                    String orderQueryResp = mexcSpotClient.queryOrder(tradeSymbolFusd, spotOrderResponse.getOrderId(), null);
                    // System.out.println(orderQueryResp);
                    OrderStatus orderStatus = gson.fromJson(orderQueryResp, OrderStatus.class);
                    System.out.println(gson.toJson(orderStatus));

                    // Insert fUSD BUY into database
                    // DatabaseService.updateZanoSellOrder(buyBack.getId(), 1, spotOrderResponse.getOrigQty(), spotOrderResponse.getOrderId(), buyBack.getZanoPrice());
                    DatabaseService.updateFusdBuyOrder(buyReq.getId(), spotOrderResponse.getOrderId(), orderStatus.getOrigQuoteOrderQty(),
                            orderStatus.getCummulativeQuoteQty(), orderStatus.getPrice(), orderStatus.getExecutedQty(), 3);
                    DatabaseService.insertSimplifiedTrade(null, "FUSD", orderStatus.getExecutedQty(),
                            "USDT", orderStatus.getCummulativeQuoteQty(),
                            "USDT", BigDecimal.ZERO, orderStatus.getTime(), buyReq.getConnectedOrderId(), 3, "BUY", buyReq.getSeqId());
                } else {
                    System.out.println("OrderId was null");
                    DatabaseService.updateFusdBuyOrder(buyReq.getId(), -1, spotOrderResponse.getMsg());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void updateAppSettings() {
        appSettings = DatabaseService.getAppSettingsFromDb();
        if (appSettings.containsKey("mexc_apikey")) {
            mexcApiKey = appSettings.get("mexc_apikey");
        }
        if (appSettings.containsKey("mexc_apisecret")) {
            mexcApiSecret = appSettings.get("mexc_apisecret");
        }
        if (appSettings.containsKey("fusd_withdraw_threshold")) {
            fusdWithdrawThreshold = new BigDecimal(appSettings.get("fusd_withdraw_threshold"));
        }
        if (appSettings.containsKey("zano_move_threshold")) {
            zanoMoveThreshold = new BigDecimal(appSettings.get("zano_move_threshold"));
        }
        if (appSettings.containsKey("zano_min_balance_floor")) {
            zanoMinBalanceFloor = new BigDecimal(appSettings.get("zano_min_balance_floor"));
        }
        if (appSettings.containsKey("mexc_deposit_address_zano")) {
            mexcDepositAddressZano = appSettings.get("mexc_deposit_address_zano");
        }
        if (appSettings.containsKey("mexc_deposit_address_fusd")) {
            mexcDepositAddressFusd = appSettings.get("mexc_deposit_address_fusd");
        }
        if (appSettings.containsKey("fusd_move_threshold")) {
            fusdMoveThreshold = new BigDecimal(appSettings.get("zano_move_threshold"));
        }
        if (appSettings.containsKey("fusd_min_balance_floor")) {
            fusdMinBalanceFloor = new BigDecimal(appSettings.get("zano_min_balance_floor"));
        }
    }
}
