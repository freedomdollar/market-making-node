package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.ApplicationStartup;
import com.zanable.marketmaking.bot.beans.GitHubRelease;
import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.beans.api.ApplicationStatusBean;
import com.zanable.marketmaking.bot.services.SettingsService;
import com.zanable.marketmaking.bot.services.ZanoDaemonService;
import com.zanable.marketmaking.bot.services.ZanoTradeService;
import com.zanable.marketmaking.bot.services.ZanoWalletService;
import com.zanable.marketmaking.bot.tools.VersionUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Controller
public class ApplicationStatus {

    @RequestMapping(value="/api/application-status", produces={"application/json"}, method={RequestMethod.GET})
    @ResponseBody
    public ResponseEntity<ApiResponseBean> getAppStatus() {
        try {

            ApiResponseBean statusResponse = new ApiResponseBean();
            statusResponse.setStatus(200);
            statusResponse.setMessage("OK");

            ApplicationStatusBean applicationStatusBean = new ApplicationStatusBean();

            if (ApplicationStartup.isServicesStarted()) {
                applicationStatusBean.setAppStarted(true);
            }

            if (ZanoDaemonService.isIsChainDownloading()) {
                applicationStatusBean.setZanoDaemonActive(true);
            }

            if (ApplicationStartup.isCexTradeServiceStarted()) {
                applicationStatusBean.setZanoCexTradingServiceActive(true);
            }

            if (ZanoWalletService.getWalletAlias() != null) {
                applicationStatusBean.setWalletHasAlias(true);
            }

            if (SettingsService.getAppSettingSafe("pending_alias_tx") != null) {
                applicationStatusBean.setWalletHasPendingAlias(true);
            } else {
                applicationStatusBean.setWalletHasPendingAlias(false);
            }

            if (ApplicationStartup.isZanoWalletStarted()) {
                applicationStatusBean.setZanoWalletServiceActive(true);
            }

            if (ApplicationStartup.isDexTradeServiceStarted()) {
                applicationStatusBean.setZanoDexTradingServiceActive(true);
            }

            if (ZanoTradeService.isTradingOpen()) {
                applicationStatusBean.setZanoDexTradingBotActive(true);
            }
            if (ApplicationStartup.isTelegramServiceStarted()) {
                applicationStatusBean.setTelegramServiceActive(true);
            }

            applicationStatusBean.setAppVersion(VersionUtil.getVersion());
            applicationStatusBean.setLatestReleaseVersion(ApplicationStartup.getLatestReleaseVersion());
            applicationStatusBean.setUpdateAvailable(ApplicationStartup.isUpdateAvailable());

            statusResponse.setPayload(applicationStatusBean);

            return new ResponseEntity<ApiResponseBean>(statusResponse, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<ApiResponseBean>(new ApiResponseBean(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
