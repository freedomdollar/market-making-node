package com.zanable.marketmaking.bot.services;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zanable.marketmaking.bot.beans.ZanoWalletMeta;
import com.zanable.marketmaking.bot.beans.zano.*;
import com.zanable.marketmaking.bot.beans.zano.trade.ZanoTradeAuthRequest;
import com.zanable.marketmaking.bot.beans.zano.trade.ZanoTradeAuthRequestData;
import com.zanable.marketmaking.bot.beans.zano.trade.ZanoTradeAuthResponse;
import com.zanable.marketmaking.bot.exceptions.ZanoWalletException;
import com.zanable.shared.beans.NewAddressResponse;
import com.zanable.shared.beans.SendResponse;
import com.zanable.shared.exceptions.AssetNotFoundException;
import com.zanable.shared.exceptions.NoApiResponseException;
import com.zanable.shared.exceptions.NotEnoughMoneyException;
import com.zanable.shared.interfaces.ApplicationService;
import com.zanable.shared.tools.PostHandler;
import lombok.Getter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NameNotFoundException;
import java.io.*;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ZanoWalletService implements ApplicationService {

    private final static Logger logger = LoggerFactory.getLogger(ZanoWalletService.class);

    private ScheduledExecutorService txChecker = Executors.newSingleThreadScheduledExecutor();
    private ScheduledExecutorService assetBalanceUpdater = Executors.newSingleThreadScheduledExecutor();

    private static Gson gson = new Gson();
    private static HashMap<String, AssetInfo> assetCache = new HashMap<String, AssetInfo>();
    private static LinkedHashMap<String, Object> config;
    private static String walletAddress;
    @Getter
    private static String walletAlias = null;

    private static String rpcDaemonAddress;
    private static String rpcWalletAddress;

    private static final String zanoAssetId = "d6329b5b1f7c0805b5c345f4957554002a2f557845f64d7645dae0e051a6498a";
    private static String tradeTokenAssetId;

    // UTXO management
    private static int minUtxo = 10;
    private static int maxUtxo = 40;
    private static BigDecimal minUtxoProducerZanoAmount = BigDecimal.valueOf(1);
    private static BigDecimal minUtxoProducerTokenAmount = BigDecimal.valueOf(50);
    private static long lastUtxoProcess = 0;

    private static long blockHeight;

    private static GetWalletBalanceResponse.AssetBalance floatTokensBalanceWalletMain;
    private static GetWalletBalanceResponse.AssetBalance floatTokensBalanceWalletRefill;
    private static GetWalletBalanceResponse.AssetBalance floatTokensBalanceAudit = null;
    private static GetWalletBalanceResponse.AssetBalance zanoBalanceWalletMain;
    private static GetWalletBalanceResponse.AssetBalance zanoBalanceWalletRefill = null;
    private static GetWalletBalanceResponse.AssetBalance zanoBalanceAudit = null;
    private static boolean skipRefill = true;
    private static boolean skipAudit = true;

    @Getter
    private static HashMap<String, ZanoWalletMeta> walletsAssetBalanceMap = new HashMap<String, ZanoWalletMeta>();

    public ZanoWalletService() {

        try {
            tradeTokenAssetId = SettingsService.getAppSetting("trade_token_asset_id");
            minUtxo = Integer.valueOf(SettingsService.getAppSetting("min_utxo"));
            maxUtxo = Integer.valueOf(SettingsService.getAppSetting("max_utxo"));
        } catch (NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        rpcDaemonAddress = "http://zanod:11211/json_rpc";
        rpcWalletAddress = "http://zanowallet:11212/json_rpc";

        try {
            // Main
            walletAddress = getWalletAddress();
            walletAlias = getAliasByAddress(walletAddress);
            String walletPubkey = signMessage(rpcWalletAddress, UUID.randomUUID().toString()).getPkey();
            ZanoWalletMeta mainWallet = new ZanoWalletMeta("main", walletAddress, walletPubkey, rpcWalletAddress, DatabaseService.getTxIndex("main"));
            logger.info("Wallet address: " + walletAddress);
            logger.info("Wallet alias: " + walletAlias);

            whiteListAsset(tradeTokenAssetId);

            // Add all wallets we are using
            walletsAssetBalanceMap.put(mainWallet.getIdent(), mainWallet);

            for (String ident : walletsAssetBalanceMap.keySet()) {
                updateAssetBalances(walletsAssetBalanceMap.get(ident), false);
            }
        } catch (NoApiResponseException e) {
            throw new RuntimeException(e);
        }

        logger.info("RPC Zano daemon: " + rpcDaemonAddress);
        logger.info("RPC Zano wallet: " + rpcWalletAddress);
    }

    @Override
    public void init() {

        AssetInfo zanoAsset = new AssetInfo();
        zanoAsset.setAsset_id("d6329b5b1f7c0805b5c345f4957554002a2f557845f64d7645dae0e051a6498a");
        zanoAsset.setOwner("0000000000000000000000000000000000000000000000000000000000000000");
        zanoAsset.setTicker("ZANO");
        zanoAsset.setDecimal_point(12);
        zanoAsset.setFull_name("Zano");
        assetCache.put(zanoAsset.getAsset_id(), zanoAsset);
        long newBlockHeight = 0;
        try {
            newBlockHeight = getBlockCount();
            if (newBlockHeight > blockHeight) {
                // System.out.println("Block height updated: " + newBlockHeight);
                blockHeight = newBlockHeight;
                if (walletAlias == null) {
                    walletAlias = getAliasByAddress(walletAddress);
                    if (walletAlias != null) {
                        SettingsService.saveAppSetting("pending_alias_tx", null);
                    }
                }
                if (SettingsService.getAppSettingSafe("pending_alias_tx") != null && walletAlias != null && !walletAlias.isEmpty()) {
                    SettingsService.saveAppSetting("pending_alias_tx", null);
                }
            }
        } catch (NoApiResponseException e) {
            throw new RuntimeException(e);
        }

        try {
            updateWalletAssetBalances();
        } catch (NoApiResponseException e) {
            e.printStackTrace();
        }

        // currentTxIndex = DatabaseService.getTxIndex();
        // currentTxIndex = 17;
        // System.out.println("Zano service started at transaction index " + currentTxIndex);

        try {
            checkNewBlockAndTransactions(true);
        } catch (NoApiResponseException e) {
            throw new RuntimeException(e);
        }

        try {
            checkUtxos();
        } catch (NoApiResponseException e) {
            e.printStackTrace();
        }

        txChecker.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    checkNewBlockAndTransactions(false);
                    checkAuditWalletTransfers();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 3000, 3000, TimeUnit.MILLISECONDS);

        assetBalanceUpdater.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    for (String ident : walletsAssetBalanceMap.keySet()) {
                        updateAssetBalances(walletsAssetBalanceMap.get(ident), false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        txChecker.shutdown();
    }

    public static void updateAllAssetBalances() {
        try {
            updateWalletAssetBalances(); // FIX THIS: CLEAN THIS UP!!!!
        } catch (NoApiResponseException e) {
            throw new RuntimeException(e);
        }
        for (String ident : walletsAssetBalanceMap.keySet()) {
            updateAssetBalances(walletsAssetBalanceMap.get(ident), false);
        }
    }

    private static void updateAssetBalances(ZanoWalletMeta zanoWalletMeta, boolean print) {
        try {

            WalletRpcResult<GetWalletBalanceResponse> balanceResp = getBalance(zanoWalletMeta.getRpcAddress());
            GetWalletBalanceResponse balance = balanceResp.getResult();
            GetWalletBalanceResponse.AssetBalance[] assetBalances = balance.getBalances();
            for (GetWalletBalanceResponse.AssetBalance assetBalance : assetBalances) {
                DatabaseService.insertZanoAsset(null, assetBalance.getAsset_info());
                BigDecimal totalUnlocked = new BigDecimal(assetBalance.getUnlocked()).movePointLeft(assetBalance.getAsset_info().getDecimal_point());
                if (print) {
                    System.out.println(assetBalance.getAsset_info().getAsset_id() + ", "+assetBalance.getAsset_info().getTicker()+": " + totalUnlocked.toPlainString());
                }
                zanoWalletMeta.getAssetBalanceMap().put(assetBalance.getAsset_info().getAsset_id(), assetBalance);
                int hash = gson.toJson(assetBalance).hashCode();
                if (zanoWalletMeta.getAssetBalanceMapHash().containsKey(assetBalance.getAsset_info().getAsset_id())) {
                    int oldHash = zanoWalletMeta.getAssetBalanceMapHash().get(assetBalance.getAsset_info().getAsset_id());
                    if (oldHash != hash) {
                        DatabaseService.insertWalletLog(null, new Timestamp(System.currentTimeMillis()), assetBalance.getAsset_info().getAsset_id(),
                                assetBalance.getOuts_count(), assetBalance.getTotal(), assetBalance.getUnlocked(), zanoWalletMeta.getWalletAdress(), zanoWalletMeta.getIdent());
                        zanoWalletMeta.getAssetBalanceMapHash().put(assetBalance.getAsset_info().getAsset_id(), hash);
                    }
                } else {
                    DatabaseService.insertWalletLog(null, new Timestamp(System.currentTimeMillis()), assetBalance.getAsset_info().getAsset_id(),
                            assetBalance.getOuts_count(), assetBalance.getTotal(), assetBalance.getUnlocked(), zanoWalletMeta.getWalletAdress(), zanoWalletMeta.getIdent());
                    zanoWalletMeta.getAssetBalanceMapHash().put(assetBalance.getAsset_info().getAsset_id(), hash);
                }
            }
        } catch (NoApiResponseException e) {
            e.printStackTrace();
        }
    }

    public static HashMap<String, GetWalletBalanceResponse.AssetBalance> getAssetBalanceMap(String ident) {
        return walletsAssetBalanceMap.get(ident).getAssetBalanceMap();
    }
    public static HashMap<String, Integer> getAssetBalanceMapHash(String ident) {
        return walletsAssetBalanceMap.get(ident).getAssetBalanceMapHash();
    }

    public static GetWalletBalanceResponse.AssetBalance getFloatTokensDataAudit() {
        return floatTokensBalanceAudit;
    }

    public static GetWalletBalanceResponse.AssetBalance getZanoDataAudit() {
        return zanoBalanceAudit;
    }

    public static GetWalletBalanceResponse.AssetBalance getFloatTokensDataMain() {
        if (floatTokensBalanceWalletMain == null) {
            return getEmptyAssetBalance();
        }
        return floatTokensBalanceWalletMain;
    }

    public static GetWalletBalanceResponse.AssetBalance getZanoDataMain() {
        if (zanoBalanceWalletMain == null) {
            return getEmptyAssetBalance();
        }
        return zanoBalanceWalletMain;
    }

    private static GetWalletBalanceResponse.AssetBalance getEmptyAssetBalance() {
        GetWalletBalanceResponse.AssetBalance emptyAssets = new GetWalletBalanceResponse.AssetBalance();

        emptyAssets.setTotal(BigInteger.ZERO);
        emptyAssets.setUnlocked(BigInteger.ZERO);
        emptyAssets.setAwaiting_in(BigInteger.ZERO);
        emptyAssets.setAwaiting_out(BigInteger.ZERO);
        emptyAssets.setOuts_amount_max(BigInteger.ZERO);
        emptyAssets.setOuts_amount_min(BigInteger.ZERO);
        emptyAssets.setOuts_count(0L);

        return emptyAssets;
    }

    private void checkNewBlockAndTransactions(boolean firstCheck) throws NoApiResponseException {

        // Check if we had a new block, if we had a new block, check for confirmed transactions
        long newBlockHeight = getBlockCount();
        boolean newBlock = false;
        if (newBlockHeight > blockHeight) {
            // System.out.println("Block height updated: " + newBlockHeight);
            blockHeight = newBlockHeight;

            if (walletAlias == null) {
                walletAlias = getAliasByAddress(walletAddress);
                if (walletAlias != null) {
                    SettingsService.saveAppSetting("pending_alias_tx", null);
                }
            }

            long timeSiceLastUtxoCheck = System.currentTimeMillis() - lastUtxoProcess;
            if (timeSiceLastUtxoCheck > (5 * 60 * 1000)) {
                try {
                    checkUtxos();
                } catch (NoApiResponseException e) {
                    e.printStackTrace();
                }
                timeSiceLastUtxoCheck = System.currentTimeMillis();
            }

            // Update the wallet balance
            updateWalletAssetBalances();
            newBlock = true;
        }
        // Always check for unconfirmed transactions
        for (String ident : walletsAssetBalanceMap.keySet()) {
            WalletRpcResult<RecentTxsAndInfo> recentTxsAndInfoWalletRpcResult =
                    (WalletRpcResult<RecentTxsAndInfo>) sendRequest(walletsAssetBalanceMap.get(ident).getRpcAddress(), getUnconfirmedTxRpcPayload(), RecentTxsAndInfo.class);
            RecentTxsAndInfo recentTxsAndInfo = recentTxsAndInfoWalletRpcResult.getResult();
            if (recentTxsAndInfo != null && recentTxsAndInfo.getTransfers() != null && recentTxsAndInfo.getTransfers().length > 0) {
                processWalletTransactions(walletsAssetBalanceMap.get(ident), recentTxsAndInfo.getTransfers(), false);
            }
        }

        // Check for confirmed transactions
        for (String ident : walletsAssetBalanceMap.keySet()) {
            ZanoWalletMeta walletMeta = walletsAssetBalanceMap.get(ident);
            WalletRpcResult<RecentTxsAndInfo> recentTxsAndInfoWalletRpcResult2 =
                    (WalletRpcResult<RecentTxsAndInfo>) sendRequest(walletMeta.getRpcAddress(), getConfirmedTxRpcPayload(walletMeta.getTxIndex()), RecentTxsAndInfo.class);
            RecentTxsAndInfo recentTxsAndInfo = recentTxsAndInfoWalletRpcResult2.getResult();
            if (recentTxsAndInfo != null && recentTxsAndInfo.getTransfers() != null && recentTxsAndInfo.getTransfers().length > 0) {
                processWalletTransactions(walletMeta, recentTxsAndInfo.getTransfers(), true);
            }
        }
    }

    private JSONObject getUnconfirmedTxRpcPayload() {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "get_recent_txs_and_info2");

        JSONObject params = new JSONObject();
        params.put("update_provision_info", true);
        params.put("exclude_mining_txs", false);
        params.put("offset", 0);
        params.put("count", 0);
        params.put("order", "FROM_BEGIN_TO_END");
        params.put("exclude_unconfirmed", false);

        payload.put("params", params);

        return payload;
    }

    private JSONObject getConfirmedTxRpcPayload(long txIndex) {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "get_recent_txs_and_info2");

        JSONObject params = new JSONObject();
        params.put("update_provision_info", true);
        params.put("exclude_mining_txs", false);
        params.put("offset", txIndex);
        params.put("count", 10);
        params.put("order", "FROM_BEGIN_TO_END");
        params.put("exclude_unconfirmed", false);
        payload.put("params", params);
        return payload;
    }

    private void processWalletTransactions(ZanoWalletMeta walletMeta, WalletTransfer[] walletTransfers, boolean isConfirmed) throws NoApiResponseException {
        for (WalletTransfer transfer : walletTransfers) {
            for (int i=0; i<transfer.getSubtransfers().length; i++) {
                DatabaseService.insertZanoWalletTransaction(null, walletMeta.getIdent(), transfer, transfer.getSubtransfers()[i], i, transfer.getTransfer_internal_index());
                if (!transfer.getSubtransfers()[0].getAsset_id().equals(zanoAssetId)) {
                    whiteListAsset(transfer.getSubtransfers()[0].getAsset_id());
                    // System.out.println("Tx " + transfer.getTx_hash() + " at index " + transfer.getTransfer_internal_index());
                    AssetInfo assetInfo = getAssetInfo(transfer.getSubtransfers()[0].getAsset_id());
                    if (assetInfo != null) {
                        DatabaseService.insertZanoAsset(null, assetInfo);
                        int decimals = assetInfo.getDecimal_point();
                        BigDecimal amount = new BigDecimal(transfer.getSubtransfers()[i].getAmount()).movePointLeft(decimals);
                        // Don't log unconfirmed
                        if (transfer.getHeight() > 0) {
                            logger.info("Inserting new wallet tx " + transfer.getTx_hash() + " for " + amount.toPlainString() + " " +
                                    assetInfo.getTicker() + " at index " + transfer.getTransfer_internal_index() + " into wallet " + walletMeta.getIdent());
                        }
                    } else {
                        logger.error("Asset info was null");
                    }
                } else {
                    BigDecimal amount = new BigDecimal(transfer.getSubtransfers()[i].getAmount()).movePointLeft(12);
                    if (transfer.getHeight() > 0) {
                        logger.info("Inserting new wallet tx " + transfer.getTx_hash() + " for " + amount.toPlainString() +
                                " ZANO at index " + transfer.getTransfer_internal_index() + " into wallet " + walletMeta.getIdent());
                    }
                }

            }
            if (isConfirmed && transfer.getTransfer_internal_index() > walletMeta.getTxIndex()) {
                walletMeta.setTxIndex(transfer.getTransfer_internal_index());
            }
        }
        if (isConfirmed) {
            walletMeta.setTxIndex(walletMeta.getTxIndex()+1);
            DatabaseService.updateTxIndex(walletMeta.getIdent(), walletMeta.getTxIndex());
        }
    }

    private static void updateWalletAssetBalances() throws NoApiResponseException {
        // Update MM wallet balances
        WalletRpcResult<GetWalletBalanceResponse> balanceResp = ZanoWalletService.getBalance(rpcWalletAddress);
        GetWalletBalanceResponse balance = balanceResp.getResult();
        Gson gson = new Gson();

        for (GetWalletBalanceResponse.AssetBalance assetBalance : balance.getBalances()) {
            if (assetBalance.getAsset_info().getAsset_id().equals(zanoAssetId)) {
                zanoBalanceWalletMain = assetBalance;
            } else if (assetBalance.getAsset_info().getAsset_id().equals(tradeTokenAssetId)) {
                floatTokensBalanceWalletMain = assetBalance;
            }
        }
    }

    private static GetWalletBalanceResponse.AssetBalance getAssetBalance(String assetId, String walletAddress) throws NoApiResponseException, AssetNotFoundException {
        WalletRpcResult<GetWalletBalanceResponse> balanceResp = ZanoWalletService.getBalance(walletAddress);
        GetWalletBalanceResponse balance = balanceResp.getResult();
        Gson gson = new Gson();

        for (GetWalletBalanceResponse.AssetBalance assetBalance : balance.getBalances()) {
            if (assetBalance.getAsset_info().getAsset_id().equals(assetId)) {
                return assetBalance;
            }
        }
        throw new AssetNotFoundException(assetId);
    }

    private static void checkUtxos() throws NoApiResponseException {
        WalletRpcResult<GetWalletBalanceResponse> balanceResp = ZanoWalletService.getBalance();
        GetWalletBalanceResponse balance = balanceResp.getResult();
        lastUtxoProcess = System.currentTimeMillis();

        for (GetWalletBalanceResponse.AssetBalance assetBalance : balance.getBalances()) {
            if ((assetBalance.getAsset_info().getAsset_id().equals(tradeTokenAssetId) || assetBalance.getAsset_info().getAsset_id().equals(zanoAssetId))) {
                BigDecimal totalUnlocked = new BigDecimal(assetBalance.getUnlocked()).movePointLeft(assetBalance.getAsset_info().getDecimal_point());
                BigDecimal total = new BigDecimal(assetBalance.getTotal()).movePointLeft(assetBalance.getAsset_info().getDecimal_point());

                System.out.println("Main wallet: " + assetBalance.getAsset_info().getAsset_id() + ", " + assetBalance.getAsset_info().getTicker() + ": " +
                        totalUnlocked.toPlainString() + ", UTXOs " + assetBalance.getOuts_count());

                // Check if we were constrained by UTXOs
                if (assetBalance.getAsset_info().getAsset_id().equals(tradeTokenAssetId) && ZanoPriceService.isContrainedByTokensUtxos()) {
                    // Compare the UTXOs available to the ones in the last order
                    BigDecimal tokenUTXOsAvailable = new BigDecimal(assetBalance.getUnlocked()).movePointLeft(assetBalance.getAsset_info().getDecimal_point());

                    // If number of tokens available is greater than the number of unlocked UTXOs
                    // Then place a new ask order
                    logger.info("Previous ask order was constrained by unlocked UTXOs");
                    logger.info("Renewing order previusly constrained by UTXOs");
                    ZanoPriceService.sendAskOrderToTradeService(true);

                    // THis doesn't work. Investigate
                    //if (tokenUTXOsAvailable.compareTo(ZanoPriceService.getDexAskVolumeInTokens()) > 0) {

                    //}
                }

                if (assetBalance.getOuts_count() > maxUtxo) {
                    System.out.println("Combining UTXOs");
                    consolidateUTXOs();
                } else {
                    logger.debug("UTXO levels satisfied");
                }
            }
        }
    }

    public static JSONObject getInfo() {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "getinfo");
        JSONObject params = new JSONObject();
        params.put("flags", 1);
        payload.put("params", params);
        JSONObject rpcResult = null;
        try {
            rpcResult = sendRequest(rpcDaemonAddress, payload);
            if (rpcResult.containsKey("result")) {
                JSONObject result = (JSONObject) rpcResult.get("result");
                return result;
            }
        } catch (NoApiResponseException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public static void consolidateUTXOs() {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "sweep_below");

        JSONObject params = new JSONObject();
        params.put("address", walletAddress);
        params.put("amount", 100000000000L);
        params.put("fee", 10000000000L);
        params.put("mixin", 16);

        payload.put("params", params);
        JSONObject rpcResult = null;
        try {
            rpcResult = sendRequest(rpcWalletAddress, payload);
            if (rpcResult.containsKey("result")) {
                JSONObject result = (JSONObject) rpcResult.get("result");
                logger.info("UTXO consolidation (Sweep Below) finished with response: " + result.toJSONString());
            }
        } catch (NoApiResponseException e) {
            logger.error(e.getMessage());
        }
    }

    public static NewAddressResponse getNewAddress() throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "make_integrated_address");
        JSONObject adressRep = (JSONObject) sendRequest(rpcWalletAddress, payload).get("result");


        NewAddressResponse newAddr = new NewAddressResponse((String) adressRep.get("integrated_address"), (String) adressRep.get("payment_id"));
        System.out.println(newAddr);
        return newAddr;
    }

    public static SwapProposalInfo decodeSwapProposal(String rawProp) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "ionic_swap_get_proposal_info");

        JSONObject params = new JSONObject();
        params.put("hex_raw_proposal", rawProp);
        payload.put("params", params);

        JSONObject rpcResponse = (JSONObject) sendRequest(rpcWalletAddress, payload);
        System.out.println(rpcResponse);
        JSONObject proposalData = (JSONObject) rpcResponse.get("result");
        System.out.println(proposalData);

        Gson gson = new Gson();
        if (proposalData.containsKey("proposal")) {
            JSONObject proposalDetails = (JSONObject) proposalData.get("proposal");
            System.out.println(proposalDetails);
            SwapProposalInfo swapProposalInfo = gson.fromJson(proposalDetails.toJSONString(), SwapProposalInfo.class);
            return swapProposalInfo;
        }
        throw new NoApiResponseException();
    }

    public static String acceptSwapProposal(String rawProp) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "ionic_swap_accept_proposal");

        JSONObject params = new JSONObject();
        params.put("hex_raw_proposal", rawProp);
        payload.put("params", params);

        System.out.println(payload);
        JSONObject response = (JSONObject) sendRequest(rpcWalletAddress, payload);
        System.out.println(response);
        if (response.containsKey("error") && !response.containsKey("result")) {
            throw new NoApiResponseException();
        }
        JSONObject swapData = (JSONObject) response.get("result");

        if (swapData.containsKey("result_tx_id")) {
            String txId = (String) swapData.get("result_tx_id");
            return txId;
        }
        throw new NoApiResponseException();
    }

    public static String generateSwapProposal(String destAddress,
                                                          BigInteger toAmount, String toAsset,
                                                          BigInteger fromAmount, String fromAsset) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "ionic_swap_generate_proposal");
        JSONObject params = new JSONObject();
        params.put("destination_address", destAddress);

        JSONObject proposal = new JSONObject();
        proposal.put("fee_paid_by_a", 10000000000L);

        JSONArray toFinalizer  = new JSONArray();
        JSONObject toFinalizerItem = new JSONObject();
        toFinalizerItem.put("amount", toAmount.toString());
        toFinalizerItem.put("asset_id", toAsset);
        toFinalizer.add(toFinalizerItem);
        proposal.put("to_finalizer", toFinalizer);

        JSONArray toInitiator  = new JSONArray();
        JSONObject toInitiatorItem = new JSONObject();
        toInitiatorItem.put("amount", fromAmount.toString());
        toInitiatorItem.put("asset_id", fromAsset);
        toInitiator.add(toInitiatorItem);
        proposal.put("to_initiator", toInitiator);

        params.put("proposal", proposal);

        payload.put("params", params);

        System.out.println(proposal.toJSONString());
        JSONObject rep = (JSONObject) sendRequest(rpcWalletAddress, payload).get("result");
        System.out.println(rep.get("hex_raw_proposal"));
        return (String) rep.get("hex_raw_proposal");
    }

    public static NewAddressResponse getNewAddress(String paymentId) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "make_integrated_address");
        JSONObject params = new JSONObject();
        params.put("payment_id", paymentId);
        payload.put("params", params);
        JSONObject rep = (JSONObject) sendRequest(rpcWalletAddress, payload).get("result");
        NewAddressResponse newAddr = new NewAddressResponse((String) rep.get("integrated_address"), (String) rep.get("payment_id"));
        System.out.println(newAddr);
        return newAddr;
    }

    public static String emitAsset(String address, BigInteger amount, String assetId) throws NoApiResponseException, NotEnoughMoneyException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "emit_asset");
        JSONObject params = new JSONObject();
        params.put("asset_id", assetId);
        params.put("do_not_split_destinations", false);

        JSONObject destination = new JSONObject();
        destination.put("address", address);
        destination.put("amount", amount.toString());
        destination.put("asset_id", assetId);

        JSONArray destinations = new JSONArray();
        destinations.add(destination);
        params.put("destinations", destinations);

        payload.put("params", params);
        JSONObject fullResponse = sendRequest(rpcWalletAddress, payload);
        JSONObject rep = (JSONObject) fullResponse.get("result");
        if (rep == null) {
            System.out.println(fullResponse);
            if (fullResponse.containsKey("error")) {
                JSONObject error = (JSONObject) fullResponse.get("error");
                String errorMessage = (String) error.get("message");
                if (errorMessage.equals("WALLET_RPC_ERROR_CODE_NOT_ENOUGH_MONEY")) {
                    throw new NotEnoughMoneyException(errorMessage);
                }
            }
            throw new NoApiResponseException();
        }
        System.out.println(rep.toJSONString());
        if (rep.containsKey("tx_id")) {
            return (String) rep.get("tx_id");
        } else {
            throw new NoApiResponseException();
        }
    }
    public static String getWalletAddress() throws NoApiResponseException {
        return getWalletAddress(rpcWalletAddress);
    }

    public static String getWalletAddress(String rpcWallet) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "getaddress");
        JSONObject adressRep = (JSONObject) sendRequest(rpcWallet, payload).get("result");
        return (String) adressRep.get("address");
    }

    public static long getBlockCount() throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "getblockcount");
        JSONObject rep = (JSONObject) sendRequest(rpcDaemonAddress, payload).get("result");
        return (Long) rep.get("count");
    }

    public static String getAliasByAddress(String address) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "get_alias_by_address");
        payload.put("params", address);
        JSONObject adressRep = (JSONObject) sendRequest(rpcDaemonAddress, payload).get("result");
        JSONArray aliasInfoList = (JSONArray) adressRep.get("alias_info_list");
        String alias = null;
        if (aliasInfoList != null) {
            JSONObject aliasInfo = (JSONObject) aliasInfoList.get(0);
            alias = (String) aliasInfo.get("alias");
        }
        return alias;
    }

    public static AliasDetails getAliasDetails(String alias) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "get_alias_details");
        JSONObject params = new JSONObject();
        params.put("alias", alias);

        payload.put("params", params);
        JSONObject responseBody = sendRequest(rpcDaemonAddress, payload);
        System.out.println(responseBody);
        if (responseBody.containsKey("result")) {
            JSONObject aliasDetailsJson = (JSONObject) ((JSONObject) responseBody.get("result")).get("alias_details");
            AliasDetails aliasDetails = new AliasDetails();
            aliasDetails.setAlias((String) alias);
            aliasDetails.setBaseAddress((String) aliasDetailsJson.get("address"));
            aliasDetails.setComment((String) aliasDetailsJson.get("comment"));
            aliasDetails.setTrackingKey((String) aliasDetailsJson.get("tracking_key"));
            return aliasDetails;
        }
        return null;
    }

    public static String getAliasCommentByAddress(String address) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "get_alias_by_address");
        payload.put("params", address);
        JSONObject adressRep = (JSONObject) sendRequest(rpcDaemonAddress, payload).get("result");
        JSONArray aliasInfoList = (JSONArray) adressRep.get("alias_info_list");
        String alias = null;
        if (aliasInfoList != null) {
            JSONObject aliasInfo = (JSONObject) aliasInfoList.get(0);
            alias = (String) aliasInfo.get("comment");
        }
        return alias;
    }

    public static boolean validateAddress(String address) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("jsonrpc", "2.0");
            payload.put("id", 0);
            payload.put("method", "get_integrated_address");

            JSONObject params = new JSONObject();
            params.put("payment_id", "11111111");
            params.put("regular_address", address);

            payload.put("params", params);
            JSONObject adressRep = (JSONObject) sendRequest(rpcDaemonAddress, payload).get("result");
            String integratedAddress = (String) adressRep.get("integrated_address");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String setAlias(String alias, String comment) throws ZanoWalletException, NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "register_alias");

        JSONObject paramsAl = new JSONObject();

        JSONObject params = new JSONObject();
        params.put("address", walletAddress);
        params.put("alias", alias);
        params.put("comment", comment);

        paramsAl.put("al", params);

        payload.put("params", paramsAl);

        JSONObject fullResponse = (JSONObject) sendRequest(rpcWalletAddress, payload);
        System.out.println(gson.toJson(fullResponse));
        if (fullResponse.containsKey("result")) {
            JSONObject regAliasRep = (JSONObject) fullResponse.get("result");
            if (regAliasRep.containsKey("tx_id")) {
                return (String) regAliasRep.get("tx_id");
            }
        }

        String message = "Error";
        if (fullResponse.containsKey("error")) {
            JSONObject error = (JSONObject) fullResponse.get("error");
            message = (String) error.get("message");
        }

        throw new ZanoWalletException(message);
    }

    public static IntegratedAddress splitIntegratedAddress(String intAddress) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        //payload.put("method", "integrated_address");
        payload.put("method", "split_integrated_address");
        JSONObject params = new JSONObject();
        params.put("integrated_address", intAddress);
        payload.put("params", params);
        JSONObject adressRep = (JSONObject) sendRequest(rpcWalletAddress, payload).get("result");
        IntegratedAddress IntegratedAddress = new IntegratedAddress((String) adressRep.get("standard_address"), (String) adressRep.get("payment_id"));
        return IntegratedAddress;
    }

    public static AssetInfo getAssetInfo(String assetId) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "get_asset_info");
        JSONObject params = new JSONObject();
        params.put("asset_id", assetId);
        payload.put("params", params);

        Gson gson = new Gson();

        String assetInfoResp = sendRequestRaw(rpcDaemonAddress, payload);
        ZanoRpcResponse zanoRpcResponse = gson.fromJson(assetInfoResp, ZanoRpcResponse.class);
        RpcResult result = zanoRpcResponse.getResult();
        String status = result.getStatus();
        if (status != null && !status.isEmpty() && status.equals("OK")) {
            AssetInfo assetInfo = result.getAsset_descriptor();
            assetInfo.setAsset_id(assetId);
            return assetInfo;
        }

        throw new NoApiResponseException();
    }

    public static void whiteListAsset(String assetId) throws NoApiResponseException {
        whiteListAsset(rpcWalletAddress, assetId);
    }

    public static void whiteListAsset(String rpcAddress, String assetId) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "assets_whitelist_add");
        JSONObject params = new JSONObject();
        params.put("asset_id", assetId);
        payload.put("params", params);
        String assetInfoResp = sendRequestRaw(rpcAddress, payload);
        // System.out.println(assetInfoResp);
    }

    public static NewAddressResponse verifyAddress() throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "make_integrated_address");
        JSONObject adressRep = (JSONObject) sendRequest(rpcWalletAddress, payload).get("result");


        NewAddressResponse newAddr = new NewAddressResponse((String) adressRep.get("integrated_address"), (String) adressRep.get("payment_id"));
        System.out.println(newAddr);
        return newAddr;
    }

    public static WalletRpcResult<GetWalletBalanceResponse> getBalance() throws NoApiResponseException {
        return getBalance(rpcWalletAddress);
    }

    public static WalletRpcResult<GetWalletBalanceResponse> getBalance(String rpcWallet) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "getbalance");
        // Object returnObj = sendRequest(rpcWalletAddress, payload, GetWalletBalanceResponse.class);
        WalletRpcResult<GetWalletBalanceResponse> balanceResp = (WalletRpcResult<GetWalletBalanceResponse>) sendRequest(rpcWallet, payload, GetWalletBalanceResponse.class);
        return balanceResp;
    }

    public static SignMessageResponse signMessage(String walletAddress, String buffer) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "sign_message");
        JSONObject params = new JSONObject();
        params.put("buff", buffer);
        payload.put("params", params);
        WalletRpcResult<SignMessageResponse> signResp = (WalletRpcResult<SignMessageResponse>) sendRequest(walletAddress, payload, SignMessageResponse.class);
        SignMessageResponse signMessageResponse = signResp.getResult();

        return signMessageResponse;
    }


    public static ZanoTradeAuthResponse authenticateToZanoTrade() throws NoApiResponseException, IOException {
        UUID uuid = UUID.randomUUID();
        String encodedPayload = Base64.getEncoder().encodeToString(uuid.toString().getBytes());
        SignMessageResponse signMessageResponse = signMessage(rpcWalletAddress, encodedPayload);

        if (walletAlias == null) {
            System.err.println("WARNING: wallet alias is null!!");
            throw new RuntimeException("Wallet alias is null, probably because blockchain isn't fully synced yet.");
        }

        ZanoTradeAuthRequestData data = new ZanoTradeAuthRequestData(
                walletAddress,
                walletAlias,
                uuid.toString(),
                signMessageResponse.getSig()
        );
        ZanoTradeAuthRequest zanoTradeAuthRequest = new ZanoTradeAuthRequest(data, true);
        Gson gson = new Gson();
        PostHandler postHandler = new PostHandler();
        System.out.println(gson.toJson(zanoTradeAuthRequest));
        String response = postHandler.sendJsonPost("https://trade.zano.org/api/auth", zanoTradeAuthRequest);
        ZanoTradeAuthResponse authResponse = gson.fromJson(response, ZanoTradeAuthResponse.class);
        return authResponse;
    }

    public static TxInfo getTransactionInfo(String txHash) throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "get_tx_details");
        JSONObject params = new JSONObject();
        params.put("tx_hash", txHash);
        payload.put("params", params);
        String responseRaw = sendRequestRaw(rpcDaemonAddress, payload);

        Gson gson = new Gson();
        ZanoRpcResponse zanoRpcResponse = gson.fromJson(responseRaw, ZanoRpcResponse.class);
        if (zanoRpcResponse.getResult() != null && zanoRpcResponse.getResult().getStatus().equals("OK")) {
            return zanoRpcResponse.getResult().getTx_info();
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

    private static Object sendRequest(String url, JSONObject payload, Class castClass) throws NoApiResponseException {
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
                    Gson gson = new Gson();

                    if (castClass.equals(GetWalletBalanceResponse.class)) {
                        Type typeMyType = new TypeToken<WalletRpcResult<GetWalletBalanceResponse>>(){}.getType();

                        return gson.fromJson(text, typeMyType);
                    } else if (castClass.equals(RecentTxsAndInfo.class)) {
                        Type typeMyType = new TypeToken<WalletRpcResult<RecentTxsAndInfo>>(){}.getType();

                        return gson.fromJson(text, typeMyType);
                    } else if (castClass.equals(AssetInfoResponse.class)) {
                        Type typeMyType = new TypeToken<WalletRpcResult<AssetInfoResponse>>(){}.getType();

                        return gson.fromJson(text, typeMyType);
                    } else if (castClass.equals(SignMessageResponse.class)) {
                        Type typeMyType = new TypeToken<WalletRpcResult<SignMessageResponse>>(){}.getType();

                        return gson.fromJson(text, typeMyType);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        throw new NoApiResponseException();
    }

    private static String sendRequestRaw(String url, JSONObject payload) throws NoApiResponseException {
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
                    return text;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        throw new NoApiResponseException();
    }

    public static SendResponse sendCoins(String address, long amount, String comment) throws NoApiResponseException {
        return sendCoins(address, amount, comment, 1, null, rpcWalletAddress);
    }

    public static SendResponse sendCoinsAnon(String address, long amount, String assetId, String comment) throws NoApiResponseException {
        return sendCoins(address, BigInteger.valueOf(amount), comment, 1, assetId, rpcWalletAddress, false, null);
    }

    public static SendResponse sendCoinsAnon(String address, long amount, String assetId, String comment, String paymentId) throws NoApiResponseException {
        return sendCoins(address, BigInteger.valueOf(amount), comment, 1, assetId, rpcWalletAddress, true, paymentId);
    }

    public static SendResponse sendCoins(String address, BigInteger amount, String assetId, String comment, String paymentId) throws NoApiResponseException {
        return sendCoins(address, amount, comment, 1, assetId, rpcWalletAddress, true, paymentId);
    }

    public static SendResponse sendCoins(String address, long amount, String comment, long number, String assetId, String rpcWallet) throws NoApiResponseException {
        return sendCoins(address, BigInteger.valueOf(amount), comment, number, assetId, rpcWalletAddress, true, null);
    }

    public static SendResponse sendCoins(String address, BigInteger amount, String comment, long number, String assetId, String rpcWallet, boolean pushPayer, String paymentId) throws NoApiResponseException {
        JSONObject destination = new JSONObject();
        destination.put("amount", amount);
        destination.put("address", address);
        if (assetId != null) {
            destination.put("asset_id", assetId);
        }

        JSONArray destinations = new JSONArray();
        for (int i=0; i<number; i++) {
            destinations.add(destination);
        }

        JSONObject payload = new JSONObject();

        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "transfer");

        JSONObject params = new JSONObject();
        params.put("destinations", destinations);
        if (!address.startsWith("i") && paymentId != null) {
            params.put("payment_id", paymentId);
        }
        params.put("push_payer", true);
        params.put("hide_receiver", false);
        params.put("service_entries_permanent", false);
        params.put("fee", 10000000000L);
        params.put("mixin", 15);
        if (comment != null && !comment.isEmpty()) {
            params.put("comment", comment);
        }
        payload.put("params", params);
        logger.info("Sending coins");
        logger.info(payload.toJSONString());
        JSONObject walletRequest = sendRequest(rpcWallet, payload);
        logger.info(walletRequest.toJSONString());
        if (walletRequest.containsKey("result")) {
            JSONObject adressRep = (JSONObject) walletRequest.get("result");
            logger.info(adressRep.toJSONString());

            SendResponse sendRep = new SendResponse();
            sendRep.setTxhash((String) adressRep.get("tx_hash"));
            sendRep.setStatus(200);

            System.out.println("Sent TX with tx hash: " + sendRep.getTxhash());
            return sendRep;
        } else if (walletRequest.containsKey("error")) { {
            JSONObject errorRep = (JSONObject) walletRequest.get("error");
            SendResponse sendRep = new SendResponse();
            sendRep.setStatus(Integer.parseInt(String.valueOf((long) errorRep.get("code"))));
            sendRep.setError((String) errorRep.get("message"));
            return sendRep;
        }}
        SendResponse sendRep = new SendResponse();
        sendRep.setStatus(500);
        return sendRep;
    }

    public static SendResponse sendCoinsHidden(String address, BigInteger amount, String assetId, String rpcWallet) throws NoApiResponseException {
        /*
        {
          "jsonrpc": "2.0",
          "id": 0,
          "method": "transfer",
          "params": {
            "destinations": [
              {
                "amount": 1000000000,
                "address": "ZxCkEgHf3ci8hgBfboZeCENaYrHBYZ1bLFi5cgWvn4WJLaxfgs4kqG6cJi9ai2zrXWSCpsvRXit14gKjeijx6YPC1zT8rneEf"
              }
            ],
            "push_payer": true,
            "hide_receiver": false,
            "service_entries_permanent": false,
            "fee": 1000000000000,
            "mixin": 10,
            "comment": "",
            "service_entries": []
          }
        }
         */
        JSONObject destination = new JSONObject();
        destination.put("amount", amount);
        destination.put("address", address);
        if (assetId != null) {
            destination.put("asset_id", assetId);
        }

        JSONArray destinations = new JSONArray();
        destinations.add(destination);

        JSONObject payload = new JSONObject();

        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "transfer");

        JSONObject params = new JSONObject();
        params.put("destinations", destinations);
        params.put("push_payer", false);
        params.put("hide_receiver", true);
        params.put("service_entries_permanent", false);
        params.put("fee", 10000000000L);
        // params.put("mixin", 1);
        payload.put("params", params);
        logger.info("Sending coins (hidden)");
        logger.info(payload.toJSONString());
        JSONObject walletRequest = sendRequest(rpcWallet, payload);
        logger.info(walletRequest.toJSONString());
        SendResponse sendRep = new SendResponse();
        sendRep.setStatus(500);
        if (walletRequest.containsKey("result")) {
            JSONObject adressRep = (JSONObject) walletRequest.get("result");
            logger.info(adressRep.toJSONString());

            sendRep.setTxhash((String) adressRep.get("tx_hash"));
            sendRep.setStatus(200);

            logger.info("Sent TX with tx hash: " + sendRep.getTxhash());
            return sendRep;
        }
        if (walletRequest.containsKey("error")) {
            JSONObject errorJson = (JSONObject) walletRequest.get("error");
            sendRep.setError((String) errorJson.get("message"));
        }
        return sendRep;
    }

    private static void checkAuditWalletTransfers() {

        List<InternalWalletTransfer> transfers = DatabaseService.getAuditTransferList();

        for (InternalWalletTransfer transfer : transfers) {
            System.out.println(transfer.toString());
            if (skipAudit) {
                DatabaseService.updateAuditTransfer(transfer.getId(), -2, "Audit wallet not active", null);
            } else {

                try {
                    String address = null;
                    if (transfer.getAddress().equals("audit")) {
                        address = walletsAssetBalanceMap.get("audit").getWalletAdress();
                    } else if (transfer.getAddress().equals("second") && config.containsKey("secondAddress")) {
                        address = (String) config.get("secondAddress");
                    }
                    if (address == null) {
                        break;
                    }
                    logger.info("Sending transfer to " + address);
                    SendResponse response = sendCoinsHidden(address,
                            transfer.getAmount().movePointRight(12).toBigInteger(), zanoAssetId, rpcWalletAddress);
                    if (response.getStatus() == 200) {
                        DatabaseService.updateAuditTransfer(transfer.getId(), 1, null, response.getTxhash());
                        // Update the bid order since the wallet balance has now changed
                        updateAllAssetBalances();
                        ZanoPriceService.sendBidOrderToTradeService(true);
                    } else {
                        DatabaseService.updateAuditTransfer(transfer.getId(), -1, response.getError(), null);
                    }
                } catch (NoApiResponseException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
