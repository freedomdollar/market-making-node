package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.SetAliasReq;
import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.exceptions.ZanoWalletException;
import com.zanable.marketmaking.bot.services.DatabaseService;
import com.zanable.marketmaking.bot.services.SettingsService;
import com.zanable.marketmaking.bot.services.ZanoWalletService;
import com.zanable.shared.exceptions.NoApiResponseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SetAlias {

    @RequestMapping(value="/api/set-alias", produces="application/json", method= RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> setAlias(HttpServletRequest request, HttpServletResponse response, @RequestBody SetAliasReq SetAliasReq) {
        Gson gson = new Gson();
        System.out.println(gson.toJson(SetAliasReq));

        ApiResponseBean responseBean = new ApiResponseBean();

        if (SetAliasReq.getAlias() == null || SetAliasReq.getAlias().length() <= 5) {
            responseBean.setMessage("Alias needs to be more than 5 characters long");
            responseBean.setStatus(400);
            return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
        }

        try {
            if (SettingsService.getAppSettingSafe("pending_alias_tx") == null) {
                String txId = ZanoWalletService.setAlias(SetAliasReq.getAlias().toLowerCase(), "");
                if (txId != null && !txId.isEmpty()) {
                    DatabaseService.upsertAppSetting("pending_alias_tx", txId);
                    SettingsService.updateAppSettings();
                }
            } else {
                responseBean.setMessage("Alias already waiting to be confirmed");
                responseBean.setStatus(400);
                return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            if (e.getMessage() != null) {
                responseBean.setMessage(e.getMessage());
            }
            return new ResponseEntity<>(responseBean, HttpStatus.SERVICE_UNAVAILABLE);
        }

        responseBean.setMessage("Alias registered");
        responseBean.setStatus(200);

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }
}
