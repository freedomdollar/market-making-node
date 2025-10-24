package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.*;
import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.beans.zano.AliasDetails;
import com.zanable.marketmaking.bot.beans.zano.IntegratedAddress;
import com.zanable.marketmaking.bot.enums.TwoFactorType;
import com.zanable.marketmaking.bot.services.DatabaseService;
import com.zanable.marketmaking.bot.services.ZanoWalletService;
import com.zanable.marketmaking.bot.tools.GoogleAuth;
import com.zanable.shared.beans.SendResponse;
import com.zanable.shared.exceptions.NoApiResponseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.List;

@Controller
public class WalletEndpoints {

    private int failedFa = 0;
    private long blocked2FaUntil = 0;

    @RequestMapping(value="/api/wallet-query-address", produces="application/json", method= RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> queryAddress(HttpServletRequest request, HttpServletResponse response, @RequestBody ZanoAddressQuery addressQuery) {
        Gson gson = new Gson();
        System.out.println(gson.toJson(addressQuery));

        ApiResponseBean responseBean = new ApiResponseBean();

        ZanoAddressQueryResponse zanoAddressQueryResponse = new ZanoAddressQueryResponse();


        if (addressQuery.getAddress().startsWith("@")) {
            try {
                String baseAlias = addressQuery.getAddress().replace("@", "").toLowerCase();
                System.out.println("Querying alias: " + baseAlias);
                AliasDetails aliasDetails = ZanoWalletService.getAliasDetails(baseAlias);
                if (aliasDetails != null) {
                    zanoAddressQueryResponse.setBaseAddress(aliasDetails.getBaseAddress());
                    zanoAddressQueryResponse.setAlias(aliasDetails.getAlias());
                    zanoAddressQueryResponse.setAliasComment(aliasDetails.getComment());
                    zanoAddressQueryResponse.setPaymentId(null);
                } else {
                    responseBean.setMessage("Invalid alias");
                    responseBean.setStatus(400);
                    return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
                }
            } catch (NoApiResponseException e) {
                System.out.println(e.getMessage());
            } catch (Exception e) {
                responseBean.setMessage("Address invalid");
                responseBean.setStatus(400);
                return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
            }
        } else if (addressQuery.getAddress().startsWith("i")) {
            try {
                IntegratedAddress integratedAddress = ZanoWalletService.splitIntegratedAddress(addressQuery.getAddress());
                zanoAddressQueryResponse.setBaseAddress(integratedAddress.getStandardAddress());
                zanoAddressQueryResponse.setPaymentId(integratedAddress.getPaymentId());
            } catch (NoApiResponseException e) {
                System.out.println(e.getMessage());
            } catch (Exception e) {
                responseBean.setMessage("Address invalid");
                responseBean.setStatus(400);
                return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
            }
        } else {
            zanoAddressQueryResponse.setBaseAddress(addressQuery.getAddress());
            if (!ZanoWalletService.validateAddress(addressQuery.getAddress())) {
                responseBean.setMessage("Address invalid");
                responseBean.setStatus(400);
                return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
            }
        }
        zanoAddressQueryResponse.setAddress(addressQuery.getAddress());

        // Check if the address has an alias
        try {
            String alias = ZanoWalletService.getAliasByAddress(zanoAddressQueryResponse.getBaseAddress());
            zanoAddressQueryResponse.setAlias(alias);
            String comment = ZanoWalletService.getAliasCommentByAddress(zanoAddressQueryResponse.getBaseAddress());
            zanoAddressQueryResponse.setAliasComment(comment);
        } catch (Exception e) {
            responseBean.setMessage("Address invalid. " + e.getMessage());
            responseBean.setStatus(400);
            return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
        }

        responseBean.setMessage("Address valid");
        responseBean.setStatus(200);
        responseBean.setPayload(zanoAddressQueryResponse);

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }

    @RequestMapping(value="/api/wallet-send", produces="application/json", method= RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> queryAddress(HttpServletRequest request, HttpServletResponse response, @RequestBody WalletSendRequest sendRequest) {
        Gson gson = new Gson();
        System.out.println(gson.toJson(sendRequest));

        ApiResponseBean responseBean = new ApiResponseBean();

        ZanoAddressQueryResponse zanoAddressQueryResponse = new ZanoAddressQueryResponse();

        if ((System.currentTimeMillis()/1000) < blocked2FaUntil) {
            responseBean.setMessage("Two factor authentication is temporarily blocked due to too many attempts.");
            responseBean.setStatus(405);
            return new ResponseEntity<>(responseBean, HttpStatus.METHOD_NOT_ALLOWED);
        }

        List<TwoFactorData> twoFaList = DatabaseService.get2FaData(null, 1);
        if (twoFaList.isEmpty()) {
            responseBean.setMessage("Two factor authentication not configured. Please setup two factor authentication.");
            responseBean.setStatus(417);
            return new ResponseEntity<>(responseBean, HttpStatus.EXPECTATION_FAILED);
        }

        if (sendRequest.getTwoFa() == null || sendRequest.getTwoFa().isEmpty()) {
            responseBean.setMessage("You need to enter a two fa ctor authentication code to send funds");
            responseBean.setStatus(400);
            return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
        }
        boolean twoFaValid = false;
        for (TwoFactorData twoFactorData : twoFaList) {
            if (sendRequest.getTwoFa().startsWith("ccc") && twoFactorData.getType().equals(TwoFactorType.YUBIKEY)) {
                if (TwoFaEndpoints.validateYubikey(sendRequest.getTwoFa(), twoFactorData.getData())) {
                    twoFaValid = true;
                    failedFa = 0;
                } else {
                    System.out.println("Yubikey validation failed");
                }
            } else {
                if (sendRequest.getTwoFa().equals(GoogleAuth.getTOTPCode(twoFactorData.getData()))) {
                    twoFaValid = true;
                    failedFa = 0;
                }
            }
        }
        if (!twoFaValid && !sendRequest.getTwoFa().startsWith("ccc")) {
            // Google auth is used
            failedFa++;
            if (failedFa > 5) {
                blocked2FaUntil = (System.currentTimeMillis()/1000) + ((System.currentTimeMillis()/1000) % 30);
                System.out.println("Blocking 2FA until " + new Timestamp(blocked2FaUntil).toString());
            }
        }

        if (!twoFaValid) {
            responseBean.setMessage("The 2FA code is wrong");
            responseBean.setStatus(401);
            return new ResponseEntity<>(responseBean, HttpStatus.UNAUTHORIZED);
        }

        int decimals = ZanoWalletService.getAssetBalanceMap("main").get(sendRequest.getAssetId()).getAsset_info().getDecimal_point();
        System.out.println("Decimals: " + decimals);
        BigInteger fullAmount = new BigDecimal(sendRequest.getAmount()).movePointRight(decimals).toBigInteger();
        try {
            SendResponse sendResponse = ZanoWalletService.sendCoins(sendRequest.getAddress(), fullAmount, sendRequest.getAssetId(), sendRequest.getComment(), sendRequest.getPaymentId());
            if (sendResponse.getTxhash() != null) {
                responseBean.setMessage("Funds sent");
                responseBean.setStatus(200);
                responseBean.setPayload(sendResponse.getTxhash());
                return new ResponseEntity<>(responseBean, HttpStatus.OK);
            }
        } catch (NoApiResponseException e) {
            e.printStackTrace();
        }

        responseBean.setMessage("Could not send funds");
        responseBean.setStatus(500);

        return new ResponseEntity<>(responseBean, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @RequestMapping(value="/api/get-wallet-balances", produces="application/json", method= RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> getWalletBalances(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();

        ApiResponseBean responseBean = new ApiResponseBean();
        responseBean.setMessage("OK");
        responseBean.setStatus(200);
        responseBean.setPayload(ZanoWalletService.getWalletsAssetBalanceMap());

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }
}
