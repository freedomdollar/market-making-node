package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.yubico.client.v2.YubicoClient;
import com.yubico.client.v2.YubicoResponse;
import com.yubico.client.v2.YubicoResponseStatus;
import com.yubico.client.v2.exceptions.YubicoValidationException;
import com.yubico.client.v2.exceptions.YubicoValidationFailure;
import com.zanable.marketmaking.bot.beans.SetAliasReq;
import com.zanable.marketmaking.bot.beans.TwoFaRegReq;
import com.zanable.marketmaking.bot.beans.TwoFactorData;
import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.beans.zano.AliasDetails;
import com.zanable.marketmaking.bot.enums.TwoFactorType;
import com.zanable.marketmaking.bot.services.DatabaseService;
import com.zanable.marketmaking.bot.services.SettingsService;
import com.zanable.marketmaking.bot.services.ZanoWalletService;
import com.zanable.marketmaking.bot.tools.GoogleAuth;
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

import java.util.ArrayList;
import java.util.List;

@Controller
public class TwoFaEndpoints {

    private String pendingSecretKey = null;

    @RequestMapping(value="/api/get-2fas", produces="application/json", method=RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> setTwoFactorAuths(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();

        ApiResponseBean responseBean = new ApiResponseBean();

        List<TwoFactorData> twoFaList = DatabaseService.get2FaData(null, 1);

        for (TwoFactorData twoFactorData : twoFaList) {
            if (twoFactorData.getType().equals(TwoFactorType.GOOGLEAUTH)) {
                twoFactorData.setData("*****");
            }
        }

        responseBean.setMessage("Secret generated");
        responseBean.setStatus(200);
        responseBean.setPayload(twoFaList);

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }

    @RequestMapping(value="/api/get-pending-google-auth", produces="application/json", method=RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> setAlias(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();

        ApiResponseBean responseBean = new ApiResponseBean();

        if (pendingSecretKey == null) {
            pendingSecretKey = GoogleAuth.generateSecretKey();
        }

        responseBean.setMessage("Secret generated");
        responseBean.setStatus(200);
        responseBean.setPayload(pendingSecretKey);

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }

    @RequestMapping(value="/api/register-2fa", produces="application/json", method= RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> setAlias(HttpServletRequest request, HttpServletResponse response, @RequestBody TwoFaRegReq twoFaRegReq) {
        Gson gson = new Gson();
        System.out.println(gson.toJson(twoFaRegReq));

        ApiResponseBean responseBean = new ApiResponseBean();
        boolean twoFaRegistered = false;

        if (twoFaRegReq.getCode() == null || twoFaRegReq.getCode().isEmpty()) {
            responseBean.setMessage("No 2FA code sent");
            responseBean.setStatus(400);

            return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
        }
        if (twoFaRegReq.getType() == null) {
            responseBean.setMessage("No 2FA type specified");
            responseBean.setStatus(400);

            return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
        }

        if (twoFaRegReq.getType().equals(TwoFactorType.GOOGLEAUTH)) {
            if (pendingSecretKey == null) {
                responseBean.setMessage("Need to generate secret key first");
                responseBean.setStatus(400);
                return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
            }

            String serverCode = GoogleAuth.getTOTPCode(pendingSecretKey);
            if (serverCode.equals(twoFaRegReq.getCode())) {
                DatabaseService.insert2FA(null, TwoFactorType.GOOGLEAUTH, pendingSecretKey, 1);
                pendingSecretKey = null;
                twoFaRegistered = true;
            }
        } else if (twoFaRegReq.getType().equals(TwoFactorType.YUBIKEY)) {
            if (validateYubikey(twoFaRegReq.getCode())) {
                String publicYubikeyId = YubicoClient.getPublicId(twoFaRegReq.getCode());
                DatabaseService.insert2FA(null, twoFaRegReq.getType(), publicYubikeyId, 1);
                twoFaRegistered = true;
            }
        }

        if (twoFaRegistered) {
            responseBean.setMessage(twoFaRegReq.getType().toString() + " two factor was registered");
            responseBean.setStatus(200);
        } else {
            responseBean.setMessage("Two factor wuth could not be registered");
            responseBean.setStatus(403);
        }

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }


    public static boolean validateYubikey(String otp, String yubikeyId) {
        int clientId = SettingsService.getYubicoClientId();
        YubicoClient client = YubicoClient.getClient(clientId);
        YubicoResponse response = null;
        try {
            response = client.verify(otp);
            boolean validOtp = response.getStatus() == YubicoResponseStatus.OK;

            String publicYubikeyId = YubicoClient.getPublicId(otp);
            System.out.println(yubikeyId + " vs " + publicYubikeyId);
            if (validOtp && publicYubikeyId.equals(yubikeyId)) {
                return true;
            }

        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static boolean validateYubikey(String otp) {
        int clientId = SettingsService.getYubicoClientId();
        YubicoClient client = YubicoClient.getClient(clientId);
        YubicoResponse response = null;
        try {
            response = client.verify(otp);
            return response.getStatus() == YubicoResponseStatus.OK;
        } catch (YubicoValidationException e) {
            return false;
        } catch (YubicoValidationFailure e) {
            return false;
        }
    }
}
