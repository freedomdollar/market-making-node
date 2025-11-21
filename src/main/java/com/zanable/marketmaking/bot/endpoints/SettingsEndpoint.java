package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.SetAliasReq;
import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.beans.api.AppSettingsBean;
import com.zanable.marketmaking.bot.services.SettingsService;
import com.zanable.marketmaking.bot.services.ZanoTradeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;

@Controller
public class SettingsEndpoint {

    @RequestMapping(value="/api/app-settings", produces="application/json", method= RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> getAppSettings(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();
        ApiResponseBean responseBean = new ApiResponseBean();

        try {
            AppSettingsBean appSettingsBean = AppSettingsBean.builder().build();

            if (SettingsService.getAppSettingSafe("ask_multiplier") != null) {
                appSettingsBean.setDexAskMultiplier(new BigDecimal(SettingsService.getAppSettingSafe("ask_multiplier")));
            }
            if (SettingsService.getAppSettingSafe("bid_multiplier") != null) {
                appSettingsBean.setDexBidMultiplier(new BigDecimal(SettingsService.getAppSettingSafe("bid_multiplier")));
            }
            if (SettingsService.getAppSettingSafe("zano_sell_volume_multiplier") != null) {
                appSettingsBean.setZanoSellPercent(new BigDecimal(SettingsService.getAppSettingSafe("zano_sell_volume_multiplier")).movePointRight(2));
            }
            if (SettingsService.getAppSettingSafe("zano_sell_price_multiplier") != null) {
                appSettingsBean.setZanoSellPriceMultiplier(new BigDecimal(SettingsService.getAppSettingSafe("zano_sell_price_multiplier")));
            }
            if (SettingsService.getAppSettingSafe("minimum_token_volume_sell") != null) {
                appSettingsBean.setDexMinimumTokenSellVolume(new BigDecimal(SettingsService.getAppSettingSafe("minimum_token_volume_sell")));
            }
            if (SettingsService.getAppSettingSafe("minimum_token_volume_buy") != null) {
                appSettingsBean.setDexMinimumTokenBuyVolume(new BigDecimal(SettingsService.getAppSettingSafe("minimum_token_volume_buy")));
            }
            if (SettingsService.getAppSettingSafe("maximum_token_volume_buy") != null) {
                appSettingsBean.setDexMaximumTokenBuyVolume(new BigDecimal(SettingsService.getAppSettingSafe("maximum_token_volume_buy")));
            }
            if (SettingsService.getAppSettingSafe("maximum_token_volume_sell") != null) {
                appSettingsBean.setDexMaximumTokenSellVolume(new BigDecimal(SettingsService.getAppSettingSafe("maximum_token_volume_sell")));
            }
            if (SettingsService.getAppSettingSafe("price_movement_threshold") != null) {
                appSettingsBean.setDexPriceMoveThreshold(new BigDecimal(SettingsService.getAppSettingSafe("price_movement_threshold")));
            }

            // ----------

            if (SettingsService.getAppSettingSafe("enable_zano_sell") != null) {
                if (SettingsService.getAppSettingSafe("enable_zano_sell").equals("1")) {
                    appSettingsBean.setZanoSellEnabled(true);
                }
            }
            if (SettingsService.getAppSettingSafe("enable_fusd_buy") != null) {
                if (SettingsService.getAppSettingSafe("enable_fusd_buy").equals("1")) {
                    appSettingsBean.setFusdBuyEnabled(true);
                }
            }
            if (SettingsService.getAppSettingSafe("enable_zano_buy") != null) {
                if (SettingsService.getAppSettingSafe("enable_zano_buy").equals("1")) {
                    appSettingsBean.setZanoBuyEnabled(true);
                }
            }
            if (SettingsService.getAppSettingSafe("enable_fusd_sell") != null) {
                if (SettingsService.getAppSettingSafe("enable_fusd_sell").equals("1")) {
                    appSettingsBean.setFusdSellEnabled(true);
                }
            }

            if (SettingsService.getAppSettingSafe("enable_move_to_cex") != null) {
                if (SettingsService.getAppSettingSafe("enable_move_to_cex").equals("1")) {
                    appSettingsBean.setZanoMoveFromWalletEnabled(true);
                }
            }
            if (SettingsService.getAppSettingSafe("enable_move_to_wallet") != null) {
                if (SettingsService.getAppSettingSafe("enable_move_to_wallet").equals("1")) {
                    appSettingsBean.setMexcFusdWithdrawEnabled(true);
                }
            }
            if (SettingsService.getAppSettingSafe("enable_autostart_dex_bot") != null) {
                if (SettingsService.getAppSettingSafe("enable_autostart_dex_bot").equals("1")) {
                    appSettingsBean.setAutoStartDexTradeBot(true);
                }
            }

            // *****
            if (SettingsService.getAppSettingSafe("zano_move_to_cex_threshold") != null) {
                appSettingsBean.setZanoMoveToCexThreshold(new BigDecimal(SettingsService.getAppSettingSafe("zano_move_to_cex_threshold")));
            }
            if (SettingsService.getAppSettingSafe("zano_move_to_cex_min_trans") != null) {
                appSettingsBean.setZanoMoveToCexMinTransfer(new BigDecimal(SettingsService.getAppSettingSafe("zano_move_to_cex_min_trans")));
            }
            if (SettingsService.getAppSettingSafe("fusd_move_to_wallet_threshold") != null) {
                appSettingsBean.setFusdMoveToWalletTheshold(new BigDecimal(SettingsService.getAppSettingSafe("fusd_move_to_wallet_threshold")));
            }
            if (SettingsService.getAppSettingSafe("fusd_move_to_wallet_min_trans") != null) {
                appSettingsBean.setFusdMoveToWalletMinTransfer(new BigDecimal(SettingsService.getAppSettingSafe("fusd_move_to_wallet_min_trans")));
            }


            if (SettingsService.getAppSettingSafe("mexc_deposit_address_zano") != null) {
                appSettingsBean.setMexcDepositAddressZano(SettingsService.getAppSettingSafe("mexc_deposit_address_zano"));
            }
            if (SettingsService.getAppSettingSafe("mexc_deposit_address_fusd") != null) {
                appSettingsBean.setMexcDepositAddressFusd(SettingsService.getAppSettingSafe("mexc_deposit_address_fusd"));
            }
            if (SettingsService.getAppSettingSafe("mexc_apikey") != null) {
                appSettingsBean.setMexcApiKey(SettingsService.getAppSettingSafe("mexc_apikey"));
            }
            if (SettingsService.getAppSettingSafe("mexc_apisecret") != null) {
                appSettingsBean.setMexcApiSecret("********");
            }

            if (SettingsService.getAppSettingSafe("telegram_bot_api_key") != null) {
                appSettingsBean.setTelegramBotToken(SettingsService.getAppSettingSafe("telegram_bot_api_key"));
            }

            responseBean.setMessage("OK");
            responseBean.setStatus(200);
            responseBean.setPayload(appSettingsBean);

            return new ResponseEntity<ApiResponseBean>(responseBean, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<ApiResponseBean>(responseBean, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @RequestMapping(value="/api/app-settings", produces="application/json", method= RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> postAppSettings(HttpServletRequest request, HttpServletResponse response, @RequestBody AppSettingsBean appSettingsBean) {
        ApiResponseBean responseBean = new ApiResponseBean();

        // 1) DEX pricing
        if (appSettingsBean.getDexAskMultiplier() != null)
            SettingsService.saveAppSetting("ask_multiplier", appSettingsBean.getDexAskMultiplier().toPlainString());
        if (appSettingsBean.getDexBidMultiplier() != null)
            SettingsService.saveAppSetting("bid_multiplier", appSettingsBean.getDexBidMultiplier().toPlainString());
        if (appSettingsBean.getDexPriceMoveThreshold() != null)
            SettingsService.saveAppSetting("price_movement_threshold", appSettingsBean.getDexPriceMoveThreshold().toPlainString());

        // 2) Volumes
        if (appSettingsBean.getDexMinimumTokenSellVolume() != null)
            SettingsService.saveAppSetting("minimum_token_volume_sell", appSettingsBean.getDexMinimumTokenSellVolume().toPlainString());
        if (appSettingsBean.getDexMinimumTokenBuyVolume() != null)
            SettingsService.saveAppSetting("minimum_token_volume_buy", appSettingsBean.getDexMinimumTokenBuyVolume().toPlainString());
        if (appSettingsBean.getDexMaximumTokenBuyVolume() != null)
            SettingsService.saveAppSetting("maximum_token_volume_buy", appSettingsBean.getDexMaximumTokenBuyVolume().toPlainString());
        if (appSettingsBean.getDexMaximumTokenSellVolume() != null)
            SettingsService.saveAppSetting("maximum_token_volume_sell", appSettingsBean.getDexMaximumTokenSellVolume().toPlainString());

        // 3) ZANO sell config
        if (appSettingsBean.getZanoSellPercent() != null)
            SettingsService.saveAppSetting("zano_sell_volume_multiplier", appSettingsBean.getZanoSellPercent().movePointLeft(2).toPlainString());
        if (appSettingsBean.getZanoSellPriceMultiplier() != null)
            SettingsService.saveAppSetting("zano_sell_price_multiplier", appSettingsBean.getZanoSellPriceMultiplier().toPlainString());

        // 4) Boolean toggles ("1"/"0")
        SettingsService.saveAppSetting("enable_zano_sell", appSettingsBean.isZanoSellEnabled() ? "1" : "0");
        SettingsService.saveAppSetting("enable_fusd_buy", appSettingsBean.isFusdBuyEnabled() ? "1" : "0");
        SettingsService.saveAppSetting("enable_fusd_sell", appSettingsBean.isZanoSellEnabled() ? "1" : "0");
        SettingsService.saveAppSetting("enable_zano_buy", appSettingsBean.isZanoBuyEnabled() ? "1" : "0");
        SettingsService.saveAppSetting("enable_move_to_cex", appSettingsBean.isZanoMoveFromWalletEnabled() ? "1" : "0");
        SettingsService.saveAppSetting("enable_move_to_wallet", appSettingsBean.isMexcFusdWithdrawEnabled() ? "1" : "0");
        SettingsService.saveAppSetting("enable_autostart_dex_bot", appSettingsBean.isAutoStartDexTradeBot() ? "1" : "0");

        // 5) Transfer thresholds / minima
        if (appSettingsBean.getZanoMoveToCexThreshold() != null)
            SettingsService.saveAppSetting("zano_move_to_cex_threshold", appSettingsBean.getZanoMoveToCexThreshold().toPlainString());
        if (appSettingsBean.getZanoMoveToCexMinTransfer() != null)
            SettingsService.saveAppSetting("zano_move_to_cex_min_trans", appSettingsBean.getZanoMoveToCexMinTransfer().toPlainString());
        if (appSettingsBean.getFusdMoveToWalletTheshold() != null)
            SettingsService.saveAppSetting("fusd_move_to_wallet_threshold", appSettingsBean.getFusdMoveToWalletTheshold().toPlainString());
        if (appSettingsBean.getFusdMoveToWalletMinTransfer() != null)
            SettingsService.saveAppSetting("fusd_move_to_wallet_min_trans", appSettingsBean.getFusdMoveToWalletMinTransfer().toPlainString());

        // 6) CEX API & deposit addresses
        if (appSettingsBean.getMexcDepositAddressZano() != null)
            SettingsService.saveAppSetting("mexc_deposit_address_zano", appSettingsBean.getMexcDepositAddressZano().trim());
        if (appSettingsBean.getMexcDepositAddressFusd() != null)
            SettingsService.saveAppSetting("mexc_deposit_address_fusd", appSettingsBean.getMexcDepositAddressFusd().trim());
        if (appSettingsBean.getMexcApiKey() != null)
            SettingsService.saveAppSetting("mexc_apikey", appSettingsBean.getMexcApiKey().trim());
        if (appSettingsBean.getTelegramBotToken() != null)
            SettingsService.saveAppSetting("telegram_bot_api_key", appSettingsBean.getTelegramBotToken().trim());

        if (appSettingsBean.getMexcApiSecret() != null) {
            String sec = appSettingsBean.getMexcApiSecret().trim();
            // getAppSettings masks this as "********"; avoid overwriting when client sends the mask back unchanged
            if (!sec.isEmpty() && !"********".equals(sec)) {
                SettingsService.saveAppSetting("mexc_apisecret", sec);
            }
        }

        SettingsService.updateAppSettings();
        ZanoTradeService.updateAppSettings();

        responseBean.setStatus(200);
        responseBean.setMessage("OK");

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }
}
