package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.ApplicationStartup;
import com.zanable.marketmaking.bot.beans.market.OrderType;
import com.zanable.marketmaking.bot.beans.market.api.MmStatusResponse;
import com.zanable.marketmaking.bot.beans.market.api.OrderBookResponse;
import com.zanable.marketmaking.bot.services.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Controller
public class StatusEndpoint {
    private Gson gson = new Gson();

    @RequestMapping(value="/api/get-order-book", produces={"application/json"}, method={RequestMethod.GET})
    @ResponseBody
    public ResponseEntity<OrderBookResponse> getOrderBook2() {
        try {

            OrderBookResponse orderBookResponse = new OrderBookResponse();
            orderBookResponse.setAsks(OrderBookAggregatorService.getAsksAggregated());
            orderBookResponse.setLowestAsk(ZanoPriceService.getLowestAsk());
            orderBookResponse.setWeightedAsk(ZanoPriceService.getWeightedAsk());
            orderBookResponse.setAskVolume(ZanoPriceService.getAskVolume());
            orderBookResponse.setLastUsedAsk(ZanoPriceService.getLastWeightedAsk());
            orderBookResponse.setLastUsedAskVolume(ZanoPriceService.getLastAskVolume());
            orderBookResponse.setAskVolumeAverage(ZanoPriceService.getAverageAskVolume());

            orderBookResponse.setBids(OrderBookAggregatorService.getBidsAggregated());
            orderBookResponse.setHighestBid(ZanoPriceService.getHighestBid());
            orderBookResponse.setWeightedBid(ZanoPriceService.getWeightedBid());
            orderBookResponse.setBidVolume(ZanoPriceService.getBidVolume());
            orderBookResponse.setLastUsedBid(ZanoPriceService.getLastWeightedBid());
            orderBookResponse.setLastUsedBidVolume(ZanoPriceService.getLastBidVolume());
            orderBookResponse.setBidVolumeAverage(ZanoPriceService.getAverageBidVolume());

            orderBookResponse.setLastUpdate(OrderBookAggregatorService.getLastUpdate());

            orderBookResponse.setStatus(200);
            orderBookResponse.setMsg("OK");

            return new ResponseEntity<OrderBookResponse>(orderBookResponse, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<OrderBookResponse>(new OrderBookResponse(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @RequestMapping(value="/api/get-mm-status", produces={"application/json"}, method={RequestMethod.GET})
    @ResponseBody
    public ResponseEntity<MmStatusResponse> getMmStatus() {
        try {

            MmStatusResponse statusResponse = new MmStatusResponse();
            statusResponse.setStatus(200);
            statusResponse.setMsg("OK");
            statusResponse.setActiveOrders(ZanoTradeService.getActiveUserOrders());
            statusResponse.setFloatTokenAssetId((String) SettingsService.getAppSetting("trade_token_asset_id"));
            statusResponse.setWalletAlias(ZanoWalletService.getWalletAlias());

            statusResponse.setCurrentAskVolume(ZanoPriceService.getDexAskVolumeInTokens());
            statusResponse.setAverageAskVolume(ZanoPriceService.getAverageAskVolume());
            statusResponse.setCurrentBidVolume(ZanoPriceService.getDexBidVolumeInZano());
            statusResponse.setAverageBidVolume(ZanoPriceService.getAverageBidVolume());

            statusResponse.setCurrentAskPrice(ZanoPriceService.getDexAskPriceInZano());
            statusResponse.setCurrentBidPrice(ZanoPriceService.getDexBidPriceInZano());

            if (OrderBookAggregatorService.getAsksAggregated() != null && !OrderBookAggregatorService.getAsksAggregated().isEmpty()) {
                statusResponse.setCurrentZanoPriceAskWeighted(OrderBookAggregatorService.getAsksAggregated().getFirst().getPrice());
            }
            if (OrderBookAggregatorService.getBidsAggregated() != null && !OrderBookAggregatorService.getBidsAggregated().isEmpty()) {
                statusResponse.setCurrentZanoPriceBidWeighted(OrderBookAggregatorService.getBidsAggregated().getFirst().getPrice());
            }

            if (ZanoPriceService.getLastCoinex() != null) {
                statusResponse.setLastCoinexActivityTimestamp(ZanoPriceService.getLastCoinex().getTime());
            }
            if (ZanoPriceService.getLastMexc() != null) {
                statusResponse.setLastMexcActivityTimestamp(ZanoPriceService.getLastMexc().getTime());
            }

            statusResponse.setLast20trades(DatabaseService.getLastTrades());
            statusResponse.setBuySellStats(DatabaseService.getTokensBuySellStats());

            statusResponse.setWalletsAssetBalanceMap(ZanoWalletService.getWalletsAssetBalanceMap());
            statusResponse.setTradingOpen(ZanoTradeService.isTradingOpen());

            BigDecimal issuedTokens = BigDecimal.ZERO;
            if (ZanoWalletService.getFloatTokensDataMain() != null) {
                issuedTokens = new BigDecimal(ZanoWalletService.getFloatTokensDataMain().getAsset_info().getCurrent_supply()).
                        movePointLeft(ZanoWalletService.getFloatTokensDataMain().getAsset_info().getDecimal_point());
            }
            statusResponse.setFloatTokensIssued(issuedTokens);
            BigDecimal marketMakerWalletBalanceZano = BigDecimal.ZERO;
            if (ZanoWalletService.getZanoDataMain() != null) {
                marketMakerWalletBalanceZano = new BigDecimal(ZanoWalletService.getZanoDataMain().getTotal()).
                        movePointLeft(ZanoWalletService.getZanoDataMain().getAsset_info().getDecimal_point());

            }
            statusResponse.setZanoInMarketMakerWallet(marketMakerWalletBalanceZano);

            BigDecimal marketMakerWalletBalanceFloat = BigDecimal.ZERO;
            if (ZanoWalletService.getFloatTokensDataMain() != null) {
                marketMakerWalletBalanceFloat = new BigDecimal(ZanoWalletService.getFloatTokensDataMain().getTotal()).
                        movePointLeft(ZanoWalletService.getFloatTokensDataMain().getAsset_info().getDecimal_point());
            }

            statusResponse.setFloatTokensInMarketMakerWallet(marketMakerWalletBalanceFloat);

            // Don't forget tokens in audit wallet
            BigDecimal tokensInCirculation = issuedTokens.subtract(marketMakerWalletBalanceFloat);
            statusResponse.setFloatTokensInCirculation(issuedTokens.subtract(marketMakerWalletBalanceFloat));

            BigDecimal auditWalletBalanceZano = BigDecimal.ZERO;
            try {
                auditWalletBalanceZano = new BigDecimal(ZanoWalletService.getZanoDataAudit().getTotal()).
                        movePointLeft(ZanoWalletService.getZanoDataAudit().getAsset_info().getDecimal_point());
            } catch (NullPointerException e) {
                // Do nothing
            }
            statusResponse.setZanoInColleteralWallet(auditWalletBalanceZano);

            BigDecimal zanoPriceMid = BigDecimal.ZERO;
            if (ZanoPriceService.getHighestBid() != null && ZanoPriceService.getLowestAsk() != null) {
                zanoPriceMid = ZanoPriceService.getHighestBid().add(ZanoPriceService.getLowestAsk()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            }

            BigDecimal collateralValue = auditWalletBalanceZano.multiply(zanoPriceMid);
            statusResponse.setZanoInColleteralWalletValue(collateralValue);

            statusResponse.setAppSettings(ZanoPriceService.getAppSettings());

            BigDecimal averageZanoPrice = BigDecimal.ZERO;
            BigDecimal averageZanoPriceBid = BigDecimal.ZERO;
            BigDecimal averageZanoPriceAsk = BigDecimal.ZERO;

            // Bids
            for (BigDecimal price : statusResponse.getCurrentZanoExchangeBids().values()) {
                averageZanoPriceBid = averageZanoPriceBid.add(price);
            }
            if (!statusResponse.getCurrentZanoExchangeBids().isEmpty()) {
                averageZanoPriceBid = averageZanoPriceBid.divide(BigDecimal.valueOf(statusResponse.getCurrentZanoExchangeBids().size()), 6, RoundingMode.DOWN);
            }

            // Asks
            for (BigDecimal price : statusResponse.getCurrentZanoExchangeAsks().values()) {
                averageZanoPriceAsk = averageZanoPriceAsk.add(price);
            }
            if (!statusResponse.getCurrentZanoExchangeAsks().isEmpty()) {
                averageZanoPriceAsk = averageZanoPriceAsk.divide(BigDecimal.valueOf(statusResponse.getCurrentZanoExchangeAsks().size()), 6, RoundingMode.DOWN);
            }

            statusResponse.setCurrentZanoPriceAverage(zanoPriceMid);

            if (tokensInCirculation.compareTo(BigDecimal.ZERO) != 0) {
                statusResponse.setFloatTokensCoverageRatio(collateralValue.divide(tokensInCirculation, 12, RoundingMode.DOWN));
            } else {
                statusResponse.setFloatTokensCoverageRatio(null);
            }

            statusResponse.setPairId(ZanoTradeService.getPairId());

            return new ResponseEntity<MmStatusResponse>(statusResponse, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<MmStatusResponse>(new MmStatusResponse(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
