package com.zanable.marketmaking.bot.services;

import com.google.gson.Gson;
import com.mxc.push.common.protobuf.PublicLimitDepthV3ApiItem;
import com.mxc.push.common.protobuf.PublicLimitDepthsV3Api;
import com.zanable.marketmaking.bot.beans.market.Exchange;
import com.zanable.marketmaking.bot.beans.market.OrderBookOrder;
import com.zanable.marketmaking.bot.beans.market.OrderType;
import com.zanable.marketmaking.bot.exchangeintegration.CoinexWebsocketClientEndpoint;
import com.zanable.marketmaking.bot.exchangeintegration.MexcWebsocketClientEndpoint;
import com.zanable.shared.interfaces.ApplicationService;
import lombok.Getter;
import lombok.Setter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;

public class ZanoPriceService implements ApplicationService {

    private final static Logger logger = LoggerFactory.getLogger(ZanoPriceService.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private static Gson gson = new Gson();

    // Coinex setup
    private CoinexWebsocketClientEndpoint clientEndPointCoinex;
    private static CoinexWebsocketClientEndpoint.MessageHandler coinexMessageHandler = null;
    private static boolean restartCoinex = false;
    private boolean subscribedToCoinex = false;
    private String coinexApiKey;
    private String coinexSecretKey;

    // Mexc setup
    private MexcWebsocketClientEndpoint clientEndPointMexc;
    private static MexcWebsocketClientEndpoint.MessageHandler mexcMessageHandler = null;
    private static boolean restartMexc = false;
    private boolean subscribedToMexc = false;
    private static long mexcOrderBookUpdate = 0;

    // Service variables
    private static long startTime;
    private static boolean populatedPrices = false;
    private static boolean firstAskOrder = true;
    private static boolean firstBidOrder = true;
    private static boolean isShuttingDown = false;
    private static long serviceStartup;
    @Getter
    @Setter
    private static boolean blockNewOrders = false;
    @Getter
    private static Timestamp lastCoinex;
    @Getter
    private static Timestamp lastMexc;

    // Setting variables
    @Getter
    private static HashMap<String, String> appSettings;
    private static BigDecimal priceMovementThreshold = BigDecimal.valueOf(0.5);
    private static BigDecimal askMargin = BigDecimal.valueOf(1.01);
    private static BigDecimal bidMargin = BigDecimal.valueOf(0.99);
    private static BigDecimal minimumVolumeBuy = BigDecimal.valueOf(100);
    private static BigDecimal maximumVolumeBuy = BigDecimal.valueOf(3000);
    private static BigDecimal minimumVolumeSell = BigDecimal.valueOf(500);
    private static BigDecimal maximumVolumeSell = BigDecimal.valueOf(6000);

    // State variables
    // ##### Ask
    @Getter
    private static BigDecimal lowestAsk;
    @Getter
    private static BigDecimal weightedAsk;
    @Getter
    private static BigDecimal lastWeightedAsk;
    @Getter
    private static BigDecimal askVolume;
    @Getter
    private static BigDecimal lastAskVolume = BigDecimal.ZERO;
    @Getter
    private static BigDecimal averageAskVolume = BigDecimal.ZERO;
    @Getter
    private static long lastAskOrderUpdate = 0;
    private static LinkedList<BigDecimal> askVolumeList = new LinkedList<>();
    @Getter
    private static BigDecimal dexBidPriceInZano;
    @Getter
    private static BigDecimal dexBidVolumeInTokens;
    @Getter
    private static BigDecimal dexBidVolumeInZano;
    @Getter
    private static boolean contrainedByTokensUtxos = false;

    // ##### Bid
    @Getter
    private static BigDecimal highestBid;
    @Getter
    private static BigDecimal weightedBid;
    @Getter
    private static BigDecimal lastWeightedBid;
    @Getter
    private static BigDecimal bidVolume;
    @Getter
    private static BigDecimal lastBidVolume = BigDecimal.ZERO;
    @Getter
    private static BigDecimal averageBidVolume = BigDecimal.ZERO;
    @Getter
    private static long lastBidOrderUpdate = 0;
    private static LinkedList<BigDecimal> bidVolumeList = new LinkedList<>();
    @Getter
    private static BigDecimal dexAskPriceInZano;
    @Getter
    private static BigDecimal dexAskVolumeInTokens;

    /**
     * Constructor
     */
    public ZanoPriceService() {
        updateAppSettings();

        coinexApiKey = null;
        coinexSecretKey = null;

        serviceStartup = System.currentTimeMillis();
    }

    /**
     * Setup the message handler used by Coinex. It needs to be a bespoke message handler
     * because each exchange returns data in their own bespoke format.
     * @return
     */
    private CoinexWebsocketClientEndpoint.MessageHandler setupCoinexMessageHandler() {

        if (coinexMessageHandler != null) {
            return coinexMessageHandler;
        }

        logger.info("Creating new CoinEx WS message handler");
        subscribedToCoinex = false;
        return new CoinexWebsocketClientEndpoint.MessageHandler() {
            public void handleMessage(String message) {

                JSONParser parser = new JSONParser();
                try {
                    JSONObject jsonMsg = (JSONObject) parser.parse(message);

                    if (jsonMsg.containsKey("id") && jsonMsg.get("id") != null && ((long)jsonMsg.get("id")) == 17) {
                        logger.info("Coinex msg: " + message);
                        if (!subscribedToCoinex) {
                            logger.info("Subscribing to Coinex Zano market");
                            logger.info(getMarketSub().toJSONString());
                            clientEndPointCoinex.sendMessage(getMarketSub().toJSONString());
                        }
                    } else if (jsonMsg.containsKey("id") && jsonMsg.get("id") != null && ((long)jsonMsg.get("id")) == 7) {
                        logger.info("Coinex msg: " + message);
                        String wsMsg = (String) jsonMsg.get("message");
                        if (wsMsg.equals("OK")) {
                            logger.info("Successfully subscribed to Coinex WS");
                            subscribedToCoinex = true;
                        } else {
                            logger.info("Not successfully subscribed to Coinex WS: " + wsMsg);
                        }

                    } else if (jsonMsg.containsKey("method") && ((String)jsonMsg.get("method")).equals("depth.update")) {
                        JSONObject data = (JSONObject) jsonMsg.get("data");
                        JSONObject depth = (JSONObject) data.get("depth");

                        JSONArray bids = (JSONArray) depth.get("bids");
                        JSONArray asks = (JSONArray) depth.get("asks");

                        // NEW LOGIC
                        // Add all orders to pe pushed to the order book aggregator
                        LinkedList<OrderBookOrder> ordersList = new LinkedList<>();

                        for (int i = 0; i < bids.size(); i++) {
                            JSONArray bidJson = (JSONArray) bids.get(i);
                            OrderBookOrder bid = new OrderBookOrder(
                                    new BigDecimal((String) bidJson.get(0)),
                                    new BigDecimal((String) bidJson.get(1)),
                                    OrderType.BID,
                                    Exchange.COINEX
                            );
                            ordersList.add(bid);
                        }
                        for (int i = 0; i < asks.size(); i++) {
                            JSONArray askJson = (JSONArray) asks.get(i);
                            OrderBookOrder ask = new OrderBookOrder(
                                    new BigDecimal((String) askJson.get(0)),
                                    new BigDecimal((String) askJson.get(1)),
                                    OrderType.ASK,
                                    Exchange.COINEX
                            );
                            ordersList.addLast(ask);
                        }

                        // Use the same method to handle the order batch for all exchanges
                        lastCoinex = new Timestamp(System.currentTimeMillis());
                        handleNewOrders(ordersList, Exchange.COINEX);

                    } else {
                        // logger.info("Coinex msg: " + message);
                    }

                } catch (ParseException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void handleDisconnect() {
                if (!isShuttingDown) {
                    restartCoinex = true;
                }
            }
        };
    }

    /**
     * Setup the message handler used by Mexc. It needs to be a bespoke message handler
     * because each exchange returns data in their own bespoke format.
     * @return
     */
    private MexcWebsocketClientEndpoint.MessageHandler setupMexcMessageHandler() {
        MexcWebsocketClientEndpoint.MessageHandler mexcMessageHandlerTmp = new MexcWebsocketClientEndpoint.MessageHandler() {

            @Override
            public void handleMessage(String message) {
                logger.info("Got Mexc msg: " + message);
                // {"id":0,"code":0,"msg":"spot@public.limit.depth.v3.api.pb@ZANOUSDT@20"}
                try {
                    JSONParser parser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) parser.parse(message);
                    if (((String)jsonObject.get("msg")).equals("spot@public.limit.depth.v3.api.pb@ZANOUSDT@20")) {
                        subscribedToMexc = true;
                        restartMexc = false;
                        logger.info("Setting subscribedToMexc to true");
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }

            @Override
            public void handleDisconnect() {
                logger.info("Mexc disconnect event");
                if (!isShuttingDown) {
                    restartMexc = true;
                }
            }

            @Override
            public void handleOrderBookUpdate(PublicLimitDepthsV3Api orderBook) {

                // Add all orders to pe pushed to the order book aggregator
                LinkedList<OrderBookOrder> ordersList = new LinkedList<>();

                for (PublicLimitDepthV3ApiItem item : orderBook.getBidsList()) {
                    OrderBookOrder bid = new OrderBookOrder(
                            new BigDecimal(item.getPrice()),
                            new BigDecimal(item.getQuantity()),
                            OrderType.BID,
                            Exchange.MEXC
                    );
                    ordersList.add(bid);
                }
                for (PublicLimitDepthV3ApiItem item : orderBook.getAsksList()) {
                    OrderBookOrder ask = new OrderBookOrder(
                            new BigDecimal(item.getPrice()),
                            new BigDecimal(item.getQuantity()),
                            OrderType.ASK,
                            Exchange.MEXC
                    );
                    ordersList.addLast(ask);
                }

                // Use the same method to handle the order batch for all exchanges
                lastMexc = new Timestamp(System.currentTimeMillis());
                handleNewOrders(ordersList, Exchange.MEXC);
            }
        };
        logger.info("Created new message handler.");
        return mexcMessageHandlerTmp;
    }

    /**
     * Open websocket to CoinEx and subscribe to the order book data
     * @throws Exception
     */
    private void openCoinExWebsocket() throws Exception {
        if (coinexApiKey == null || coinexSecretKey == null) {
            return;
        }
        logger.info("Opening coinex websocket. Subscribed: " + subscribedToCoinex);
        restartCoinex = false;
        clientEndPointCoinex = new CoinexWebsocketClientEndpoint(new URI("wss://socket.coinex.com/v2/spot"));
        clientEndPointCoinex.addMessageHandler(setupCoinexMessageHandler());

        // send message to coinex websocket
        JSONObject authObject = new JSONObject();
        JSONObject authParams = new JSONObject();

        long timestamp = System.currentTimeMillis();
        String prepString = String.valueOf(timestamp);

        authParams.put("access_id", coinexApiKey);
        authParams.put("signed_str", sign(coinexSecretKey, prepString));
        authParams.put("timestamp", timestamp);

        authObject.put("method", "server.sign");
        authObject.put("params", authParams);
        authObject.put("id", 17);

        for (int i=0; i<10; i++) {
            if (clientEndPointCoinex.isOpen()) {
                break;
            }
            logger.info("WS not open, waiting");
            Thread.sleep(1000);
        }
        clientEndPointCoinex.sendMessage(authObject.toJSONString());
    }

    /**
     * Open websocket to Mexc and subscribe to the order book data
     * @throws Exception
     */
    private void openMexcWebsocket() throws Exception {
        logger.info("Opening a new WS connection to Mexc");
        if (clientEndPointMexc != null && clientEndPointMexc.isOpen()) {
            logger.info("clientEndPointMexc is not null and clientEndPointMexc.isOpen()");
            return;
        }
        clientEndPointMexc = new MexcWebsocketClientEndpoint(new URI("wss://wbs-api.mexc.com/ws"));
        logger.info("Opened WS connection to Mexc, adding message handler.");
        clientEndPointMexc.addMessageHandler(setupMexcMessageHandler());

        JSONObject mexcSub = new JSONObject();
        mexcSub.put("method", "SUBSCRIPTION");
        JSONArray markets = new JSONArray();
        markets.add("spot@public.limit.depth.v3.api.pb@ZANOUSDT@20");
        mexcSub.put("params", markets);
        logger.info("Sending subscribe request to Mexc");
        clientEndPointMexc.sendMessage(mexcSub.toJSONString());
    }

    /**
     * Executed when the server is initiated (not created)
     */
    @Override
    public void init() {

        startTime = System.currentTimeMillis();

        try {
            openCoinExWebsocket();
            openMexcWebsocket();

            scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        runEvery30();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 30000, TimeUnit.MILLISECONDS);


        } catch (InterruptedException ex) {
            System.err.println("InterruptedException exception: " + ex.getMessage());
        } catch (URISyntaxException ex) {
            System.err.println("URISyntaxException exception: " + ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Executed when this service is shutting down.
     */
    @Override
    public void destroy() {
        isShuttingDown = true;
        try {
            clientEndPointCoinex.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Simple method executed every 30 seconds. Hard coded :(
     */
    private void runEvery30() {

        // Coinex handling
        if (subscribedToCoinex) {
            if (!restartCoinex) {
                if (clientEndPointCoinex != null && clientEndPointCoinex.isOpen()) {
                    JSONObject pingObject = new JSONObject();
                    pingObject.put("method", "server.ping");
                    pingObject.put("params", new JSONObject());
                    pingObject.put("id", 1337);

                    try {
                        // logger.info("Sending ping to CoinEx");
                        clientEndPointCoinex.sendMessage(pingObject.toJSONString());
                    } catch (IOException e) {
                        logger.error("Could not send ping to Coinex API");
                    }
                } else {
                    try {
                        openCoinExWebsocket();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                try {
                    logger.info("Trying to restart Coinex websocket");
                    openCoinExWebsocket();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (subscribedToMexc) {
            if (!restartMexc) {
                if (clientEndPointMexc != null && clientEndPointMexc.isOpen()) {
                    // Do nothing for now
                    // Add ping here
                } else {
                    try {
                        openMexcWebsocket();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

            } else {
                try {
                    openMexcWebsocket();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Signning a message for CoinEx, used to authenticating
     * @param secretKey SecretKeySpec for the HMAC-SHA256 key
     * @param preparedStr Input string
     * @return MAC signature as a lowercase hex string
     * @throws Exception
     */
    public static String sign(String secretKey, String preparedStr) throws Exception {
        // Create a SecretKeySpec for the HMAC-SHA256 key
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.ISO_8859_1), "HmacSHA256");

        // Get an HMAC-SHA256 Mac instance and initialize with the secret key
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);

        // Compute the HMAC on the input string
        byte[] rawHmac = mac.doFinal(preparedStr.getBytes(StandardCharsets.ISO_8859_1));

        // Convert the raw bytes to lowercase hex
        StringBuilder sb = new StringBuilder();
        for (byte b : rawHmac) {
            sb.append(String.format("%02x", b));
        }

        // Return the HMAC signature as a lowercase hex string
        return sb.toString().toLowerCase();
    }

    /**
     * Get the JSON object for subscribing to the market updates from Coinex.
     * Moved to it's own method to clean up the code. This part is ugly and is better to keep separate.
     * @return
     */
    private JSONObject getMarketSub() {
        JSONObject depthSub = new JSONObject();
        JSONObject depthSubParams = new JSONObject();
        JSONArray market = new JSONArray();
        market.add("ZANOUSDT");
        market.add(50);
        market.add("0.0000");
        market.add(true);

        JSONArray marketArray = new JSONArray();
        marketArray.add(market);
        depthSubParams.put("market_list", marketArray);

        depthSub.put("method", "depth.subscribe");
        depthSub.put("params", depthSubParams);
        depthSub.put("id", 7);

        return depthSub;
    }

    /**
     * Unzip bytebuffer received from the CoinEx websocket
     * @param input
     * @return
     * @throws DataFormatException
     */
    public static String unzip(ByteBuffer input) throws DataFormatException {

        try {
            GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(input.array()));
            InputStreamReader reader = new InputStreamReader(gzis);
            BufferedReader in = new BufferedReader(reader);

            String read;
            while ((read = in.readLine()) != null) {
                return read;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        throw new DataFormatException();
    }

    /**
     * Simple method for updating the app settings stored in the database
     */
    public static void updateAppSettings() {
        appSettings = DatabaseService.getAppSettingsFromDb();
        if (appSettings.containsKey("ask_multiplier")) {
            askMargin = new BigDecimal(appSettings.get("ask_multiplier"));
        }
        if (appSettings.containsKey("bid_multiplier")) {
            bidMargin = new BigDecimal(appSettings.get("bid_multiplier"));
        }
        if (appSettings.containsKey("minimum_token_volume_buy")) {
            minimumVolumeBuy = new BigDecimal(appSettings.get("minimum_token_volume_buy"));
        }
        if (appSettings.containsKey("maximum_token_volume_buy")) {
            maximumVolumeBuy = new BigDecimal(appSettings.get("maximum_token_volume_buy"));
        }
        if (appSettings.containsKey("minimum_token_volume_sell")) {
            minimumVolumeSell = new BigDecimal(appSettings.get("minimum_token_volume_sell"));
        }
        if (appSettings.containsKey("maximum_token_volume_sell")) {
            maximumVolumeSell = new BigDecimal(appSettings.get("maximum_token_volume_sell"));
        }
        if (appSettings.containsKey("price_movement_threshold")) {
            priceMovementThreshold = new BigDecimal(appSettings.get("price_movement_threshold"));
        }
    }

    /**
     * Handle a list of orders (both buy and sell) from the websockets of the exchanges.
     * When we get here, the order format is uniform regardless of the source exchange
     * @param orderList List<OrderBookOrder>
     * @param exchange Exchange enum
     */
    private void handleNewOrders(List<OrderBookOrder> orderList, Exchange exchange) {
        try {
            // Delete the orders from this exchange from our aggregated order book
            OrderBookAggregatorService.deleteOrders(exchange);

            // Send all orders to the aggregator matching engine
            for (OrderBookOrder order : orderList) {
                OrderBookAggregatorService.addOrderToQueue(order);
            }

            // Commit our changes to our aggregated order book to avoid special conditions where the bot fetches a partial order book
            OrderBookAggregatorService.commitSets();

            // Update all state variables called by other classes

            // Asks
            BigDecimal tempAskVolume = BigDecimal.ZERO;
            if (OrderBookAggregatorService.getBidsAggregated() != null && !OrderBookAggregatorService.getBidsAggregated().isEmpty()) {
                lowestAsk = OrderBookAggregatorService.getAsksAggregated().first().getPrice();
                weightedAsk = lowestAsk.multiply(askMargin);
                tempAskVolume = OrderBookAggregatorService.getPartialVolume(OrderType.ASK, weightedAsk);
            }

            // Bids
            BigDecimal tempBidVolume = BigDecimal.ZERO;
            if (OrderBookAggregatorService.getAsksAggregated() != null && !OrderBookAggregatorService.getAsksAggregated().isEmpty()) {
                highestBid = OrderBookAggregatorService.getBidsAggregated().first().getPrice();
                weightedBid = highestBid.multiply(bidMargin);
                tempBidVolume = OrderBookAggregatorService.getPartialVolume(OrderType.BID, weightedBid);
            }

            // Run the logic if we should update the current orders or not

            // ===== Ask order logic =====
            boolean placeAskOrder = false;

            // Ask price
            if (lastWeightedAsk == null) {
                placeAskOrder = true;

            } else {
                // Compare last used weighted ask to the new latest ask
                BigDecimal change = weightedAsk.divide(lastWeightedAsk, 4, RoundingMode.UP).movePointRight(2).subtract(BigDecimal.valueOf(100)).abs();

                // If the price moved more than priceMovementThreshold, update the price
                if (change.compareTo(priceMovementThreshold) > 0) {
                    logger.info("[NEW LOGIC] New ask price is " + change + "% different. new " + weightedAsk + " vs old " + lastWeightedAsk);
                    placeAskOrder = true;
                }
            }
            // Check average ask volume
            LinkedList<BigDecimal> askVolumeListTemp = new LinkedList<>();
            if (tempAskVolume.compareTo(BigDecimal.ZERO) != 0) {
                askVolumeListTemp.addFirst(tempAskVolume);
                askVolumeListTemp.addAll(askVolumeList);

                if (askVolumeListTemp.size() > 32) {
                    askVolumeListTemp.removeLast();
                }
            }

            if (!askVolumeListTemp.isEmpty()) {
                BigDecimal allVolumes = BigDecimal.ZERO;
                for (BigDecimal vol : askVolumeListTemp) {
                    allVolumes = allVolumes.add(vol);
                }
                averageAskVolume = allVolumes.divide(BigDecimal.valueOf(askVolumeListTemp.size()), 12, RoundingMode.DOWN);
                askVolume = tempAskVolume; // This is always the latest value
                askVolumeList = askVolumeListTemp;
            }
            if (averageAskVolume.compareTo(lastAskVolume.multiply(BigDecimal.valueOf(0.3))) < 0) {
                logger.info("[NEW LOGIC] New bid volume has gone down too much. " + averageAskVolume + " vs " + lastAskVolume + ", placing new order.");
                placeAskOrder = true;
            }

            if (placeAskOrder) {
                sendBidOrderToTradeService(false);
            }

            // ===== Bid order logic =====
            boolean placeBidOrder = false;

            // Check bid price
            if (lastWeightedBid == null) {
                placeBidOrder = true;
            } else {
                // Compare last used weighted bid to the new latest bid
                BigDecimal change = weightedBid.divide(lastWeightedBid, 4, RoundingMode.UP).movePointRight(2).subtract(BigDecimal.valueOf(100)).abs();

                // If the price moved more than priceMovementThreshold, update the price
                if (change.compareTo(priceMovementThreshold) > 0) {
                    logger.info("[NEW LOGIC] New bid price is " + change + "% different. " + lastWeightedBid + " vs " + weightedBid);
                    placeBidOrder = true;
                }
            }
            // Check average bid volume
            LinkedList<BigDecimal> bidVolumeListTemp = new LinkedList<>();
            if (tempBidVolume.compareTo(BigDecimal.ZERO) != 0) {
                bidVolumeListTemp.addFirst(tempBidVolume);
                bidVolumeListTemp.addAll(bidVolumeList);
                if (bidVolumeListTemp.size() > 32) {
                    bidVolumeListTemp.removeLast();
                }
            }

            if (!bidVolumeListTemp.isEmpty()) {
                BigDecimal allVolumes = BigDecimal.ZERO;
                for (BigDecimal vol : bidVolumeListTemp) {
                    allVolumes = allVolumes.add(vol);
                }
                averageBidVolume = allVolumes.divide(BigDecimal.valueOf(bidVolumeListTemp.size()), 12, RoundingMode.DOWN);
                bidVolume = tempBidVolume;
                bidVolumeList = bidVolumeListTemp;
            }
            if (averageBidVolume.compareTo(lastBidVolume.multiply(BigDecimal.valueOf(0.3))) < 0) {
                logger.info("[NEW LOGIC] New bid volume has gone down too much. " + averageBidVolume + " vs " + lastBidVolume + ", placing new order.");
                placeBidOrder = true;
            }

            if (placeBidOrder) {
                sendAskOrderToTradeService(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Send a ask order to the DEX for listing a sell order.
     * @param override If we should ignore last placed order or not
     */
    public static void sendAskOrderToTradeService(boolean override) {
        if (blockNewOrders) {
            logger.info("Won't place new ask order because of block in place.");
            return;
        }

        if (((System.currentTimeMillis()-serviceStartup) > 10000 && (System.currentTimeMillis()-lastBidOrderUpdate) > 30000) || override) {
            logger.info("[NEW LOGIC] Placing new DEX ask order @" + weightedBid + " with volume " + averageBidVolume.multiply(BigDecimal.valueOf((0.9))));

            // Here, we are SELLING tokens to the DEX ussers. We are placing an ask order on the DEX
            // Users send Zano, we send tokens

            // Bid and ask gets reversed when it's on the DEX
            dexAskPriceInZano = BigDecimal.ONE.divide(weightedBid, 12, RoundingMode.HALF_UP);
            dexAskVolumeInTokens = getAvailableBidVolumeForDexBidOrder();

            logger.info("[NEW LOGIC] DEX ask order with price @" + dexAskPriceInZano + " with " + dexAskVolumeInTokens + " tokens");
            if (dexAskVolumeInTokens.compareTo(BigDecimal.ZERO) != 0) {
                try {
                    ZanoTradeService.placeAskOrder(dexAskPriceInZano, dexAskVolumeInTokens, weightedBid);
                    lastWeightedBid = weightedBid.add(BigDecimal.ZERO);
                    lastBidVolume = averageBidVolume.multiply(BigDecimal.valueOf(0.9));
                    lastBidOrderUpdate = System.currentTimeMillis();
                } catch (IOException e) {
                    logger.error("Unable to place ask order: "  + e.getMessage());
                }
            } else {
                logger.error("Can't place an order will zero volume. Ignoring.");
            }

        } else {
            if ((System.currentTimeMillis()-serviceStartup) <= 10000) {
                logger.info("[NEW LOGIC] Not placing new bid order since service was started " + (System.currentTimeMillis()-serviceStartup)/1000 + " seconds ago");
            } else {
                logger.info("[NEW LOGIC] Not placing new bid order since a new order was placed " + (System.currentTimeMillis()-lastBidOrderUpdate)/1000 + " seconds ago");
            }
        }
    }

    /**
     * Get the available volume when placing a DEX bid (buy) order.
     * The volume is based on the aggregated ask (sell) order book from all exchanges.
     * @return Volume amount to use for the DEX bid order
     */
    private static BigDecimal getAvailableBidVolumeForDexBidOrder() {
        // Before we can place an order, we need to check available tokens in the walllet
        BigDecimal availableTokensInWallet = BigDecimal.ZERO;
        if (ZanoWalletService.getFloatTokensDataMain() != null) {
            availableTokensInWallet = new BigDecimal(ZanoWalletService.getFloatTokensDataMain().getUnlocked()).movePointLeft(ZanoWalletService.getFloatTokensDataMain().getAsset_info().getDecimal_point());
        }
        BigDecimal availableZanoInWallet = BigDecimal.ZERO;
        if (ZanoWalletService.getZanoDataMain() != null) {
            availableZanoInWallet = new BigDecimal(ZanoWalletService.getZanoDataMain().getUnlocked()).movePointLeft(ZanoWalletService.getZanoDataMain().getAsset_info().getDecimal_point());
        }

        logger.info("Available unlocked tokens: " + availableTokensInWallet);
        logger.info("Available unlocked ZANO: " + availableZanoInWallet);

        // We can't buy more tokens than we have ZANO
        // availableTokensInWallet = availableZanoInWallet.multiply(weightedBid);

        // We are buying tokens, make sure we have enough ZANO
        BigDecimal thisOrdersVolume = averageBidVolume.multiply(weightedAsk);
        logger.info("Average bid volume (in ZANO): " + averageBidVolume.toPlainString());
        logger.info("Want to offer token volume: " + thisOrdersVolume.toPlainString());

        // If the amount of tokens is less than the available volume, then we cap the volume to the available tokens
        if (thisOrdersVolume.compareTo(availableTokensInWallet) > 0) {
            logger.info("New ask order hit by unlocked tokens amount constraint in wallet. Original volume " + thisOrdersVolume + " tokens changed to " + availableTokensInWallet);
            thisOrdersVolume = availableTokensInWallet;
            contrainedByTokensUtxos = true;
        } else if (thisOrdersVolume.compareTo(maximumVolumeSell) >= 0) {
            // Cap by settings as well
            logger.info("New ask order hit by maximum tokens to sell constraint in settings. Original volume " + thisOrdersVolume + " changed to " + maximumVolumeSell);
            thisOrdersVolume = maximumVolumeSell;
        } else if (thisOrdersVolume.compareTo(minimumVolumeSell) <= 0) {
            // Cap by settings as well
            if (availableTokensInWallet.compareTo(minimumVolumeSell) < 0) {
                logger.info("New ask order hit by minimum tokens to sell constraint in settings and available tokens. Original volume " + thisOrdersVolume + " changed to " + availableTokensInWallet);
                thisOrdersVolume = availableTokensInWallet;
            } else {
                logger.info("New ask order hit by minimum tokens to sell constraint in settings. Original volume " + thisOrdersVolume + " changed to " + minimumVolumeSell);
                thisOrdersVolume = minimumVolumeSell;
            }
        }

        return thisOrdersVolume;
    }

    public static void sendBidOrderToTradeService(boolean override) {
        if (blockNewOrders) {
            logger.info("Won't place new bid order because of block in place.");
            return;
        }

        if (((System.currentTimeMillis()-serviceStartup) > 10000 && (System.currentTimeMillis()-lastAskOrderUpdate) > 30000) || override) {
            logger.info("[NEW LOGIC] Placing DEX bid ask order @" + weightedAsk + " with volume " + averageAskVolume.multiply(BigDecimal.valueOf(0.9)));

            // Here, we are BUYING tokens back from the DEX users. We are placing a bid order on the DEX
            // Users send tokens, we send Zano.

            // Bid and ask gets reversed when it's on the DEX
            dexBidPriceInZano = BigDecimal.ONE.divide(weightedAsk, 12, RoundingMode.HALF_UP);
            dexBidVolumeInTokens = getAvailableAskVolumeForDexAskOrder();

            logger.info("[NEW LOGIC] DEX bid order with price @" + dexBidPriceInZano + " with volume " + dexBidVolumeInTokens);
            try {
                ZanoTradeService.placeBidOrder(dexBidPriceInZano, dexBidVolumeInTokens, weightedAsk);
                lastWeightedAsk = weightedAsk.add(BigDecimal.ZERO);
                lastAskVolume = averageAskVolume.multiply(BigDecimal.valueOf(0.9));
                lastAskOrderUpdate = System.currentTimeMillis();
            } catch (IOException e) {
                logger.error("Unable to place bid order: "  + e.getMessage());
            }
        } else {
            if ((System.currentTimeMillis()-serviceStartup) <= 10000) {
                logger.info("[NEW LOGIC] Not placing new ask order since service was started " + (System.currentTimeMillis()-serviceStartup)/1000 + " seconds ago");
            } else {
                logger.info("[NEW LOGIC] Not placing new ask order since a new order was placed " + (System.currentTimeMillis()-lastAskOrderUpdate)/1000 + " seconds ago");
            }
        }
    }

    /**
     * Get the available volume when placing a DEX ask (sell) order.
     * The volume is based on the aggregated bid (buy) order book from all exchanges.
     * @return Volume amount to use for the DEX ask order
     */
    private static BigDecimal getAvailableAskVolumeForDexAskOrder() {
        // Check available Zano
        BigDecimal availableZanoInWallet = BigDecimal.ZERO;
        if (ZanoWalletService.getZanoDataMain() != null) {
            availableZanoInWallet = new BigDecimal(ZanoWalletService.getZanoDataMain().getUnlocked()).movePointLeft(ZanoWalletService.getZanoDataMain().getAsset_info().getDecimal_point());
        }

        BigDecimal thisOrdersVolume = averageAskVolume;
        logger.info("getAvailableAskVolumeForDexAskOrder: thisOrdersVolume = " + thisOrdersVolume);
        BigDecimal thisOrdersTokenVolume = averageAskVolume.multiply(weightedAsk).setScale(0, RoundingMode.DOWN);
        logger.info("getAvailableAskVolumeForDexAskOrder: thisOrdersTokenVolume = " + thisOrdersTokenVolume);

        BigDecimal possibleTokensToBuy = availableZanoInWallet.multiply(weightedAsk).setScale(0, RoundingMode.DOWN);
        // If the amount of zano is less than the available volume, then we cap the volume to the available zano
        if (thisOrdersVolume.compareTo(availableZanoInWallet) > 0) {
            logger.info("New bid order hit by unlocked ZANO amount constraint in wallet. Original volume " + thisOrdersVolume + " ZANO changed to " + availableZanoInWallet + " ZANO");
            dexBidVolumeInZano = availableZanoInWallet;
            thisOrdersVolume = thisOrdersTokenVolume;
        }

        if (thisOrdersTokenVolume.compareTo(maximumVolumeBuy) >= 0) {
            // Cap by settings as well
            logger.info("New bid order hit by maximum token buy amount constraint in settings. Original volume "
                    + thisOrdersVolume + " changed to " + maximumVolumeBuy);
            thisOrdersVolume = maximumVolumeBuy;
            thisOrdersTokenVolume = maximumVolumeBuy;
            dexBidVolumeInZano = maximumVolumeBuy.divide(weightedAsk, 12, RoundingMode.DOWN);
        } else if (thisOrdersTokenVolume.compareTo(minimumVolumeBuy) <= 0) {
            // Cap by settings as well
            logger.info("New bid order hit by minimum ZANO to buy constraint in settings. Original volume " + thisOrdersVolume + " changed to " + minimumVolumeBuy);
            thisOrdersVolume = minimumVolumeBuy;
            thisOrdersTokenVolume = minimumVolumeBuy;
            dexBidVolumeInZano = minimumVolumeBuy.divide(weightedAsk, 12, RoundingMode.DOWN);

            if (thisOrdersVolume.compareTo(availableZanoInWallet) > 0) {
                thisOrdersVolume = availableZanoInWallet;
            }
        }
        if (thisOrdersTokenVolume.compareTo(possibleTokensToBuy) > 0) {
            thisOrdersTokenVolume = possibleTokensToBuy;
        }
        return thisOrdersTokenVolume;
    }
}
