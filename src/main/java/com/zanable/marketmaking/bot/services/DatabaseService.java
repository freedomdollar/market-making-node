package com.zanable.marketmaking.bot.services;

import com.zanable.marketmaking.bot.ApplicationStartup;
import com.zanable.marketmaking.bot.beans.TelegramChannel;
import com.zanable.marketmaking.bot.beans.TwoFactorData;
import com.zanable.marketmaking.bot.beans.WalletTransaction;
import com.zanable.marketmaking.bot.beans.api.ExtendedTradeChain;
import com.zanable.marketmaking.bot.beans.market.*;
import com.zanable.marketmaking.bot.beans.zano.*;
import com.zanable.marketmaking.bot.enums.TradeType;
import com.zanable.marketmaking.bot.enums.TwoFactorType;
import com.zanable.shared.interfaces.ApplicationService;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;


public class DatabaseService implements ApplicationService {

    private final static Logger logger = LoggerFactory.getLogger(DatabaseService.class);


    private static String dataSourceUrlStatic;
    private static String dataSourceUserStatic;
    private static String dataSourcePassStatic;

    public DatabaseService(String dataSourceUrl, String dataSourceUser, String dataSourcePass) {
        dataSourceUrlStatic = String.valueOf(dataSourceUrl);
        dataSourceUserStatic = String.valueOf(dataSourceUser);
        dataSourcePassStatic = String.valueOf(dataSourcePass);
    }

