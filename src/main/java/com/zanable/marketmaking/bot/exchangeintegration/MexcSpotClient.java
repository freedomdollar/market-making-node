package com.zanable.marketmaking.bot.exchangeintegration;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.Mexc.AccountAssetResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal MEXC Spot REST client covering common trading actions.
 *
 * Base endpoint and signing rules (HMAC SHA256 over query+body, lowercase hex; send X-MEXC-APIKEY; include timestamp/recvWindow)
 * are from "General Info" (Spot v3). :contentReference[oaicite:0]{index=0}
 */
public class MexcSpotClient {

    public enum Side { BUY, SELL }
    public enum Type { LIMIT, MARKET }

    private static final String BASE_URL = "https://api.mexc.com";
    private static final String API_KEY_HEADER = "X-MEXC-APIKEY";

    private final HttpClient http = HttpClient.newHttpClient();
    private final String apiKey;
    private final String secretKey;
    private final long defaultRecvWindowMs;
    private Gson gson = new Gson();

    public MexcSpotClient(String apiKey, String secretKey) {
        this(apiKey, secretKey, 20000);
    }

    public MexcSpotClient(String apiKey, String secretKey, long defaultRecvWindowMs) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.defaultRecvWindowMs = defaultRecvWindowMs;
    }

    /* -----------------------------------------------------------
     * Helpers
     * ----------------------------------------------------------- */

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String hmacSha256Hex(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b)); // lowercase hex (required)
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign request", e);
        }
    }

    private static String qs(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String signedQuery(Map<String, String> params, String bodyIfAny) {
        String query = qs(params);
        String totalParams = bodyIfAny == null || bodyIfAny.isEmpty() ? query : (query + bodyIfAny); // concatenate (no extra '&') per docs. :contentReference[oaicite:1]{index=1}
        String sig = hmacSha256Hex(totalParams);
        return query + "&signature=" + sig;
    }

    private HttpRequest.Builder baseReqBuilder(String path) {
        return HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header(API_KEY_HEADER, apiKey)
                .header("Content-Type", "application/json");
    }

    private long now() {
        System.out.println("Time: " + System.currentTimeMillis());
        System.out.println("Timestamp: " + new Timestamp(System.currentTimeMillis()).toString());
        return System.currentTimeMillis();
        // return Instant.now().toEpochMilli();
    }

    /* -----------------------------------------------------------
     * 1) Place single order  (POST /api/v3/order)  :contentReference[oaicite:2]{index=2}
     *    LIMIT requires quantity + price; MARKET requires quantity or quoteOrderQty.
     * ----------------------------------------------------------- */
    public String placeOrder(
            String symbol,
            Side side,
            Type type,
            String quantity,        // pass null if you prefer quoteOrderQty for MARKET
            String quoteOrderQty,   // pass null when using quantity
            String price,           // required for LIMIT
            String newClientOrderId // optional
    ) throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("symbol", symbol);
        q.put("side", side.name());
        q.put("type", type.name());
        if (quantity != null) q.put("quantity", quantity);
        if (quoteOrderQty != null) q.put("quoteOrderQty", quoteOrderQty);
        if (price != null) q.put("price", price);
        if (newClientOrderId != null) q.put("newClientOrderId", newClientOrderId);
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));

        String signed = signedQuery(q, "");
        HttpRequest req = baseReqBuilder("/api/v3/order?" + signed)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /* -----------------------------------------------------------
     * 2) Place batch order (POST /api/v3/batchOrders)  Supports up to 20 orders. :contentReference[oaicite:3]{index=3}
     *    Each element follows the same rules as single order (LIMIT: quantity+price; MARKET: quantity or quoteOrderQty).
     * ----------------------------------------------------------- */
    public static class BatchOrder {
        public String symbol;     // required
        public Side side;         // required
        public Type type;         // required
        public String quantity;   // LIMIT or MARKET (one of quantity/quoteOrderQty for MARKET)
        public String quoteOrderQty;
        public String price;      // required for LIMIT
        public String newClientOrderId;
    }

    public String placeBatchOrders(List<BatchOrder> orders) throws Exception {
        // API expects: query params include timestamp/recvWindow; body contains batchOrders as JSON string. :contentReference[oaicite:4]{index=4}
        Map<String, String> q = new LinkedHashMap<>();
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));

        // Build the JSON array required by "batchOrders"
        StringBuilder jsonArr = new StringBuilder("[");
        for (int i = 0; i < orders.size(); i++) {
            BatchOrder o = orders.get(i);
            String obj = String.format(Locale.US,
                    "{\"symbol\":\"%s\",\"side\":\"%s\",\"type\":\"%s\"%s%s%s%s}",
                    o.symbol,
                    o.side.name(),
                    o.type.name(),
                    o.quantity != null ? ",\"quantity\":\"" + o.quantity + "\"" : "",
                    o.quoteOrderQty != null ? ",\"quoteOrderQty\":\"" + o.quoteOrderQty + "\"" : "",
                    o.price != null ? ",\"price\":\"" + o.price + "\"" : "",
                    o.newClientOrderId != null ? ",\"newClientOrderId\":\"" + o.newClientOrderId + "\"" : ""
            );
            if (i > 0) jsonArr.append(',');
            jsonArr.append(obj);
        }
        jsonArr.append(']');

        // Per signing rules, signature is computed over query + body (concatenated, no extra '&'). :contentReference[oaicite:5]{index=5}
        String body = "{\"batchOrders\":" + jsonArr + "}";
        String signed = signedQuery(q, body);

        HttpRequest req = baseReqBuilder("/api/v3/batchOrders?" + signed)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /* -----------------------------------------------------------
     * 3) Cancel single order (DELETE /api/v3/order)  Either orderId or origClientOrderId is required. :contentReference[oaicite:6]{index=6}
     * ----------------------------------------------------------- */
    public String cancelOrder(String symbol, String orderId, String origClientOrderId, String newClientOrderId) throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("symbol", symbol);
        if (orderId != null) q.put("orderId", orderId);
        if (origClientOrderId != null) q.put("origClientOrderId", origClientOrderId);
        if (newClientOrderId != null) q.put("newClientOrderId", newClientOrderId);
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));

        String signed = signedQuery(q, "");
        HttpRequest req = baseReqBuilder("/api/v3/order?" + signed)
                .method("DELETE", HttpRequest.BodyPublishers.noBody())
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /* -----------------------------------------------------------
     * 4) Cancel batch order(s) for symbol(s)
     *    DELETE /api/v3/openOrders  (max 5 symbols comma-separated per docs). :contentReference[oaicite:7]{index=7}
     * ----------------------------------------------------------- */
    public String cancelAllOpenOrders(String symbolsCsv) throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("symbol", symbolsCsv); // API doc shows “maximum input 5 symbols,separated by ',' e.g. BTCUSDT,MXUSDT” :contentReference[oaicite:8]{index=8}
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));
        String signed = signedQuery(q, "");

        HttpRequest req = baseReqBuilder("/api/v3/openOrders?" + signed)
                .method("DELETE", HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /* -----------------------------------------------------------
     * 5) Update order (if possible)
     *    MEXC Spot v3 docs provided do NOT include an "amend/replace order" endpoint.
     *    This helper implements a common cancel-and-replace flow.
     *    (Cancel: DELETE /api/v3/order; Place: POST /api/v3/order). :contentReference[oaicite:9]{index=9}
     * ----------------------------------------------------------- */
    public String updateOrderByCancelReplace(
            String symbol,
            String orderIdOrOrigClientOrderId,     // send either ID or origClientOrderId
            boolean isClientOrderId,
            String newPrice,
            String newQuantity,
            String newQuoteOrderQty,
            String newClientOrderId
    ) throws Exception {
        String cancelJson = cancelOrder(
                symbol,
                isClientOrderId ? null : orderIdOrOrigClientOrderId,
                isClientOrderId ? orderIdOrOrigClientOrderId : null,
                null
        );
        // Place the new order with updated fields. Caller must pass appropriate Type/Side; here we assume LIMIT if price provided; otherwise MARKET.
        Type type = (newPrice != null) ? Type.LIMIT : Type.MARKET;
        return placeOrder(symbol, Side.BUY, type, newQuantity, newQuoteOrderQty, newPrice, newClientOrderId);
    }

    /* -----------------------------------------------------------
     * 6) Update batch order (if possible)
     *    Not natively supported in provided Spot v3 docs; this is a convenience that
     *    cancels all open orders on a symbol, then re-places a batch. Use carefully. :contentReference[oaicite:10]{index=10}
     * ----------------------------------------------------------- */
    public String updateBatchByCancelAllThenPlace(String symbol, List<BatchOrder> newOrders) throws Exception {
        cancelAllOpenOrders(symbol);
        return placeBatchOrders(newOrders);
    }

    /* -----------------------------------------------------------
     * 7) Get 24 hours trading volume (public market data)
     *    NOTE: The provided PDFs cover account/trade and general info; the public 24h ticker endpoint
     *    (commonly GET /api/v3/ticker/24hr?symbol=...) is not listed there. Please verify in the MEXC Market Data docs.
     *    This method calls that conventional endpoint; adjust field parsing per the official market spec.
     * ----------------------------------------------------------- */
    public String get24hTicker(String symbol) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE_URL + "/api/v3/ticker/24hr?symbol=" + enc(symbol)))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /* -----------------------------------------------------------
     * 8) Get account balance(s): GET /api/v3/account (balances array with asset/free/locked). :contentReference[oaicite:11]{index=11}
     * ----------------------------------------------------------- */
    public String getAccountInfoRaw() throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));
        String signed = signedQuery(q, "");

        HttpRequest req = baseReqBuilder("/api/v3/account?" + signed)
                .GET()
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    public String getListenKey() throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));
        String signed = signedQuery(q, "");

        HttpRequest req = baseReqBuilder("/api/v3/userDataStream?" + signed).POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /**
     * Convenience: returns balances JSON filtered to a single asset client-side (you can parse the raw yourself).
     */
    public AccountAssetResponse getBalanceForAsset() throws Exception {
        String json = getAccountInfoRaw(); // contains "balances":[{"asset":"XXX","free":"...","locked":"..."}] per docs. :contentReference[oaicite:12]{index=12}
        // Leave JSON parsing to caller in real code; trimmed here to avoid adding deps. Example placeholder below:
        // In production, use Jackson/Gson to parse and filter.
        System.out.println(json);
        return gson.fromJson(json, AccountAssetResponse.class);
    }

    /* -----------------------------------------------------------
     * Query single order (GET /api/v3/order)
     * Requires: symbol AND (orderId OR origClientOrderId)
     * ----------------------------------------------------------- */
    public String queryOrder(String symbol, String orderId, String origClientOrderId) throws Exception {
        if ((orderId == null || orderId.isBlank()) && (origClientOrderId == null || origClientOrderId.isBlank())) {
            throw new IllegalArgumentException("Provide either orderId or origClientOrderId");
        }

        Map<String, String> q = new LinkedHashMap<>();
        q.put("symbol", symbol);
        if (orderId != null) q.put("orderId", orderId);
        if (origClientOrderId != null) q.put("origClientOrderId", origClientOrderId);
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));

        String signed = signedQuery(q, "");
        HttpRequest req = baseReqBuilder("/api/v3/order?" + signed)
                .GET()
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /* -----------------------------------------------------------
     * Withdraw funds (POST /api/v3/capital/withdraw)
     * Required: coin, address, amount
     * Optional: netWork (exact key spelling), memo, contractAddress, withdrawOrderId, remark
     * Docs: Wallet Endpoints – "Withdraw(new)". Permission: SPOT_WITHDRAW_WRITE.
     * ----------------------------------------------------------- */
    public String withdraw(
            String coin,
            String address,
            String amount,
            String netWork,           // e.g. "TRC20", "ERC20", "BEP20(BSC)", or chain name per /capital/config/getall
            String memo,              // tag/memo/destination tag if the network requires it
            String contractAddress,   // for EVM-style networks when needed
            String withdrawOrderId,   // optional client-supplied id
            String remark             // optional note
    ) throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("coin", coin);
        q.put("address", address);
        q.put("amount", amount);
        if (netWork != null) q.put("netWork", netWork);              // note: "netWork" (new param key)
        if (memo != null) q.put("memo", memo);
        if (contractAddress != null) q.put("contractAddress", contractAddress);
        if (withdrawOrderId != null) q.put("withdrawOrderId", withdrawOrderId);
        if (remark != null) q.put("remark", remark);

        // standard signed params
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));     // optional but supported
        q.put("timestamp", String.valueOf(now()));

        String signed = signedQuery(q, "");
        HttpRequest req = baseReqBuilder("/api/v3/capital/withdraw?" + signed)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /* -----------------------------------------------------------
     * Get all coins information (with networkList for each coin)
     * Endpoint: GET /api/v3/capital/config/getall
     * Permission: SPOT_WITHDRAW_READ
     * ----------------------------------------------------------- */
    public String getAllCoinConfigs() throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));

        String signed = signedQuery(q, "");
        HttpRequest req = baseReqBuilder("/api/v3/capital/config/getall?" + signed)
                .GET()
                .build();

        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    public String getDepositAddressesRaw(String coin, String network) throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("coin", coin);
        if (network != null) q.put("network", network);
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));
        String signed = signedQuery(q, "");

        HttpRequest req = baseReqBuilder("/api/v3/capital/deposit/address?" + signed)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** Typed convenience wrapper for GET deposit address(es). */
    public List<DepositAddress> getDepositAddresses(String coin, String network) throws Exception {
        String json = getDepositAddressesRaw(coin, network);
        DepositAddress[] arr = gson.fromJson(json, DepositAddress[].class);
        return arr == null ? java.util.List.of() : java.util.Arrays.asList(arr);
    }

    /** POST /api/v3/capital/deposit/address (generate if none exists for coin+network)
     *  Permission: SPOT_WITHDRAW_WRITE
     *  Docs: page 5 (Wallet Endpoints).
     */
    public String generateDepositAddressRaw(String coin, String network) throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("coin", coin);
        q.put("network", network); // mandatory for POST (deposit generation)
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));
        String signed = signedQuery(q, "");

        HttpRequest req = baseReqBuilder("/api/v3/capital/deposit/address?" + signed)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** Typed convenience for POST deposit address. Returns the entry for the requested network if present. */
    public DepositAddress generateDepositAddress(String coin, String network) throws Exception {
        String json = generateDepositAddressRaw(coin, network);
        DepositAddress[] arr = gson.fromJson(json, DepositAddress[].class);
        if (arr == null || arr.length == 0) return null;
        for (DepositAddress da : arr) {
            if (network.equalsIgnoreCase(da.network)) return da;
        }
        // Fallback: return the first if API didn’t echo a strict per-network record
        return arr[0];
    }

    /** Get deposit address for the given coin+network; if missing, auto-create it.
     *  Requires network because POST generation needs it.
     *  GET shape: page 6; POST generation: page 5.
     */
    public DepositAddress getOrCreateDepositAddress(String coin, String network) throws Exception {
        if (network == null || network.isBlank()) {
            throw new IllegalArgumentException("network is required to create a new deposit address");
        }
        List<DepositAddress> existing = getDepositAddresses(coin, network);
        for (DepositAddress da : existing) {
            if (network.equalsIgnoreCase(da.network) && da.address != null && !da.address.isBlank()) {
                return da;
            }
        }
        // Not found – generate
        DepositAddress created = generateDepositAddress(coin, network);
        if (created == null || created.address == null || created.address.isBlank()) {
            throw new IllegalStateException("Failed to generate deposit address for " + coin + " on " + network);
        }
        return created;
    }

    /** GET /api/v3/capital/deposit/hisrec
     *  Optional: coin, status, startTime, endTime, limit
     *  Notes: defaults to last 7 days; up to 90 days; see status codes in DTO.
     *  Docs: pages 3–4 (Wallet Endpoints).
     */
    public String getDepositHistoryRaw(String coin, String status, Long startTimeMs, Long endTimeMs, Integer limit) throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        if (coin != null) q.put("coin", coin);
        if (status != null) q.put("status", status);
        if (startTimeMs != null) q.put("startTime", String.valueOf(startTimeMs));
        if (endTimeMs != null) q.put("endTime", String.valueOf(endTimeMs));
        if (limit != null) q.put("limit", String.valueOf(limit));
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));
        String signed = signedQuery(q, "");

        HttpRequest req = baseReqBuilder("/api/v3/capital/deposit/hisrec?" + signed)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** Typed convenience for deposit history. */
    public List<DepositHistoryItem> getDepositHistory(String coin, String status, Long startTimeMs, Long endTimeMs, Integer limit) throws Exception {
        String json = getDepositHistoryRaw(coin, status, startTimeMs, endTimeMs, limit);
        DepositHistoryItem[] arr = gson.fromJson(json, DepositHistoryItem[].class);
        return arr == null ? java.util.List.of() : java.util.Arrays.asList(arr);
    }

    /** GET /api/v3/capital/withdraw/history
     *  Optional: coin, status, startTime, endTime, limit
     *  Notes: defaults to last 7 days; up to 90 days; network field may be absent on some rows.
     *  Docs: pages 4–5 (Wallet Endpoints).
     */
    public String getWithdrawHistoryRaw(String coin, String status, Long startTimeMs, Long endTimeMs, Integer limit) throws Exception {
        Map<String, String> q = new LinkedHashMap<>();
        if (coin != null) q.put("coin", coin);
        if (status != null) q.put("status", status);
        if (limit != null) q.put("limit", String.valueOf(limit));
        if (startTimeMs != null) q.put("startTime", String.valueOf(startTimeMs));
        if (endTimeMs != null) q.put("endTime", String.valueOf(endTimeMs));
        q.put("recvWindow", String.valueOf(defaultRecvWindowMs));
        q.put("timestamp", String.valueOf(now()));
        String signed = signedQuery(q, "");

        HttpRequest req = baseReqBuilder("/api/v3/capital/withdraw/history?" + signed)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** Typed convenience for withdrawal history. */
    public List<WithdrawHistoryItem> getWithdrawHistory(String coin, String status, Long startTimeMs, Long endTimeMs, Integer limit) throws Exception {
        String json = getWithdrawHistoryRaw(coin, status, startTimeMs, endTimeMs, limit);
        WithdrawHistoryItem[] arr = gson.fromJson(json, WithdrawHistoryItem[].class);
        return arr == null ? java.util.List.of() : java.util.Arrays.asList(arr);
    }

    /* -----------------------------------------------------------
     * Utilities you might want:
     *  - Query single order: GET /api/v3/order (status/filled qty etc.). :contentReference[oaicite:13]{index=13}
     *  - List open orders: GET /api/v3/openOrders. :contentReference[oaicite:14]{index=14}
     *  - List all orders (up to 7 days): GET /api/v3/allOrders. :contentReference[oaicite:15]{index=15}
     *  Implement similarly to getAccountInfoRaw() using signedQuery().
     * ----------------------------------------------------------- */

    // ---- Wallet DTOs ----
    public static class DepositAddress {
        public String coin;
        public String network;  // deposit network (e.g., TRC20, ERC20, BEP20(BSC), EOS)
        public String address;
        public String memo;     // aka tag / destination tag (if required by network)
        public String tag;      // some responses use "tag" instead of "memo"
    }

    public static class DepositHistoryItem {
        public String amount;
        public String coin;
        public String network;       // may be absent for some legacy entries
        public Integer status;       // 1:SMALL,2:TIME_DELAY,3:LARGE_DELAY,4:PENDING,5:SUCCESS,6:AUDITING,7:REJECTED,8:REFUND,9:PRE_SUCCESS,10:INVALID,11:RESTRICTED,12:COMPLETED
        public String address;
        public String txId;
        public Long insertTime;
        public String unlockConfirm;
        public String confirmTimes;
        public String memo;
    }

    public static class WithdrawHistoryItem {
        public String id;
        public String txId;
        public String coin;
        public String network;       // “Supported multiple network coins’ withdraw history may not return the 'network' field”
        public String address;
        public String amount;
        public Integer transferType; // 0: outside, 1: inside transfer
        public Integer status;       // 1:APPLY,2:AUDITING,3:WAIT,4:PROCESSING,5:WAIT_PACKAGING,6:WAIT_CONFIRM,7:SUCCESS,8:FAILED,9:CANCEL,10:MANUAL
        public String transactionFee;
        public String confirmNo;
        public Long applyTime;
        public String remark;
        public String memo;
        public String transHash;
        public Long updateTime;
        public String coinId;
        public String vcoinId;
        public String withdrawOrderId;
    }
}
