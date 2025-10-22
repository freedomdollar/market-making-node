package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.BotDashNew;
import com.zanable.marketmaking.bot.beans.Mexc.AccountAssetResponse;
import com.zanable.marketmaking.bot.beans.Mexc.AccountBalance;
import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.beans.api.ExtendedTradeChain;
import com.zanable.marketmaking.bot.exchangeintegration.MexcSpotClient;
import com.zanable.marketmaking.bot.services.DatabaseService;
import com.zanable.marketmaking.bot.services.SettingsService;
import com.zanable.marketmaking.bot.services.ZanoWalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

@Controller
public class BotDashDataNew {

    private static MexcSpotClient mexcSpotClient = null;
    @Getter
    private static HashMap<String, String> appSettings;

    @RequestMapping(value="/api/bot-data-dash-new", produces="application/json", method= RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<BotDashNew> getBotData(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();
        BotDashNew botDashNew = new BotDashNew();

        try {
            if (mexcSpotClient == null) {
                mexcSpotClient = new MexcSpotClient(SettingsService.getAppSetting("mexc_apikey"), SettingsService.getAppSetting("mexc_apisecret"));
            }
            BigDecimal usdtBalance = BigDecimal.ZERO;
            BigDecimal fusdBalance = BigDecimal.ZERO;
            BigDecimal zanoBalanceMexc = BigDecimal.ZERO;

            AccountAssetResponse accountAssetResponse = mexcSpotClient.getBalanceForAsset();
            System.out.println(gson.toJson(accountAssetResponse));
            if (accountAssetResponse.getBalances() != null) {
                for (AccountBalance balance : accountAssetResponse.getBalances()) {
                    System.out.println(balance.getAsset() + ": " + balance.getAvailable());
                    if (balance.getAsset().equals("FUSD")) {
                        fusdBalance = balance.getAvailable();
                    } else if (balance.getAsset().equals("USDT")) {
                        usdtBalance = balance.getAvailable();
                    } else if (balance.getAsset().equals("ZANO")) {
                        zanoBalanceMexc = balance.getAvailable();
                    }
                }
            }

            BigDecimal fusdBalanceWallet = BigDecimal.ZERO;
            if (ZanoWalletService.getFloatTokensDataMain() != null) {
                fusdBalanceWallet = new BigDecimal(ZanoWalletService.getFloatTokensDataMain().getUnlocked()).movePointLeft(4);
            }

            BigDecimal zanoBalanceWallet = BigDecimal.ZERO;
            if (ZanoWalletService.getZanoDataMain() != null) {
                zanoBalanceWallet = new BigDecimal(ZanoWalletService.getZanoDataMain().getUnlocked()).movePointLeft(12);
            }


            botDashNew.setMainWalletFusdBalance(fusdBalanceWallet);
            botDashNew.setMainWalletZanoBalance(zanoBalanceWallet);

            botDashNew.setMexcUsdtBalance(usdtBalance);
            botDashNew.setMexcFusdBalance(fusdBalance);
            botDashNew.setMexcZanoBalance(zanoBalanceMexc);

            botDashNew.setStatus(200);
            botDashNew.setMessage("OK");
            return new ResponseEntity<BotDashNew>(botDashNew, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<BotDashNew>(botDashNew, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @RequestMapping(value="/api/extended-trade-data", produces="application/json", method= RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> getExtendedTradeData(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();
        ApiResponseBean responseBean = new ApiResponseBean();

        try {
            List<ExtendedTradeChain> tradeList = DatabaseService.getLastTradesExtended(20);
            responseBean.setPayload(tradeList);
            responseBean.setMessage("OK");
            responseBean.setStatus(200);

            return new ResponseEntity<ApiResponseBean>(responseBean, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<ApiResponseBean>(responseBean, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @RequestMapping(value="/api/extended-trade-data-buy", produces="application/json", method= RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> getExtendedTradeDataBuy(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();
        ApiResponseBean responseBean = new ApiResponseBean();

        try {
            List<ExtendedTradeChain> tradeList = DatabaseService.getLastTradesExtendedBuy(20);
            responseBean.setPayload(tradeList);
            responseBean.setMessage("OK");
            responseBean.setStatus(200);

            return new ResponseEntity<ApiResponseBean>(responseBean, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<ApiResponseBean>(responseBean, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

}