    @Override
    public void init() {

        while(!DatabaseService.testDatabaseConnection()) {
            logger.info("Waiting for database to be ready.");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        logger.info("Starting database service. Tx index is " + getTxIndex("main"));

        try {
            Connection connection = getConnection();
            connection.close();
            ApplicationStartup.setDatabaseServiceStarted(true);
            logger.info("Database test connection OK");
        } catch (SQLException e) {
            ApplicationStartup.getErrors().add("Unable to connect to database");
        }
    }

    @Override
    public void destroy() {

    }

    /**
     * Insert trade log into Database
     * @param conn
     * @param myOrderId
     * @param otherOrderId
     * @param tokenAmount
     * @param zanoAmount
     * @param type
     * @param txid
     * @param timestamp
     * @param price
     * @param zanoPriceUsdt
     * @param assetId
     */
    public static void insertTradeLog(Connection conn, long myOrderId, long otherOrderId, BigInteger tokenAmount,
                                      BigInteger zanoAmount, String type, String txid, Timestamp timestamp,
                                      BigInteger price, BigDecimal zanoPriceUsdt, String assetId, UUID seqId) {

        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO trade_log (my_order_id, other_order_id, token_amount, zano_amount, type, " +
                    "txid, timestamp, zano_price, zano_usdt_price, asset_id, seq_id) VALUES (?,?,?,?,?,?,?,?,?,?,?);";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setLong(1, myOrderId);
            ps.setLong(2, otherOrderId);
            ps.setString(3, tokenAmount.toString());
            ps.setString(4, zanoAmount.toString());
            ps.setString(5, type);
            ps.setString(6, txid);
            ps.setTimestamp(7, timestamp);
            ps.setString(8, price.toString());
            ps.setBigDecimal(9, zanoPriceUsdt);
            ps.setString(10, assetId);
            ps.setString(11, seqId.toString());
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void insertZanoBuyBack(Connection conn, long orderId, BigDecimal amount, BigDecimal zanoAmount) {
        boolean connWasNull = false;
        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO zano_buy_back_log (connected_order, timestamp, usdt_amount, zano_amount) VALUES (?,NOW(),?,?);";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setLong(1, orderId);
            ps.setBigDecimal(2, amount);
            ps.setBigDecimal(3, zanoAmount);
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void insertFusdToUsdtCexOrder(Connection conn, long orderId, BigDecimal usdtAmount, BigDecimal fusdAmount, UUID seqId) {
        boolean connWasNull = false;
        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO fusd_to_usdt_cex_trade_log (connected_order, timestamp, usdt_amount, fusd_amount, seq_id) VALUES (?,NOW(),?,?,?);";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setLong(1, orderId);
            ps.setBigDecimal(2, usdtAmount);
            ps.setBigDecimal(3, fusdAmount);
            ps.setString(4, seqId.toString());
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void insertUsdtToZanoCexOrder(Connection conn, long orderId, BigDecimal usdtAmount, BigDecimal zanoPrice, UUID seqId) {
        boolean connWasNull = false;
        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO usdt_to_zano_cex_trade_log (connected_order, timestamp, usdt_amount, price, seq_id) VALUES (?,NOW(),?,?,?);";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setLong(1, orderId);
            ps.setBigDecimal(2, usdtAmount);
            ps.setBigDecimal(3, zanoPrice);
            ps.setString(4, seqId.toString());
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void updateUsdtToZanoBuyOrder(long id, int status, BigDecimal amountExecuted) {
        PreparedStatement ps = null;
        Connection conn;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE usdt_to_zano_cex_trade_log SET status=?, zano_amount_filled=? WHERE id=?");
            ps.setInt(1, status);
            ps.setBigDecimal(2, amountExecuted);
            ps.setLong(3, id);
            int rows = ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateZanoSellOrder(long id, int status, BigDecimal amountFilled, String orderId) {
        PreparedStatement ps = null;
        Connection conn;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE usdt_buy_back_log SET status=?, usdt_amount_filled=?, cex_order_id=? WHERE id=?");
            ps.setInt(1, status);
            ps.setBigDecimal(2, amountFilled);
            ps.setString(3, orderId);
            ps.setLong(4, id);
            int rows = ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateZanoSellOrder(long id, int status) {
        PreparedStatement ps = null;
        Connection conn;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE usdt_buy_back_log SET status=? WHERE id=?");
            ps.setInt(1, status);
            ps.setLong(2, id);
            int rows = ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateZanoSellOrder(long id, int status, BigDecimal amountFilled, String orderId, BigDecimal price) {
        PreparedStatement ps = null;
        Connection conn;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE usdt_buy_back_log SET status=?, usdt_amount_filled=?, cex_order_id=?, price=? WHERE id=?");
            ps.setInt(1, status);
            ps.setBigDecimal(2, amountFilled);
            ps.setString(3, orderId);
            ps.setBigDecimal(4, price);
            ps.setLong(5, id);
            int rows = ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateUsdtToZanoBuyOrder(long id, int status, BigDecimal price, BigDecimal zanoAmount, String cexOrderId) {
        PreparedStatement ps = null;
        Connection conn;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE usdt_to_zano_cex_trade_log SET status=?, cex_order_id=?, price=?, zano_amount=? WHERE id=?");
            ps.setInt(1, status);
            ps.setString(2, cexOrderId);
            ps.setBigDecimal(3, price);
            ps.setBigDecimal(4, zanoAmount);
            ps.setLong(5, id);
            int rows = ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateFusdSellOrderCex(long id, int status, BigDecimal usdtAmount, BigDecimal fusdAmountExecuted, String orderId, BigDecimal price) {
        PreparedStatement ps = null;
        Connection conn;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE fusd_to_usdt_cex_trade_log SET status=?, usdt_amount=?, fusd_amount_filled=?, cex_order_id=?, price=? WHERE id=?");
            ps.setInt(1, status);
            ps.setBigDecimal(2, usdtAmount);
            ps.setBigDecimal(3, fusdAmountExecuted);
            ps.setString(4, orderId);
            ps.setBigDecimal(5, price);
            ps.setLong(6, id);
            int rows = ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void insertZanoSellOrder(Connection conn, long orderId, BigDecimal usdtAmount, BigDecimal zanoAmount, BigDecimal price, UUID seqId) {
        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO usdt_buy_back_log (connected_order, timestamp, usdt_amount, zano_amount, price, seq_id) VALUES (?,NOW(),?,?,?,?);";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setLong(1, orderId);
            ps.setBigDecimal(2, usdtAmount);
            ps.setBigDecimal(3, zanoAmount);
            ps.setBigDecimal(4, price);
            ps.setString(5, seqId.toString());
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void insertZanoMove(Connection conn, String fromIdent, String toIdent, String toAddress, String txid, BigDecimal amount) {
        /*
        create table zano_moves
(
    from_ident varchar(128)    null,
    to_ident   varchar(128)    null,
    to_address varchar(512)    null,
    txid       varchar(128)    null,
    amount     decimal(32, 12) null,
    timestamp  timestamp       null
);
         */
        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO zano_moves (from_ident, to_ident, to_address, txid, amount, timestamp) VALUES (?,?,?,?,?,NOW());";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, fromIdent);
            ps.setString(2, toIdent);
            ps.setString(3, toAddress);
            ps.setString(4, txid);
            ps.setBigDecimal(5, amount);
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void insertFusdBuyOrder(Connection conn, BigDecimal usdtAmount, long connectedOrderId, UUID seqId) {
        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO fusd_buy_back_log (timestamp, usdt_amount, seq_id, connected_order, status) VALUES (NOW(),?,?,?,0);";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setBigDecimal(1, usdtAmount);
            ps.setString(2, seqId.toString());
            ps.setLong(3, connectedOrderId);
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void updateFusdBuyOrder(long id, String orderId, BigDecimal amount, BigDecimal fusdAmount, BigDecimal price, BigDecimal fusdAmountFilled, int status) {
        boolean connWasNull = false;
        Connection conn = null;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "UPDATE fusd_buy_back_log SET cex_order_id=?, usdt_amount=?, fusd_amount=?, price=?, fusd_amount_filled=?, status=? WHERE id=?;";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, orderId);
            ps.setBigDecimal(2, amount);
            ps.setBigDecimal(3, fusdAmount);
            ps.setBigDecimal(4, price);
            ps.setBigDecimal(5, fusdAmountFilled);
            ps.setInt(6, status);
            ps.setLong(7, id);
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void updateFusdBuyOrder(long id, int status, String error) {
        boolean connWasNull = false;
        Connection conn = null;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "UPDATE fusd_buy_back_log SET status=?, error=? WHERE id=?;";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setInt(1, status);
            ps.setString(2, error);
            ps.setLong(3, id);
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<ExtendedTradeChain> getLastTradesExtended(int count) {
        List<ExtendedTradeChain> trades = new ArrayList<>();
        try {
            Connection conn = getConnection();
            String query = "SELECT A.zano_amount, A.token_amount, B.decimals, B.ticker, A.zano_usdt_price, A.type, A.timestamp, A.my_order_id, A.other_order_id, A.seq_id FROM trade_log A JOIN zano_assets B ON (A.asset_id = B.asset_id) WHERE seq_id IS NOT NULL AND type='sell' ORDER BY timestamp DESC LIMIT ?;";
            if (count == 0) {
                query = "SELECT A.zano_amount, A.token_amount, B.decimals, B.ticker, A.zano_usdt_price, A.type, A.timestamp, A.seq_id, A.my_order_id, A.other_order_id FROM trade_log A JOIN zano_assets B ON (A.asset_id = B.asset_id) WHERE seq_id IS NOT NULL AND type='sell' ORDER BY timestamp DESC;";
            }
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setInt(1, count);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int decimals = rs.getInt("decimals");
                BigDecimal tokenAmount = new BigDecimal(rs.getString("token_amount")).movePointLeft(decimals);
                BigDecimal zanoTraded = new BigDecimal(rs.getString("zano_amount")).movePointLeft(12);

                ExtendedTradeChain extendedTradeChain = ExtendedTradeChain.builder()
                        .decimals(rs.getInt("decimals"))
                        .tokensTraded(tokenAmount)
                        .zanoTraded(zanoTraded)
                        .ticker(rs.getString("ticker"))
                        .zanoUsdtPrice(rs.getBigDecimal("zano_usdt_price"))
                        .type(TradeType.valueOf(rs.getString("type").toUpperCase()))
                        .timestamp(rs.getTimestamp("timestamp"))
                        .myOrderId(rs.getLong("my_order_id"))
                        .otherOrderId(rs.getLong("other_order_id"))
                        .build();

                if (rs.getString("seq_id") != null) {
                    extendedTradeChain.setSeqId(UUID.fromString(rs.getString("seq_id")));
                }

                if (extendedTradeChain.getTimestamp() != null) {
                    extendedTradeChain.setTimestampLong(extendedTradeChain.getTimestamp().getTime());
                }

                if (extendedTradeChain.getSeqId() != null) {
                    extendedTradeChain.setZanoSellOrder(getZanoSellorder(extendedTradeChain.getSeqId()));
                    extendedTradeChain.setFusdBuyOrder(getFusdBuyorder(extendedTradeChain.getSeqId()));
                }

                trades.add(extendedTradeChain);
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return trades;
    }

    public static List<ExtendedTradeChain> getLastTradesExtendedBuy(int count) {
        List<ExtendedTradeChain> trades = new ArrayList<>();
        try {
            Connection conn = getConnection();
            String query = "SELECT A.zano_amount, A.token_amount, B.decimals, B.ticker, A.zano_usdt_price, A.type, A.timestamp, A.my_order_id, A.other_order_id, A.seq_id FROM trade_log A JOIN zano_assets B ON (A.asset_id = B.asset_id) WHERE seq_id IS NOT NULL AND type='buy' ORDER BY timestamp DESC LIMIT ?;";
            if (count == 0) {
                query = "SELECT A.zano_amount, A.token_amount, B.decimals, B.ticker, A.zano_usdt_price, A.type, A.timestamp, A.seq_id, A.my_order_id, A.other_order_id FROM trade_log A JOIN zano_assets B ON (A.asset_id = B.asset_id) WHERE seq_id IS NOT NULL AND type='buy' ORDER BY timestamp DESC;";
            }
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setInt(1, count);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int decimals = rs.getInt("decimals");
                BigDecimal tokenAmount = new BigDecimal(rs.getString("token_amount")).movePointLeft(decimals);
                BigDecimal zanoTraded = new BigDecimal(rs.getString("zano_amount")).movePointLeft(12);

                ExtendedTradeChain extendedTradeChain = ExtendedTradeChain.builder()
                        .decimals(rs.getInt("decimals"))
                        .tokensTraded(tokenAmount)
                        .zanoTraded(zanoTraded)
                        .ticker(rs.getString("ticker"))
                        .zanoUsdtPrice(rs.getBigDecimal("zano_usdt_price"))
                        .type(TradeType.valueOf(rs.getString("type").toUpperCase()))
                        .timestamp(rs.getTimestamp("timestamp"))
                        .myOrderId(rs.getLong("my_order_id"))
                        .otherOrderId(rs.getLong("other_order_id"))
                        .build();

                if (rs.getString("seq_id") != null) {
                    extendedTradeChain.setSeqId(UUID.fromString(rs.getString("seq_id")));
                }

                if (extendedTradeChain.getTimestamp() != null) {
                    extendedTradeChain.setTimestampLong(extendedTradeChain.getTimestamp().getTime());
                }

                if (extendedTradeChain.getSeqId() != null) {
                    extendedTradeChain.setFusdSellOrder(DatabaseService.getFusdToUsdtCexTrade(extendedTradeChain.getSeqId()));
                    extendedTradeChain.setZanoBuyOrder(DatabaseService.getUsdtToZanoCexTrade(extendedTradeChain.getSeqId()));
                }

                trades.add(extendedTradeChain);
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return trades;
    }

    public static void getTokenSellPnL() {
        // How many tokens have we sold
        // SELECT SUM(A.token_amount), B.decimals, B.ticker, AVG(A.zano_usdt_price) FROM trade_log A JOIN zano_assets B ON (A.asset_id = B.asset_id) WHERE seq_id IS NOT NULL AND type='sell';

        // How many tokens did we buy back on the CEX
        // SELECT SUM(fusd_amount_filled) AS fUSDbought FROM fusd_buy_back_log WHERE status=3;
    }

    public static List<WalletTransaction> getWalletTransactions(Connection conn, String walletIdent, long page, long pageSize) {

        List<WalletTransaction> txList = new ArrayList<>();
        boolean connWasNull = false;
        try {
            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            PreparedStatement ps = conn.prepareStatement("SELECT A.txid, amount, ticker, full_name, C.id AS swap_id, remote_alias, tx_index, remote_address, " +
                    "A.asset_id, height, is_income, vout, timestamp, decimals, is_mining  " +
                    "FROM zano_wallet_transactions A LEFT JOIN zano_assets B ON (A.asset_id = B.asset_id) " +
                    "LEFT JOIN swaps C ON (C.txid = A.txid) WHERE A.wallet_ident=? ORDER BY timestamp DESC LIMIT ? OFFSET ?;");
            ps.setString(1, walletIdent);
            ps.setLong(2, pageSize);
            ps.setLong(3, pageSize*page);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                WalletTransaction walletTransaction = new WalletTransaction();
                walletTransaction.setTxId(rs.getString("txid"));
                walletTransaction.setAmount(new BigDecimal(rs.getString("amount")).movePointLeft(rs.getInt("decimals")));
                walletTransaction.setTicker(rs.getString("ticker"));
                walletTransaction.setFullName(rs.getString("full_name"));
                walletTransaction.setRemoteAlias(rs.getString("remote_alias"));
                walletTransaction.setTransactionIndex(rs.getLong("tx_index"));
                walletTransaction.setRemoteAddress(rs.getString("remote_address"));
                walletTransaction.setAssetId(rs.getString("asset_id"));
                walletTransaction.setHeight(rs.getLong("height"));
                walletTransaction.setIncome(rs.getBoolean("is_income"));
                walletTransaction.setSubTransferIndex(rs.getInt("vout"));
                walletTransaction.setTimestamp(rs.getTimestamp("timestamp"));
                walletTransaction.setSwapId(rs.getLong("swap_id"));
                walletTransaction.setMining(rs.getBoolean("is_mining"));

                // Maybe it's a move to a CEX?
                if (walletTransaction.getSwapId() == 0) {
                    String ident = getZanoMoveDestIdent(null, walletTransaction.getTxId());
                    if (ident != null) {
                        walletTransaction.setRemoteAlias(ident);
                    }
                }

                txList.add(walletTransaction);
            }
            if (!connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return txList;
    }

    public static long getWalletTransactionsItems(Connection conn, String walletIdent) {

        long items = 0;
        boolean connWasNull = false;
        try {
            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS count " +
                    "FROM zano_wallet_transactions A LEFT JOIN zano_assets B ON (A.asset_id = B.asset_id) " +
                    "LEFT JOIN swaps C ON (C.txid = A.txid) WHERE A.wallet_ident=? ORDER BY timestamp;");
            ps.setString(1, walletIdent);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                items = rs.getLong("count");
            }
            if (!connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public static String getZanoMoveDestIdent(Connection conn, String txid) {

        String ident = null;
        boolean connWasNull = false;
        try {
            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            PreparedStatement ps = conn.prepareStatement("SELECT to_ident FROM zano_moves WHERE txid=?;");
            ps.setString(1, txid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ident = rs.getString("to_ident");
            }
            if (!connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ident;
    }

    public static UUID updateUsdtBuyOrderWithUUID(long id) {
        boolean connWasNull = false;
        Connection conn = null;
        UUID uuid = UUID.randomUUID();

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "UPDATE usdt_buy_back_log SET seq_id=? WHERE id=? AND seq_id IS NULL;";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, uuid.toString());
            ps.setLong(2, id);
            int updated = ps.executeUpdate();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
            if (updated > 0) {
                return uuid;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static SimplifiedTrade getZanoSellorder(UUID seqId) {
        SimplifiedTrade simplifiedTrade = null;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM usdt_buy_back_log WHERE seq_id=?;");
            ps.setString(1, seqId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                simplifiedTrade = SimplifiedTrade.builder()
                        .firstCurrency("ZANO")
                        .secondCurrency("USDT")
                        .firstAmount(rs.getBigDecimal("zano_amount"))
                        .secondAmount(rs.getBigDecimal("usdt_amount"))
                        .secondAmountTarget(rs.getBigDecimal("usdt_amount_filled"))
                        .status(rs.getInt("status"))
                        .price(rs.getBigDecimal("usdt_amount").divide(rs.getBigDecimal("zano_amount"), 6, RoundingMode.DOWN))
                        .seqId(seqId)
                        .timestamp(rs.getTimestamp("timestamp"))
                        .type("SELL")
                        .build();

                if (simplifiedTrade.getStatus() == 0) {
                    simplifiedTrade.setStatusString("Initialized");
                } else if (simplifiedTrade.getStatus() == 1) {
                    simplifiedTrade.setStatusString("Placed");
                } else if (simplifiedTrade.getStatus() == 2) {
                    simplifiedTrade.setStatusString("Cancelled");
                } else if (simplifiedTrade.getStatus() == 3) {
                    simplifiedTrade.setStatusString("Filled");
                } else if (simplifiedTrade.getStatus() == -1) {
                    simplifiedTrade.setStatusString("Cancelled");
                } else if (simplifiedTrade.getStatus() == -2) {
                    simplifiedTrade.setStatusString("Not allowed, volume too small");
                } else if (simplifiedTrade.getStatus() == -3) {
                    simplifiedTrade.setStatusString("Oversold");
                } else if (simplifiedTrade.getStatus() == -4) {
                    simplifiedTrade.setStatusString("Error");
                }
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return simplifiedTrade;
    }

    public static BigDecimal getZanoPriceFromOrder(long orderId) {
        BigDecimal zanoPrice = null;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM order_log WHERE order_id=?;");
            ps.setLong(1, orderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                zanoPrice = rs.getBigDecimal("zano_usdt_price");
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return zanoPrice;
    }

    public static SimplifiedTrade getFusdBuyorder(UUID seqId) {
        SimplifiedTrade simplifiedTrade = null;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM fusd_buy_back_log WHERE seq_id=?;");
            ps.setString(1, seqId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                simplifiedTrade = SimplifiedTrade.builder()
                        .firstCurrency("FUSD")
                        .secondCurrency("USDT")
                        .firstAmount(rs.getBigDecimal("fusd_amount"))
                        .secondAmount(rs.getBigDecimal("usdt_amount"))
                        .status(rs.getInt("status"))
                        .price(rs.getBigDecimal("price"))
                        .seqId(seqId)
                        .timestamp(rs.getTimestamp("timestamp"))
                        .type("BUY")
                        .build();
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return simplifiedTrade;
    }

    public static SimplifiedTrade getZanoBuybackOrder(UUID seqId) {
        SimplifiedTrade simplifiedTrade = null;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM zano_buy_back_log WHERE seq_id=?;");
            ps.setString(1, seqId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                simplifiedTrade = SimplifiedTrade.builder()
                        .firstCurrency("ZANO")
                        .secondCurrency("USDT")
                        .firstAmount(rs.getBigDecimal("zano_amount"))
                        .secondAmount(rs.getBigDecimal("usdt_amount"))
                        .status(rs.getInt("status"))
                        .price(rs.getBigDecimal("price"))
                        .seqId(seqId)
                        .timestamp(rs.getTimestamp("timestamp"))
                        .type("BUY")
                        .build();
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return simplifiedTrade;
    }

    public static SimplifiedTrade getFusdToUsdtCexTrade(UUID seqId) {
        SimplifiedTrade simplifiedTrade = null;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM fusd_to_usdt_cex_trade_log WHERE seq_id=?;");
            ps.setString(1, seqId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                simplifiedTrade = SimplifiedTrade.builder()
                        .firstCurrency("FUSD")
                        .secondCurrency("USDT")
                        .firstAmount(rs.getBigDecimal("fusd_amount"))
                        .secondAmount(rs.getBigDecimal("usdt_amount"))
                        .status(rs.getInt("status"))
                        .price(rs.getBigDecimal("price"))
                        .seqId(seqId)
                        .timestamp(rs.getTimestamp("timestamp"))
                        .type("SELL")
                        .build();
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return simplifiedTrade;
    }

    public static SimplifiedTrade getUsdtToZanoCexTrade(UUID seqId) {
        SimplifiedTrade simplifiedTrade = null;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM usdt_to_zano_cex_trade_log WHERE seq_id=?;");
            ps.setString(1, seqId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                simplifiedTrade = SimplifiedTrade.builder()
                        .firstCurrency("ZANO")
                        .secondCurrency("USDT")
                        .firstAmount(rs.getBigDecimal("zano_amount"))
                        .secondAmount(rs.getBigDecimal("usdt_amount"))
                        .status(rs.getInt("status"))
                        .price(rs.getBigDecimal("price"))
                        .seqId(seqId)
                        .timestamp(rs.getTimestamp("timestamp"))
                        .type("BUY")
                        .build();
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return simplifiedTrade;
    }

    public static List<FusdUsdtBuyReq> getFusdBuyOrders() {
        List<FusdUsdtBuyReq> buys = new ArrayList<FusdUsdtBuyReq>();
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM fusd_buy_back_log WHERE status=0 AND seq_id IS NOT NULL ORDER BY id DESC;");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                FusdUsdtBuyReq fusdUsdtBuyReq = FusdUsdtBuyReq.builder()
                        .id(rs.getLong("id"))
                        .timestamp(rs.getTimestamp("timestamp"))
                        .usdtAmount(rs.getBigDecimal("usdt_amount"))
                        .connectedOrderId(rs.getLong("connected_order"))
                        .seqId(UUID.fromString(rs.getString("seq_id")))
                        .build();
                String uuid = rs.getString("seq_id");
                if (uuid != null) {
                    fusdUsdtBuyReq.setSeqId(UUID.fromString(uuid));
                }
                buys.add(fusdUsdtBuyReq);
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return buys;
    }

    public static List<ZanoSellReq> getZanoSellOrders() {

        List<ZanoSellReq> sells = new ArrayList<ZanoSellReq>();
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM usdt_buy_back_log WHERE status=0 AND timestamp > DATE_SUB(NOW(), INTERVAL 5 HOUR) ORDER BY id ASC;");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                sells.add(ZanoSellReq.builder()
                        .id(rs.getLong("id"))
                        .timestamp(rs.getTimestamp("timestamp"))
                        .amount(rs.getBigDecimal("zano_amount"))
                        .amountFilled(rs.getBigDecimal("usdt_amount_filled"))
                        .zanoPrice(rs.getBigDecimal("price"))
                        .cexOrderId(rs.getString("cex_order_id"))
                        .connectedOrderId(rs.getLong("connected_order"))
                        .seqId(UUID.fromString(rs.getString("seq_id")))
                        .build()
                );
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sells;
    }

    public static long insert2FA(Connection conn, TwoFactorType type, String data, long loginId) {

        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO two_factor_auths (type, data, created, login_id) " +
                    "VALUES (?,?,NOW(),?);";
            PreparedStatement ps = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, type.toString());
            ps.setString(2, data);
            ps.setLong(3, loginId);
            ps.execute();
            ResultSet keys = ps.getGeneratedKeys();
            long id = -1;
            if (keys.next()) {
                id = keys.getLong(1);
            }
            ps.close();

            if (connWasNull) {
                conn.close();
            }

            return id;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static void insertTelegramChannel(Connection conn, long channelId, String name) {
        boolean connWasNull = false;
        try {
            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT IGNORE INTO telegram_channels (channel_id, channel_name) " +
                    "VALUES (?,?);";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setLong(1, channelId);
            ps.setString(2, name);
            ps.execute();

            if (connWasNull) {
                conn.close();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void removeTelegramChannel(Connection conn, long channelId) {
        boolean connWasNull = false;
        try {
            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "DELETE FROM telegram_channels WHERE channel_id=?";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setLong(1, channelId);
            ps.execute();

            if (connWasNull) {
                conn.close();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<TelegramChannel> getTelegramChannels(Connection conn) {

        List<TelegramChannel> channels = new ArrayList<>();
        boolean connWasNull = false;
        try {
            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM telegram_channels");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                channels.add(new TelegramChannel(rs.getLong("channel_id"), rs.getString("channel_name")));
            }
            if (!connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return channels;
    }

    public static List<TwoFactorData> get2FaData(Connection conn, long loginId) {

        List<TwoFactorData> twoFaList = new ArrayList<>();
        boolean connWasNull = false;
        try {
            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM two_factor_auths WHERE login_id=?");
            ps.setLong(1, loginId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TwoFactorData twoFactorData = new TwoFactorData(loginId, rs.getString("data"), TwoFactorType.valueOf(rs.getString("type")), rs.getTimestamp("created"));
                twoFaList.add(twoFactorData);
            }
            if (!connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return twoFaList;
    }

    public static void insertSimplifiedTrade(Connection conn, String firstCurrency, BigDecimal firstAmount,
                                             String secondCurrency, BigDecimal secondAmount, String feeCurrency, BigDecimal fee, long timestamp, long connectedOrderId, int sequenceNumber, String side, UUID seqId) {
        /*
        CREATE TABLE `simplified_trade` (
          `first_currency` varchar(32) DEFAULT NULL,
          `first_amount` decimal(32,18) DEFAULT NULL,
          `second_currency` varchar(32) DEFAULT NULL,
          `second_amount` decimal(32,18) DEFAULT NULL,
          `fee` decimal(32,18) DEFAULT NULL,
          `fee_currency` varchar(32) DEFAULT NULL,
          `timestamp` timestamp NULL DEFAULT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci
         */
        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO simplified_trade (first_currency, first_amount, second_currency, second_amount, fee, fee_currency, timestamp, seq_no, connected_order, side, seq_id) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE timestamp=?;";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, firstCurrency);
            ps.setBigDecimal(2, firstAmount);
            ps.setString(3, secondCurrency);
            ps.setBigDecimal(4, secondAmount);
            ps.setBigDecimal(5, fee);
            ps.setString(6, feeCurrency);
            ps.setTimestamp(7, new Timestamp(timestamp));
            ps.setInt(8, sequenceNumber);
            ps.setLong(9, connectedOrderId);
            ps.setString(10, side);
            ps.setString(11, seqId.toString());
            ps.setTimestamp(12, new Timestamp(timestamp));

            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Insert order log
     * @param conn
     * @param orderId
     * @param total
     * @param price
     * @param left
     * @param status
     * @param opened
     * @param closed
     * @param type
     * @param zanoUsdtPrice
     */
    public static void insertOrderLog(Connection conn, long orderId, BigDecimal total, BigDecimal price,
                                      BigDecimal left, int status, Timestamp opened, Timestamp closed, String type, BigDecimal zanoUsdtPrice) {

        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO order_log (order_id, total, price, amount_left, status, opened, closed, type, amount_start, zano_usdt_price) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE amount_left=?, closed=?;";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setLong(1, orderId);
            ps.setBigDecimal(2, total);
            ps.setBigDecimal(3, price);
            ps.setBigDecimal(4, left);
            ps.setInt(5, status);
            ps.setTimestamp(6, opened);
            ps.setTimestamp(7, closed);
            ps.setString(8, type);
            ps.setBigDecimal(9, left);
            ps.setBigDecimal(10, zanoUsdtPrice);

            ps.setBigDecimal(11, left);
            ps.setTimestamp(12, closed);

            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Insert wallet log
     * @param conn
     * @param timestamp
     * @param assetId
     * @param utxos
     * @param total
     * @param unlocked
     * @param walletAddress
     * @param ident
     */
    public static void insertWalletLog(Connection conn, Timestamp timestamp, String assetId, long utxos, BigInteger total, BigInteger unlocked, String walletAddress, String ident) {

        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO zano_wallet_balance (timestamp, asset_id, utxos, total, unlocked, wallet_address, ident) " +
                    "VALUES (?,?,?,?,?,?,?);";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setTimestamp(1, timestamp);
            ps.setString(2, assetId);
            ps.setLong(3, utxos);
            ps.setString(4, total.toString());
            ps.setString(5, unlocked.toString());
            ps.setString(6, walletAddress);
            ps.setString(7, ident);
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Insert swap into database
     * @param conn
     * @param swapProposalInfo
     * @param txid
     * @param accepted
     * @param reason
     * @param myOrderId
     * @param otherOrderId
     */
    public static void insertSwap(Connection conn, SwapProposalInfo swapProposalInfo, String txid, boolean accepted, String reason, long myOrderId, long otherOrderId) {

        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO swaps (to_finalizer_amount, to_finalizer_asset_id, to_initiator_amount, to_initiator_asset_id, txid, accepted, reason, my_order_id, other_order_id) " +
                    "VALUES (?,?,?,?,?,?,?,?,?);";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setLong(1, swapProposalInfo.getTo_finalizer()[0].getAmount());
            ps.setString(2, swapProposalInfo.getTo_finalizer()[0].getAsset_id());
            ps.setLong(3,  swapProposalInfo.getTo_initiator()[0].getAmount());
            ps.setString(4, swapProposalInfo.getTo_initiator()[0].getAsset_id());
            ps.setString(5, txid);
            ps.setBoolean(6, accepted);
            ps.setString(7, reason);
            ps.setLong(8,  myOrderId);
            ps.setLong(9,  otherOrderId);
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Close active order by adding a timestamp to column 'closed'
     * @param orderId
     * @param status
     * @param closed
     */
    public static void closeOrder(long orderId, int status, Timestamp closed) {
        PreparedStatement ps = null;
        Connection conn;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE order_log SET status=?, closed=? WHERE order_id = ? AND status >= 1 AND closed IS null");
            ps.setInt(1, status);
            ps.setTimestamp(2, closed);
            ps.setLong(3, orderId);
            int rows = ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Update order status
     * @param orderId
     * @param status
     * @param left
     */
    public static void updateOrder(long orderId, int status, BigDecimal left) {
        PreparedStatement ps = null;
        Connection conn;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE order_log SET status=?, amount_left=? WHERE order_id = ? AND status > 0 AND closed IS null");
            ps.setInt(1, status);
            ps.setBigDecimal(2, left);
            ps.setLong(3, orderId);
            int rows = ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Upsert zano asset meta data
     * @param conn
     * @param assetInfo
     */
    public static void insertZanoAsset(Connection conn, AssetInfo assetInfo) {

        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO zano_assets (asset_id, current_supply, decimals, full_name, hidden_supply, meta_info, owner, owner_eth_pub_key, ticker, total_max_supply) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE current_supply=?, owner=?, owner_eth_pub_key=?;";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, assetInfo.getAsset_id());
            ps.setString(2, assetInfo.getCurrent_supply().toString());
            ps.setInt(3, assetInfo.getDecimal_point());
            ps.setString(4, assetInfo.getFull_name());
            ps.setBoolean(5, assetInfo.isHidden_supply());
            ps.setString(6, assetInfo.getMeta_info());
            ps.setString(7, assetInfo.getOwner());
            ps.setString(8, assetInfo.getOwner_eth_pub_key());
            ps.setString(9, assetInfo.getTicker());
            ps.setString(10, assetInfo.getTotal_max_supply().toString());
            ps.setString(11, assetInfo.getCurrent_supply().toString());
            ps.setString(12, assetInfo.getOwner());
            ps.setString(13, assetInfo.getOwner_eth_pub_key());

            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Insert zano wallet transaction into database
     * @param conn
     * @param ident
     * @param walletTransfer
     * @param subtransfer
     * @param vout
     * @param txIndex
     */
    public static void insertZanoWalletTransaction(Connection conn, String ident, WalletTransfer walletTransfer, Subtransfer subtransfer, long vout, long txIndex) {

        boolean connWasNull = false;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO zano_wallet_transactions (txid, comment, fee, height, is_mining, is_mixing, is_service, payment_id, remote_address, remote_alias, " +
                    "vout, amount, asset_id, is_income, timestamp, unlock_time, wallet_ident, tx_index) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE height=?;";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, walletTransfer.getTx_hash());
            ps.setString(2, walletTransfer.getComment());
            ps.setString(3, walletTransfer.getFee().toString());
            ps.setLong(4, walletTransfer.getHeight());
            ps.setBoolean(5, walletTransfer.is_mining());
            ps.setBoolean(6, walletTransfer.is_mixing());
            ps.setBoolean(7, walletTransfer.is_service());
            ps.setString(8, walletTransfer.getPayment_id());
            String remoteAdress = null;
            if (walletTransfer.getRemote_addresses() != null && walletTransfer.getRemote_addresses().length>0) {
                remoteAdress = walletTransfer.getRemote_addresses()[0];
            }
            ps.setString(9, remoteAdress);
            String remoteAlias = null;
            if (walletTransfer.getRemote_aliases() != null && walletTransfer.getRemote_aliases().length>0) {
                remoteAlias = walletTransfer.getRemote_aliases()[0];
            }
            ps.setString(10, remoteAlias);
            ps.setLong(11, vout);
            ps.setString(12, subtransfer.getAmount().toString());
            ps.setString(13, subtransfer.getAsset_id());
            ps.setBoolean(14, subtransfer.is_income());
            ps.setTimestamp(15, new Timestamp(walletTransfer.getTimestamp()*1000));
            ps.setLong(16, walletTransfer.getUnlock_time());
            ps.setString(17, ident);
            ps.setLong(18, txIndex);
            ps.setLong(19, walletTransfer.getHeight());

            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the tx index for the zano wallet
     * @param walletIdent
     * @param newIndex
     * @return
     */
    public static long updateTxIndex(String walletIdent, long newIndex) {
        long txIndex = 0;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("UPDATE settings SET transaction_index=? WHERE wallet_ident=?");
            ps.setLong(1, newIndex);
            ps.setString(2, walletIdent);
            ps.execute();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return txIndex;
    }

    /**
     * Get current tx index
     * @param ident
     * @return
     */
    public static long getTxIndex(String ident) {
        long txIndex = 0;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT transaction_index FROM settings WHERE wallet_ident=?");
            ps.setString(1, ident);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                txIndex = rs.getLong("transaction_index");
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return txIndex;
    }

    public static List<InternalWalletTransfer> getAuditTransferList() {
        /*
        CREATE TABLE `audit_wallet_transfers` (
          `id` int(11) NOT NULL AUTO_INCREMENT,
          `requester` varchar(256) DEFAULT NULL,
          `comment` varchar(256) DEFAULT NULL,
          `txid` varchar(256) DEFAULT NULL,
          `status` int(11) DEFAULT NULL,
          `timestamp` timestamp NULL DEFAULT NULL,
          `amount` decimal(32,12) DEFAULT NULL,
          PRIMARY KEY (`id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci
         */
        List<InternalWalletTransfer> transfers = new ArrayList<>();
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM audit_wallet_transfers WHERE status=0;");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                transfers.add(InternalWalletTransfer.builder()
                        .id(rs.getInt("id"))
                        .requester(rs.getString("requester"))
                        .comment(rs.getString("comment"))
                        .status(0)
                        .timestamp(rs.getTimestamp("timestamp"))
                        .amount(rs.getBigDecimal("amount"))
                        .address(rs.getString("address"))
                        .build()
                );
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return transfers;
    }

    public static int updateAuditTransfer(int id, int status, String error, String txid) {
        int rows = 0;
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("UPDATE audit_wallet_transfers SET status=?, error=?, txid=? WHERE id=?");
            ps.setInt(1, status);
            ps.setString(2, error);
            ps.setString(3, txid);
            ps.setInt(4, id);
            rows = ps.executeUpdate();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }

    /**
     * Get Zano sell requests
     * @return
     */
    public static List<ZanoSellReq> getActiveSellOrders() {

        List<ZanoSellReq> orderIds = new ArrayList<ZanoSellReq>();
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM usdt_buy_back_log WHERE status=1 AND cex_order_id IS NOT NULL ORDER BY id ASC;");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ZanoSellReq zanoSellReq = ZanoSellReq.builder()
                        .id(rs.getLong("id"))
                        .timestamp(rs.getTimestamp("timestamp"))
                        .amount(rs.getBigDecimal("zano_amount"))
                        .amountFilled(rs.getBigDecimal("usdt_amount_filled"))
                        .zanoPrice(rs.getBigDecimal("price"))
                        .cexOrderId(rs.getString("cex_order_id"))
                        .build();

                String uuid = rs.getString("seq_id");
                if (uuid != null) {
                    zanoSellReq.setSeqId(UUID.fromString(uuid));
                }
                orderIds.add(zanoSellReq);
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orderIds;
    }

    public static List<FusdSellReq> getPendingFusdSellOrdersCex() {

        List<FusdSellReq> orders = new ArrayList<FusdSellReq>();
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM fusd_to_usdt_cex_trade_log WHERE status=0;");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                FusdSellReq fusdSellReq = FusdSellReq.builder()
                        .id(rs.getLong("id"))
                        .timestamp(rs.getTimestamp("timestamp"))
                        .fusdAmount(rs.getBigDecimal("fusd_amount"))
                        .fusdAmountFilled(rs.getBigDecimal("fusd_amount_filled"))
                        .usdtAmount(rs.getBigDecimal("usdt_amount"))
                        .zanoPrice(rs.getBigDecimal("price"))
                        .cexOrderId(rs.getString("cex_order_id"))
                        .seqId(UUID.fromString(rs.getString("seq_id")))
                        .connectedOrderId(rs.getLong("connected_order"))
                        .build();

                orders.add(fusdSellReq);
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orders;
    }

    public static List<ZanoBuyReq> getPendingZanoBuyOrdersCex() {

        List<ZanoBuyReq> orders = new ArrayList<ZanoBuyReq>();
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM usdt_to_zano_cex_trade_log WHERE status >= 0 AND status < 3;");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ZanoBuyReq zanoBuyReq = ZanoBuyReq.builder()
                        .id(rs.getLong("id"))
                        .timestamp(rs.getTimestamp("timestamp"))
                        .usdtAmount(rs.getBigDecimal("usdt_amount"))
                        .zanoAmount(rs.getBigDecimal("zano_amount"))
                        .zanoAmountExecuted(rs.getBigDecimal("zano_amount_filled"))
                        .zanoPrice(rs.getBigDecimal("price"))
                        .cexOrderId(rs.getString("cex_order_id"))
                        .seqId(UUID.fromString(rs.getString("seq_id")))
                        .status(rs.getInt("status"))
                        .connectedOrderId(rs.getLong("connected_order"))
                        .build();

                orders.add(zanoBuyReq);
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orders;
    }

    /**
     * Get "hot" application settings (reloaded without restarting the whole application)
     * @return Settings Map
     */
    public static HashMap<String, String> getAppSettingsFromDb() {
        HashMap<String, String> settings = new HashMap<>();
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT `key`, `value` FROM app_settings;");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                settings.put(rs.getString("key"), rs.getString("value"));
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return settings;
    }

    public static void upsertAppSetting(String key, String value) {
        boolean connWasNull = true;
        Connection conn = null;

        try {

            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            String query = "INSERT INTO app_settings (`key`, `value`) VALUES (?,?) ON DUPLICATE KEY UPDATE `value`=?;";
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setString(3, value);
            ps.execute();
            ps.close();

            if (connWasNull) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get token buy sell statistics
     * @return
     */
    public static JSONArray getTokensBuySellStats() {
        JSONArray jsonArray = new JSONArray();
        try {
            Connection conn = getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT SUM(token_amount) AS tokens, B.decimals, SUM(zano_amount) AS zano, A.type FROM trade_log A JOIN zano_assets B ON (A.asset_id = B.asset_id) GROUP BY type;");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject tradeStats = new JSONObject();
                int decimals = rs.getInt("decimals");
                BigDecimal tokenTraded = rs.getBigDecimal("tokens").movePointLeft(decimals);
                tradeStats.put("tokens", tokenTraded);
                BigDecimal zanoTraded = rs.getBigDecimal("zano").movePointLeft(12);
                tradeStats.put("zano", zanoTraded);
                tradeStats.put("side", rs.getString("type"));
                jsonArray.add(tradeStats);
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return jsonArray;
    }

    /**
     * Get last 20 trades
     * @return trade array
     */
    public static JSONArray getLastTrades() {
        return getLastTrades(20);
    }

    /**
     * Get last trades
     * @param count
     * @return trade array
     */
    public static JSONArray getLastTrades(int count) {
        JSONArray jsonArray = new JSONArray();
        try {
            Connection conn = getConnection();
            String query = "SELECT A.zano_amount, A.token_amount, B.decimals, B.ticker, A.zano_usdt_price, A.type, A.timestamp, A.my_order_id, A.other_order_id FROM trade_log A JOIN zano_assets B ON (A.asset_id = B.asset_id) ORDER BY timestamp DESC LIMIT ?;";
            if (count == 0) {
                query = "SELECT A.zano_amount, A.token_amount, B.decimals, B.ticker, A.zano_usdt_price, A.type, A.timestamp FROM trade_log A JOIN zano_assets B ON (A.asset_id = B.asset_id) ORDER BY timestamp DESC;";
            }
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setInt(1, count);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject trade = new JSONObject();
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
                jsonArray.add(trade);
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return jsonArray;
    }

    public static List<String> getPendingSwaps(Connection conn) {

        List<String> pendingSwaps = new ArrayList<>();
        boolean connWasNull = false;
        try {
            if (conn == null) {
                conn = getConnection();
                connWasNull = true;
            }
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM cross_chain_swaps WHERE status = 2 AND dst_chain='ZANO';");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                pendingSwaps.add(rs.getString("signed_payload"));
            }
            ps.close();
            if (connWasNull) {
                conn.close();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pendingSwaps;
    }

    public static void updateSwapStatus(String txHash, int status, String outgoingTxHash) {
        PreparedStatement ps = null;
        Connection conn;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE cross_chain_swaps SET dst_txid=?, status=? WHERE src_txid = ?;");
            ps.setString(1, outgoingTxHash);
            ps.setInt(2, status);
            ps.setString(3, txHash);
            int rows = ps.executeUpdate();

            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void applyPatch(String resourceFileName) throws Exception {
        applyPatch(getConnection(), resourceFileName);
    }

    /**
     * Executes the SQL patch file from the resources folder.
     *
     * @param connection Active JDBC connection (MariaDB)
     * @param resourceFileName Path to the SQL file in resources (e.g. "db/patches/v2_patch.sql")
     * @throws Exception if any I/O or SQL error occurs
     */
    public static void applyPatch(Connection connection, String resourceFileName) throws Exception {
        // Load the SQL file from classpath
        InputStream inputStream = DatabaseService.class.getClassLoader().getResourceAsStream(resourceFileName);
        if (inputStream == null) {
            throw new IllegalArgumentException("SQL file not found in resources: " + resourceFileName);
        }

        // Read the entire file as a single string
        String sql = new BufferedReader(new InputStreamReader(inputStream))
                .lines()
                .collect(Collectors.joining("\n"));

        // Split SQL statements by semicolon (basic splitting — works for most migration scripts)
        String[] statements = sql.split("(?<=;)(?=\\s*[^\\n])");

        try (Statement stmt = connection.createStatement()) {
            for (String s : statements) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    boolean executed = stmt.execute(trimmed);
                    logger.info("Database patch " + trimmed + " was executed.");
                }
            }
        }
    }

    public static boolean testDatabaseConnection() {
        try {
            Connection connection = getConnection();
            connection.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates database connection based on the application config file
     * @return
     * @throws SQLException
     */
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dataSourceUrlStatic, dataSourceUserStatic, dataSourcePassStatic);
    }

}
