package com.zanable.marketmaking.bot.services;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.zano.AssetInfo;
import com.zanable.marketmaking.bot.beans.zano.BotSwapResult;
import com.zanable.marketmaking.bot.beans.zano.SwapProposalInfo;
import com.zanable.marketmaking.bot.beans.zano.trade.*;
import com.zanable.shared.exceptions.NoApiResponseException;
import com.zanable.shared.interfaces.ApplicationService;
import com.zanable.shared.tools.PostHandler;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.Polling;
import lombok.Getter;
import org.apache.http.client.HttpResponseException;
import org.checkerframework.checker.units.qual.A;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ZanoTradeService implements ApplicationService {

    private final static Logger logger = LoggerFactory.getLogger(ZanoTradeService.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Socket socket;
    private static PostHandler postHandler = new PostHandler();
    private final static Gson gson = new Gson();
    private static SecureRandom secureRandom = new SecureRandom();

    // Final variables
    private static final String tradeUrl = "https://api.trade.zano.org";
    private static final int zanoDecimals = 12;

    // State
    @Getter
    private static boolean tradingOpen = false;
    private static HashMap<Long, Long> lastRenew = new HashMap<>(); // Last renew of the instant label per order

    // Variables set by config
    private static String tokenAssetId;
    private static int tokenDecimals;
    @Getter
    private static long pairId;
    private static String zanoAssetId = "d6329b5b1f7c0805b5c345f4957554002a2f557845f64d7645dae0e051a6498a";
    private static String zanoTradeApiToken;

    @Getter
    private static HashMap<String, String> appSettings = new HashMap<>();
    private static BigDecimal zanoSellOrderVolumeMultiplier = BigDecimal.valueOf(1);
    private static BigDecimal zanoSellOrderPriceMultiplier = BigDecimal.valueOf(1);

    // Keep track of our own orders
    private static HashMap<Long, ZanoTradeOrderData> activeOrders = new HashMap<>();
    private static HashMap<Long, BigDecimal> orderZanoPriceMap = new HashMap<>();

    public ZanoTradeService() {

        try {
            // Set the asset id variable
            tokenAssetId = SettingsService.getAppSetting("trade_token_asset_id");

            if (SettingsService.getAppSettingSafe("enable_autostart_dex_bot") != null && SettingsService.getAppSettingSafe("enable_autostart_dex_bot").equals("1")) {
                tradingOpen = true;
            }

            // Get asset info
            AssetInfo assetInfo = ZanoWalletService.getAssetInfo(tokenAssetId);
            tokenDecimals = assetInfo.getDecimal_point();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init() {
        try {
            // Authenticate to API
            ZanoTradeAuthResponse authResponse = ZanoWalletService.authenticateToZanoTrade();
            if (authResponse.isSuccess()) {
                zanoTradeApiToken = authResponse.getData();
            } else {
                throw new Exception("Could not authenticate with trade API");
            }

            updateAppSettings();

            // Get the pair ID from trade.zano.org
            pairId = getZanoTradePairId(tokenAssetId);

            // Cancel all orders when we startup the bot, just in case there are orders left
            cancelAllOrders();

            // Socket IO stuff since trade.zano.org uses socket.io :(
            String[] transports = new String[1];
            IO.Options options = IO.Options.builder()
                    // IO factory options
                    .setForceNew(true)
                    .setMultiplex(true)

                    // low-level engine options
                    .setTransports(new String[] { Polling.NAME })
                    .setUpgrade(true)
                    .setRememberUpgrade(true)
                    .setPath("/socket.io/")
                    .setQuery(null)
                    .setExtraHeaders(null)

                    // Manager options
                    .setReconnection(true)
                    .setReconnectionAttempts(Integer.MAX_VALUE)
                    .setReconnectionDelay(1_000)
                    .setReconnectionDelayMax(5_000)
                    .setRandomizationFactor(0.5)
                    .setTimeout(20_000)

                    // Socket options
                    // .setAuth(null)
                    .build();
            socket = IO.socket("https://api.trade.zano.org", options);

            // JSON request object to listen to trades for our specific trade pair
            JSONObject tradeListener = new JSONObject();
            tradeListener.put("id", pairId);

            // JSON request object to listen to in dex notifications. Not sure if it does anything. reversed engineered from trade.zano.org
            JSONObject inDexNotifications = new JSONObject();
            inDexNotifications.put("token", zanoTradeApiToken);

            /*
            socket.onAnyIncoming(new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    // System.out.println("Any incoming: " + gson.toJson(args));
                }
            });
             */

            // Connect to the trade.zano.org websocket and emit messages to listen to the things we need to listen to
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    logger.info("Connected to DEX with WS: " + socket.id());
                    try {
                        socket.emit("in-trading", tradeListener, (Ack) args2 -> {
                            JSONObject response = (JSONObject) args2[0];
                        });
                        socket.emit("in-dex-notifications", inDexNotifications, (Ack) args2 -> {
                            JSONObject response = (JSONObject) args2[0];
                        });
                        socket.emit("out-dex-notifications", inDexNotifications, (Ack) args2 -> {
                            JSONObject response = (JSONObject) args2[0];
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });


            socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    logger.error("WS disconnect: " + socket.id());
                }
            });

            socket.on("error", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    logger.error("WS Error: " + socket.id());
                }
            });

            socket.on("reconnect_attempt", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    logger.info("WS reconnect attempt: " + socket.id());
                }
            });

            socket.on("reconnect", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    logger.info("ES reconnect: " + socket.id()); // null
                }
            });

            socket.on("new-order", new Emitter.Listener() {
                @Override
                public void call(Object... args) {

                    // Check if we have a matching order
                    try {
                        ZanoTradeUserPage userPage = getUserPage();

                        // Here we check if there is any other users' order that matches with our order. Right no, no action is taken
                        // instead, action is taken when a new order is observed via websocket. This is just for logging purposes.
                        // If we want to make the bot also a market taker instead of only a market maker, orders can be matched here
                        for (ZanoTradeOrderData orderData : userPage.getData().getApplyTips()) {
                            long myOrderId = orderData.getConnected_order_id();
                            logger.info("New order: WS Matched order " + orderData.getId() + " with our order " + myOrderId + ", no action taken right now.");
                            logger.info(gson.toJson(orderData));

                            // Check if we should take this order
                            if (orderData.getType().toUpperCase().equals("SELL")) {
                                // They are selling tokens to us, we are sending them ZANO
                                // Compare the price
                                ZanoTradeOrderData myMatchedOrder = activeOrders.get(myOrderId);
                                logger.info("Matched sell order with price of " + orderData.getPrice().toPlainString()
                                        + " compared to our order " + activeOrders.get(myOrderId).getPrice().toPlainString());
                            }
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            socket.on("delete-order", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    logger.info("Delete order: " + gson.toJson(args));
                    JSONParser jsonParser = new JSONParser();
                    try {
                        JSONArray jsonArray = (JSONArray) jsonParser.parse(gson.toJson(args));
                        for (Object obj : jsonArray.toArray()) {
                            JSONObject jsonObject = (JSONObject) obj;
                            if (jsonObject.containsKey("map")) {
                                JSONObject map = (JSONObject) jsonObject.get("map");
                                String orderIdString = (String) map.get("orderId");
                                long orderId = Long.valueOf(orderIdString);

                                if (activeOrders.containsKey(orderId)) {
                                    ZanoTradeOrderData orderData = activeOrders.get(orderId);
                                    if (orderData.getType().toUpperCase().equals("SELL")) {
                                        // Place a new fresh ask order
                                        activeOrders.remove(orderId);
                                        ZanoPriceService.sendAskOrderToTradeService(true);
                                        // placeAskOrder(MarketMakingService.getCurrentAskPrice(), MarketMakingService.getCurrentAskVolume().multiply(MarketMakingService.getCurrentAskPrice()));
                                    } else {
                                        // Place a new fresh bid order
                                        activeOrders.remove(orderId);
                                        ZanoPriceService.sendBidOrderToTradeService(true);
                                    }
                                    logger.info("Deleted order: " + orderId);
                                }
                            }
                        }
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            // This message is sent when a user on the DEX is matching with our order and sending a signed ionic swap request
            // The data sent is empty, so we need to fetch our user page and then figure out which order that is matching.
            // Not great, but it works.
            socket.on("update-orders", new Emitter.Listener() {
                @Override
                public void call(Object... args) {

                    // Check if we have a matching order
                    try {
                        // User page is fetched from the DEX backend API
                        ZanoTradeUserPage userPage = getUserPage();

                        for (ZanoTradeOrderData orderData : userPage.getData().getApplyTips()) {
                            handleMatchedOrder(orderData);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            socket.onAnyOutgoing(new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    logger.info("Outgoing WS message: " + gson.toJson(args));
                }
            });

            socket.connect();
            socket.open();

            // Ping the zano trade backend
            scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        updateAppSettings();
                        runEvery10();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, 5000, 10000, TimeUnit.MILLISECONDS);

        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static BotSwapResult processDirectSwap(long orderId, String rawProp, BigDecimal amount) throws NoApiResponseException, IOException {

        if (!activeOrders.containsKey(orderId)) {
            return new BotSwapResult(false, "449", "No order with that ID. The order probably timed out. Please try again.");
        }

        // Get the market maker's order
        ZanoTradeOrderData myOrder = activeOrders.get(orderId);

        if (amount.compareTo(myOrder.getLeft()) > 0) {
            return new BotSwapResult(false, "509", "Requested amount is larger than the current amount being offered.");
        }

        // Decode the proposal
        SwapProposalInfo swapProposalInfo = ZanoWalletService.decodeSwapProposal(rawProp);

        ZanoTradeOrderData fauxOrder = new ZanoTradeOrderData();
        fauxOrder.setPrice(myOrder.getPrice());
        fauxOrder.setHex_raw_proposal(rawProp);
        fauxOrder.setId(-17);
        fauxOrder.setConnected_order_id(orderId);
        fauxOrder.setLeft(amount);
        fauxOrder.setAmount(amount);

        // Different legic depending on if it's sell or buy
        if (myOrder.getType().toUpperCase().equals("SELL")) {
            // We are selling tokens, sending tokens to the user
            fauxOrder.setType("buy");
        } else if (myOrder.getType().toUpperCase().equals("BUY")) {
            // We are buying tokens, sending tokens to the user
            fauxOrder.setType("sell");
        }
        return handleMatchedOrder(fauxOrder);
    }

    private static BotSwapResult handleMatchedOrder(ZanoTradeOrderData orderData) throws IOException {
        logger.info("Update-order: WS Matched order: " + orderData.getId());
        logger.info(gson.toJson(orderData));
        long myOrderId = orderData.getConnected_order_id();
        ZanoTradeOrderData myOrder = activeOrders.get(myOrderId);
        BotSwapResult swapResult = new BotSwapResult();

        if (orderData.getHex_raw_proposal() != null && !orderData.getHex_raw_proposal().isEmpty()) {
            ZanoPriceService.setBlockNewOrders(true);
            try {
                SwapProposalInfo swapProposalInfo = ZanoWalletService.decodeSwapProposal(orderData.getHex_raw_proposal());
                logger.info("Swap proposal decoded: " + swapProposalInfo);

                boolean rejectProposal = false;
                // We are BUYING (receiving) tokens, SELLING (sending) Zano
                if (orderData.getType().toUpperCase().equals("SELL")) {
                    // Tokens sold
                    BigDecimal tokensSold = orderData.getLeft();
                    BigDecimal tokensProposed = new BigDecimal(swapProposalInfo.getTo_finalizer()[0].getAmount()).movePointLeft(tokenDecimals);

                    logger.info("Tokens bought: " + tokensSold.toPlainString() + ", tokens proposed: " + tokensProposed.toPlainString());

                    if (tokensSold.compareTo(tokensProposed) == 0) {
                        BigDecimal sellPrice = orderData.getPrice();
                        BigDecimal amount = sellPrice.multiply(tokensSold);
                        BigDecimal proposalAmount = new BigDecimal(swapProposalInfo.getTo_initiator()[0].getAmount()).movePointLeft(zanoDecimals);
                        logger.info("ZANO amount in order: " + amount.toPlainString() + ", amount in proposal: " + proposalAmount.toPlainString());

                        if (amount.compareTo(proposalAmount) == 0) {
                            logger.info("Proposal matches order");

                            // Accept the swap and send the tokens to the Zano network
                            String txId = ZanoWalletService.acceptSwapProposal(orderData.getHex_raw_proposal());
                            logger.info("Swap accepted: " + txId);
                            swapResult.setAccepted(true);
                            swapResult.setTxid(txId);
                            swapResult.setReason("OK");
                            DatabaseService.insertSwap(null, swapProposalInfo, txId, true, null, myOrderId, orderData.getId());
                            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                            BigDecimal zanoPriceUsdt = myOrder.getZanoPrice();

                            // Tell the DEX backend we have confirmed the trade
                            confirmTransaction(orderData.getId(), myOrderId, myOrder.getLeft());

                            UUID seqId = UUID.randomUUID();

                            // Log the trade to the database
                            DatabaseService.insertTradeLog(null, myOrderId, orderData.getId(), tokensProposed.movePointRight(tokenDecimals).toBigInteger(),
                                    proposalAmount.movePointRight(zanoDecimals).toBigInteger(), "buy", txId, timestamp,
                                    sellPrice.movePointRight(12).toBigInteger(), zanoPriceUsdt, swapProposalInfo.getTo_finalizer()[0].getAsset_id(), seqId);

                            // Run the fUSD sell seq on the CEX
                            if (SettingsService.getAppSettingSafe("enable_fusd_sell") != null && SettingsService.getAppSettingSafe("enable_fusd_sell").equals("1")) {
                                DatabaseService.insertFusdToUsdtCexOrder(null, myOrderId, BigDecimal.ZERO, tokensProposed, seqId);
                                logger.info("Inserted fUSD -> USDT sell order for CEX trading service");
                            } else {
                                logger.info("Not inserting fUSD -> USDT sell order, enable_fusd_sell is " + SettingsService.getAppSettingSafe("enable_fusd_sell"));
                            }

                            // Set left
                            myOrder.setLeft(myOrder.getLeft().subtract(tokensSold));
                            // ZanoWalletService.getAssetBalanceMap("main").get("")
                            logger.info("Order has " + myOrder.getLeft() + " tokens left");

                            DatabaseService.updateOrder(myOrderId, 2, myOrder.getLeft());

                            // Update wallet balance
                            ZanoWalletService.updateAllAssetBalances();

                            // Compare the wallet balance to what's left in the order
                            BigDecimal zanoBalance = new BigDecimal(ZanoWalletService.getAssetBalanceMap("main").get(zanoAssetId).getUnlocked()).movePointLeft(zanoDecimals);
                            logger.info( zanoBalance.toPlainString() + " ZANO are unlocked after this order");

                            // If we have less tokens than available in the wallet, place a new order
                            if (zanoBalance.compareTo(myOrder.getLeft()) < 0) {
                                logger.info("ZANO amount is less than current unlocked ZANO. Placing a new order.");
                                ZanoPriceService.setBlockNewOrders(false);
                                if (zanoBalance.compareTo(BigDecimal.ZERO) > 0) {
                                    ZanoPriceService.sendBidOrderToTradeService(true);
                                } else {
                                    logger.error("No unlocked ZANO available. Cancelling order.");
                                    deleteOrder(myOrderId, -4);
                                }

                            } else if (myOrder.getLeft().compareTo(myOrder.getAmount().multiply(BigDecimal.valueOf(0.5))) < 0) {
                                // If the amount left is less than 50% of the original order, place a new one
                                logger.info("Amount left is less than 50% of the original order. Placing a new order.");
                                ZanoPriceService.setBlockNewOrders(false);
                                ZanoPriceService.sendBidOrderToTradeService(true);
                            }
                        } else {
                            // This will happen if the user's proposal amount doesn't match exactly
                            logger.error("Proposal rejected because zano amounts didn't match");
                            rejectProposal = true;
                            DatabaseService.insertSwap(null, swapProposalInfo, null, false, "Zano amounts didn't match", myOrderId, orderData.getId());
                            swapResult.setReason("Proposal rejected because ZANO amounts didn't match. Wanted " + amount.toPlainString() + " but got " + proposalAmount.toPlainString());
                        }
                    } else {
                        // This will happen if the user's proposal price doesn't match exactly
                        logger.error("Proposal rejected because token amounts didn't match");
                        rejectProposal = true;
                        DatabaseService.insertSwap(null, swapProposalInfo, null, false, "Token amounts didn't match", myOrderId, orderData.getId());
                        swapResult.setReason("Proposal rejected because token amounts didn't match. Wanted " + tokensSold.toPlainString() + " but got " + tokensProposed.toPlainString());
                    }
                } else if (orderData.getType().toUpperCase().equals("BUY")) {
                    // We are SELLING (sending) tokens, BUYING (receiving) Zano
                    BigDecimal tokensSold = orderData.getLeft();
                    BigDecimal tokensProposed = new BigDecimal(swapProposalInfo.getTo_initiator()[0].getAmount()).movePointLeft(tokenDecimals);

                    logger.info("Tokens sold: " + tokensSold.toPlainString() + ", tokens proposed: " + tokensProposed.toPlainString());

                    if (tokensSold.compareTo(tokensProposed) == 0) {
                        BigDecimal sellPrice = orderData.getPrice();
                        BigDecimal amount = sellPrice.multiply(tokensSold).setScale(12, RoundingMode.DOWN);
                        BigDecimal proposalAmount = new BigDecimal(swapProposalInfo.getTo_finalizer()[0].getAmount()).movePointLeft(zanoDecimals);
                        logger.info("Amount in order: " + amount.toPlainString() + ", amount in proposal: " + proposalAmount.toPlainString());

                        if (amount.compareTo(proposalAmount) == 0) {
                            logger.info("Proposal matches order");

                            // Accept the swap and send the zano to the Zano network
                            String txId = ZanoWalletService.acceptSwapProposal(orderData.getHex_raw_proposal());
                            logger.info("Swap accepted: " + txId);
                            swapResult.setAccepted(true);
                            swapResult.setTxid(txId);
                            swapResult.setReason("OK");
                            DatabaseService.insertSwap(null, swapProposalInfo, txId, true, null, myOrderId, orderData.getId());
                            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                            BigDecimal zanoPriceUsdt = myOrder.getZanoPrice();

                            // Tell the DEX backend we have confirmed the trade
                            confirmTransaction(orderData.getId(), myOrderId, myOrder.getLeft());

                            UUID seqId = UUID.randomUUID();

                            // Log the trade to the database
                            DatabaseService.insertTradeLog(null, myOrderId, orderData.getId(), tokensProposed.movePointRight(tokenDecimals).toBigInteger(),
                                    proposalAmount.movePointRight(zanoDecimals).toBigInteger(), "sell", txId, timestamp,
                                    sellPrice.movePointRight(zanoDecimals).toBigInteger(), zanoPriceUsdt, swapProposalInfo.getTo_initiator()[0].getAsset_id(), seqId);

                            // Set left
                            myOrder.setLeft(myOrder.getLeft().subtract(tokensSold));

                            // Cancel the order, place a new one to make sure we only offer what's available in the wallet
                            DatabaseService.updateOrder(myOrderId, 2, myOrder.getLeft());

                            // Update wallet balance
                            ZanoWalletService.updateAllAssetBalances();

                            // Place ZANO sell order for USDT
                            BigDecimal price = amount.divide(tokensSold, 4, RoundingMode.HALF_UP);
                            System.out.println("Sell price multiplier is " + zanoSellOrderPriceMultiplier);
                            BigDecimal sellPriceZanoUsdt = zanoPriceUsdt.multiply(zanoSellOrderPriceMultiplier);

                            if (SettingsService.getAppSettingSafe("enable_zano_sell") != null && SettingsService.getAppSettingSafe("enable_zano_sell").equals("1")) {
                                logger.info("Inserting ZANO sell order @" + sellPriceZanoUsdt);
                                DatabaseService.insertZanoSellOrder(null, myOrderId,
                                        proposalAmount.multiply(sellPriceZanoUsdt).multiply(zanoSellOrderVolumeMultiplier),
                                        proposalAmount.multiply(zanoSellOrderVolumeMultiplier),
                                        sellPriceZanoUsdt,
                                        seqId
                                );
                            }

                            DatabaseService.insertSimplifiedTrade(null, "FUSD", tokensSold,
                                    "ZANO", amount,
                                    "ZANO", BigDecimal.valueOf(0.01), System.currentTimeMillis(), myOrderId, 1, "SELL", seqId);

                            // Compare the wallet balance to what's left in the order
                            BigDecimal tokenBalance = new BigDecimal(ZanoWalletService.getAssetBalanceMap("main").get(tokenAssetId).getUnlocked()).movePointLeft(tokenDecimals);

                            logger.info( tokenBalance.toPlainString() + " tokens are unlocked after this order");

                            if (tokenBalance.compareTo(BigDecimal.ZERO) > 0) {

                            }
                            // If we have less tokens tNew ask order hit by minimum tokens to sell constraint in settings. Original volumehan available in the wallet, place a new order
                            if (tokenBalance.compareTo(myOrder.getLeft()) < 0) {
                                logger.info("Token amount is less than current unlocked tokens. Placing a new order.");
                                ZanoPriceService.setBlockNewOrders(false);
                                if (tokenBalance.compareTo(BigDecimal.ZERO) > 0) {
                                    ZanoPriceService.sendAskOrderToTradeService(true);
                                } else {
                                    logger.error("Can't place new order since we have no unlocked tokens. Cancelling order.");
                                    deleteOrder(myOrderId, -4);
                                }

                            } else if (myOrder.getLeft().compareTo(myOrder.getAmount().multiply(BigDecimal.valueOf(0.5))) < 0) {
                                // If the amount left is less than 50% of the original order, place a new one
                                logger.info("Amount left is less than 50% of the original order. Placing a new order.");
                                ZanoPriceService.setBlockNewOrders(false);
                                ZanoPriceService.sendAskOrderToTradeService(true);
                            }

                        } else {
                            // This will happen if the user's proposal amount doesn't match exactly
                            rejectProposal = true;
                            DatabaseService.insertSwap(null, swapProposalInfo, null, false, "Zano amounts didn't match", myOrderId, orderData.getId());
                            swapResult.setReason("Proposal rejected because ZANO amounts didn't match. Wanted " + amount.toPlainString() + " but got " + proposalAmount.toPlainString());
                        }
                    } else {
                        // This will happen if the user's proposal price doesn't match exactly
                        rejectProposal = true;
                        DatabaseService.insertSwap(null, swapProposalInfo, null, false, "Token amounts didn't match", myOrderId, orderData.getId());
                        swapResult.setReason("Proposal rejected because token amounts didn't match. Wanted " + tokensSold.toPlainString() + " but got " + tokensProposed.toPlainString());
                    }
                }

                if (rejectProposal) {
                    logger.error("Rejected proposal for order " + orderData.getId());
                    // Delete my own order
                    deleteOrder(myOrderId, -3);
                    if (orderData.getType().toUpperCase().equals("SELL")) {
                        // Place a new fresh ask order
                        ZanoPriceService.setBlockNewOrders(false);
                        ZanoPriceService.sendBidOrderToTradeService(true);
                    } else {
                        // Place a new fresh bid order
                        ZanoPriceService.setBlockNewOrders(false);
                        ZanoPriceService.sendAskOrderToTradeService(true);
                    }
                }

            } catch (NoApiResponseException e) {
                e.printStackTrace();
            }
            ZanoPriceService.setBlockNewOrders(false);
        } else {
            // This one should not happen, but you never know
            logger.info("No swap proposal sent with order " + orderData.getId());
        }
        return swapResult;
    }

    @Override
    public void destroy() {
        cancelAllOrders();
        socket.close();
        socket.disconnect();
    }

    private void runEvery10() {
        try {
            newBotInstantLabel();
        } catch (IOException e) {
            logger.error("Could not ping trade backend: " + e.getMessage());
        }
    }

    private static ZanoTradeUserPage getUserPage() throws IOException {
        String response = postHandler.sendJsonPost(tradeUrl + "/api/orders/get-user-page",
                new ZanoTradeUserOrdersPageRequest(zanoTradeApiToken, pairId));
        return gson.fromJson(response, ZanoTradeUserPage.class);
    }

    private static void deleteOrder(long orderId, int status) throws IOException {
        activeOrders.remove(orderId);
        DatabaseService.closeOrder(orderId, status, new Timestamp(System.currentTimeMillis()));
        ZanoTradeDeleteOrderRequest delReq = new ZanoTradeDeleteOrderRequest(orderId, zanoTradeApiToken);
        String response = postHandler.sendJsonPost(tradeUrl + "/api/orders/cancel", delReq);
        activeOrders.remove(orderId);
    }

    private static void confirmTransaction(long orderId, long myOrderId, BigDecimal left) throws IOException {
        ZanoTradeConfirmTransactionRequest delReq = new ZanoTradeConfirmTransactionRequest(orderId, zanoTradeApiToken);
        String response = postHandler.sendJsonPost(tradeUrl + "/api/transactions/confirm", delReq);
        DatabaseService.updateOrder(myOrderId, 2, left);
        logger.info("Confirming transaction with order id " + orderId + ", " + response);
    }

    private static void renewBot(long orderId) throws IOException {
        long lastRenewTime = 0;
        if (lastRenew.containsKey(orderId)) {
            lastRenewTime = lastRenew.get(orderId);
        }
        if (System.currentTimeMillis() - lastRenewTime > 5000) {
            ZanoTradeDeleteOrderRequest delReq = new ZanoTradeDeleteOrderRequest(orderId, zanoTradeApiToken);
            String response = postHandler.sendJsonPost(tradeUrl + "/api/dex/renew-bot", delReq);
            lastRenew.put(orderId, System.currentTimeMillis());
            JSONParser parser = new JSONParser();
            try {
                JSONObject renewTime = (JSONObject) parser.parse(response);
                if (renewTime.containsKey("data")) {
                    long expirationTimestamp = (long) ((JSONObject)renewTime.get("data")).get("expirationTimestamp");
                    long timeLeft = expirationTimestamp - System.currentTimeMillis();
                }

            } catch (Exception e) {
                System.err.println("Could not parse JSON: " + response);
                e.printStackTrace();
            }
        }
    }

    public static void closeTrading() {
        tradingOpen = false;
        cancelBidOrders();
        cancelAskOrders();
    }

    public static void openTrading() {
        tradingOpen = true;
        ZanoPriceService.sendBidOrderToTradeService(true);
        ZanoPriceService.sendAskOrderToTradeService(true);
    }

    private static void newBotInstantLabel() throws IOException {
        ZanoTradeUserPage userPage = getUserPage();
        if (tradingOpen) {
            for (ZanoTradeOrderData orderData : userPage.getData().getOrders()) {
                renewBot(orderData.getId());
            }
        }
    }

    public static synchronized void placeAskOrder(BigDecimal price, BigDecimal volume, BigDecimal usdRate) throws IOException {
        cancelAskOrders();
        if (!tradingOpen) {
            return;
        }
        List<Long> deleteList = new ArrayList<>();
        for (long orderId : activeOrders.keySet()) {
            if (activeOrders.get(orderId).getType().toUpperCase().equals("SELL") ) {
                logger.error("ASK order " + orderId + " excists when it shuld not");
                deleteList.add(orderId);
            }
        }
        for (long orderId : deleteList) {
            activeOrders.remove(orderId);
        }

        ZanoTradeCreateOrderData order = new ZanoTradeCreateOrderData(
                OrderType.sell,
                OrderSide.limit,
                price.toPlainString(),
                volume.setScale(tokenDecimals, RoundingMode.DOWN).toPlainString(),
                pairId,
                usdRate
        );
        try {
            ZanoTradeCreateOrderResponse createOrderResponse = gson.fromJson(postHandler.sendJsonPost(tradeUrl + "/api/orders/create",
                    new ZanoTradeCreateOrderRequest(zanoTradeApiToken, order)), ZanoTradeCreateOrderResponse.class);
            logger.info("Placed ask order:" + gson.toJson(order) + ", " + createOrderResponse.isSuccess());
            logger.info(gson.toJson(createOrderResponse));

            if (createOrderResponse.isSuccess()) {

                ZanoTradeOrderData orderData = createOrderResponse.getData();
                if (orderData == null || orderData.getId() == 0) {
                    ZanoTradeUserPage userPage = getUserPage();
                    for (ZanoTradeOrderData maybeMyOrder : userPage.getData().getOrders()) {
                        if (maybeMyOrder.getAmount().compareTo(new BigDecimal(order.getAmount())) == 0 && maybeMyOrder.getPrice().compareTo(new BigDecimal(order.getPrice())) == 0) {
                            orderData = maybeMyOrder;
                            orderData.setTotal(orderData.getTotal().setScale(zanoDecimals, RoundingMode.DOWN));
                            orderData.setPrice(orderData.getPrice().setScale(zanoDecimals, RoundingMode.DOWN));
                        }
                    }
                }

                orderData.setZanoPrice(order.getZanoPrice());
                activeOrders.put(orderData.getId(), orderData);
                DatabaseService.insertOrderLog(null, orderData.getId(), orderData.getTotal(),
                        orderData.getPrice(), orderData.getLeft(), 1, new Timestamp(Long.valueOf(orderData.getTimestamp())), null, orderData.getType(), orderData.getZanoPrice());
            }
        } catch (Exception e) {
            logger.error("Could not place ask order: " + gson.toJson(order));
            e.printStackTrace();
        }
    }

    public static synchronized void placeBidOrder(BigDecimal price, BigDecimal volume, BigDecimal usdRate) throws IOException {
        cancelBidOrders();
        if (!tradingOpen) {
            return;
        }

        List<Long> deleteList = new ArrayList<>();
        for (long orderId : activeOrders.keySet()) {
            if (activeOrders.get(orderId).getType().toUpperCase().equals("BUY") ) {
                logger.error("BID order " + orderId + " excists when it should not");
                deleteList.add(orderId);
            }
        }

        for (long orderId : deleteList) {
            activeOrders.remove(orderId);
        }

        ZanoTradeCreateOrderData order = new ZanoTradeCreateOrderData(
                OrderType.buy,
                OrderSide.limit,
                price.toPlainString(),
                volume.setScale(tokenDecimals, RoundingMode.DOWN).toPlainString(),
                pairId,
                usdRate
        );

        try {
            ZanoTradeCreateOrderResponse createOrderResponse = gson.fromJson(postHandler.sendJsonPost(tradeUrl + "/api/orders/create",
                    new ZanoTradeCreateOrderRequest(zanoTradeApiToken, order)), ZanoTradeCreateOrderResponse.class);

            logger.info("Placed bid order:" + gson.toJson(order) + ", " + createOrderResponse.isSuccess());

            logger.info(gson.toJson(createOrderResponse));
            if (createOrderResponse.isSuccess()) {
                ZanoTradeOrderData orderData = createOrderResponse.getData();
                if (orderData == null || orderData.getId() == 0) {
                    ZanoTradeUserPage userPage = getUserPage();
                    for (ZanoTradeOrderData maybeMyOrder : userPage.getData().getOrders()) {
                        if (maybeMyOrder.getAmount().compareTo(new BigDecimal(order.getAmount())) == 0 && maybeMyOrder.getPrice().compareTo(new BigDecimal(order.getPrice())) == 0) {
                            orderData = maybeMyOrder;
                        }
                    }
                }

                orderData.setZanoPrice(order.getZanoPrice());
                activeOrders.put(orderData.getId(), orderData);
                DatabaseService.insertOrderLog(null, orderData.getId(), orderData.getTotal(),
                        orderData.getPrice(), orderData.getLeft(), 1, new Timestamp(Long.valueOf(orderData.getTimestamp())), null, orderData.getType(), orderData.getZanoPrice());
            }
        } catch (Exception e) {
            logger.error("Could not place ask order: " + gson.toJson(order));
            e.printStackTrace();
        }
    }

    public static void cancelAllOrders() {
        try {
            ZanoTradeUserPage userPage = getUserPage();
            logger.info("Cancelling all orders");
            for (ZanoTradeOrderData orderData : userPage.getData().getOrders()) {
                try {
                    deleteOrder(orderData.getId(), -2);
                } catch (HttpResponseException e) {
                    logger.error("Could not cancel order. DEX backend rejected the request.");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void cancelAskOrders() {
        try {
            ZanoTradeUserPage userPage = getUserPage();

            for (ZanoTradeOrderData orderData : userPage.getData().getOrders()) {
                if (orderData.getType().toUpperCase().equals("SELL")) {
                    boolean matching = false;
                    long age = 0;
                    for (ZanoTradeOrderData potentialMatchingOrder : userPage.getData().getApplyTips()) {
                        long timestamp = 0;
                        if (potentialMatchingOrder.getTimestamp() != null) {
                            timestamp = Long.valueOf(potentialMatchingOrder.getTimestamp());
                        }
                        age = System.currentTimeMillis() - timestamp;
                        if (potentialMatchingOrder.getConnected_order_id() == orderData.getId() && age < 30000) {
                            matching = true;
                        }
                    }
                    if (!matching) {
                        logger.info("Cancelling ask order " + orderData.getId() + ", " + orderData.getLeft() + "@" + orderData.getPrice());
                        try {
                            deleteOrder(orderData.getId(), -1);
                        } catch (HttpResponseException e) {
                            logger.error("Could not delete order from DEX");
                        }

                    } else {
                        logger.error("Not deleting order yet because of matching order, age " + age);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void cancelBidOrders() {
        try {
            ZanoTradeUserPage userPage = getUserPage();

            for (ZanoTradeOrderData orderData : userPage.getData().getOrders()) {
                if (orderData.getType().toUpperCase().equals("BUY")) {
                    boolean matching = false;
                    long age = 0;
                    for (ZanoTradeOrderData potentialMatchingOrder : userPage.getData().getApplyTips()) {
                        long timestamp = 0;
                        if (potentialMatchingOrder.getTimestamp() != null) {
                            timestamp = Long.valueOf(potentialMatchingOrder.getTimestamp());
                        }
                        age = System.currentTimeMillis() - timestamp;
                        if (potentialMatchingOrder.getConnected_order_id() == orderData.getId() && age < 30000) {
                            matching = true;
                        }
                    }
                    if (!matching) {
                        logger.info("Cancelling bid order " + orderData.getId() + ", " + orderData.getLeft() + "@" + orderData.getPrice());
                        try {
                            deleteOrder(orderData.getId(), -1);
                        } catch (HttpResponseException e) {
                            logger.error("Could not delete order from DEX");
                        }
                    } else {
                        logger.error("Not deleting order yet because of matching order, age: " + age);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void ApplyOrder(ZanoTradeApplyOrderData applyOrderData) throws IOException {
        ZanoTradeApplyOrder applyOrder = new ZanoTradeApplyOrder(zanoTradeApiToken, applyOrderData);
        String response = postHandler.sendJsonPost(tradeUrl + "/api/orders/apply-order", applyOrder);
        logger.info("Apply order data: " + applyOrderData.getId() + ", " + response);
    }

    public static ZanoTradeOrderData[] getActiveUserOrders() {
        return activeOrders.values().toArray(new ZanoTradeOrderData[0]);
    }

    /**
     * Get the pair id from the DEX API
     * @param assetId
     * @return trade pair id
     * @throws NoApiResponseException
     */
    private long getZanoTradePairId(String assetId) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("page", 1);
        payload.put("searchText", assetId);
        payload.put("sortOption", "VOLUME_HIGH_TO_LOW");
        payload.put("whitelistedOnly", false);
        JSONObject responseRaw = sendRequest("https://trade.zano.org/api/dex/get-pairs-page", payload);
        if (responseRaw.containsKey("success") && responseRaw.get("success") instanceof Boolean && (boolean)responseRaw.get("success") == true ) {
            JSONArray dataArray = (JSONArray) responseRaw.get("data");
            JSONObject data = (JSONObject) dataArray.get(0);
            return (long) data.get("id");
        }
        throw new NoApiResponseException();
    }

    private static JSONObject sendRequest(String url, JSONObject payload) throws NoApiResponseException {
        // logger.info("Sending request to Zano Wallet " + url);

        HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
        HttpHeaders headers = new HttpHeaders();
        headers.put("Content-Type", "application/json");
        headers.put("accept", "application/json");

        try {
            HttpResponse httpResponse = requestFactory.buildPostRequest(
                            new GenericUrl(url), ByteArrayContent.fromString("application/json", payload.toJSONString())).setHeaders(headers).setReadTimeout(40000)
                    .execute();
            if (httpResponse.isSuccessStatusCode()) {
                try (InputStream is = httpResponse.getContent(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    String text = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));
                    return (JSONObject) new JSONParser().parse(text);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new NoApiResponseException();
    }

    private static List<BigDecimal> cutupBuyBackAmounts(List<BigDecimal> amountList, BigDecimal amountLeft) {
        if (amountLeft.compareTo(BigDecimal.valueOf(400)) <= 0) {
            amountList.add(amountLeft);
            return amountList;
        }
        double ceiling = amountLeft.intValue()*0.8;
        BigDecimal random = BigDecimal.valueOf(secureRandom.nextInt(319, (int) ceiling));
        BigDecimal thisItem = amountLeft.subtract(random);

        if (random.compareTo(BigDecimal.valueOf(400)) > 0) {
            amountList.add(thisItem);
            amountList.addAll(cutupBuyBackAmounts(new ArrayList<BigDecimal>(), random));
        } else {
            amountList.add(thisItem);
            amountList.add(random);
        }

        return amountList;
    }

    public static void updateAppSettings() {
        appSettings = DatabaseService.getAppSettingsFromDb();
        if (appSettings.containsKey("zano_sell_volume_multiplier")) {
            zanoSellOrderVolumeMultiplier = new BigDecimal(appSettings.get("zano_sell_volume_multiplier"));
        }
        if (appSettings.containsKey("zano_sell_price_multiplier")) {
            zanoSellOrderPriceMultiplier = new BigDecimal(appSettings.get("zano_sell_price_multiplier"));
        }
    }
}
