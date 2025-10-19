package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.nimbusds.jwt.SignedJWT;
import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.beans.zano.BotSwapResult;
import com.zanable.marketmaking.bot.services.ZanoPriceService;
import com.zanable.marketmaking.bot.services.ZanoTradeService;
import com.zanable.shared.exceptions.NoApiResponseException;
import com.zanable.shared.services.SigningEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;

@Controller
public class SendBotCommand {
    @RequestMapping(value="/api/send-bot-command", produces="application/json", method= RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> getBotData(HttpServletRequest request, HttpServletResponse response, @RequestBody String jwtBody) {
        Gson gson = new Gson();

        ApiResponseBean responseBean = new ApiResponseBean();
        responseBean.setMessage("Error");
        responseBean.setStatus(500);

        try {
            SignedJWT signedJWT = SignedJWT.parse(jwtBody);
            if (!SigningEngine.validateJwt(signedJWT)) {
                responseBean.setMessage("Invalid JWT");
                responseBean.setStatus(403);
                return new ResponseEntity<>(responseBean, HttpStatus.BAD_REQUEST);
            }
            System.out.println(signedJWT.getJWTClaimsSet().toJSONObject());

            if (signedJWT.getJWTClaimsSet().getClaimAsString("command").equals("halttrading")) {
                ZanoTradeService.closeTrading();
            } else if (signedJWT.getJWTClaimsSet().getClaimAsString("command").equals("resumetrading")) {
                ZanoTradeService.openTrading();
            } else if (signedJWT.getJWTClaimsSet().getClaimAsString("command").equals("updateSettings")) {
                ZanoPriceService.updateAppSettings();
            } else if (signedJWT.getJWTClaimsSet().getClaimAsString("command").equals("directswap")) {
                System.out.println("Got a direct swap");
                responseBean.setMessage("Error");
                responseBean.setStatus(500);
                BotSwapResult swapResult = new BotSwapResult();
                try {
                    BigDecimal amount = new BigDecimal(signedJWT.getJWTClaimsSet().getClaimAsString("amount"));
                    swapResult = ZanoTradeService.processDirectSwap(
                            signedJWT.getJWTClaimsSet().getLongClaim("orderid"),
                            signedJWT.getJWTClaimsSet().getClaimAsString("swapdata"),
                            amount);
                    if (swapResult.isAccepted()) {
                        responseBean.setStatus(200);
                        responseBean.setMessage(swapResult.getTxid());
                    } else {
                        if (swapResult.getTxid().equals("449")) {
                            responseBean.setStatus(449);
                        } else {
                            responseBean.setStatus(403);
                        }
                        responseBean.setMessage(swapResult.getReason());
                    }
                } catch (NoApiResponseException e) {
                    swapResult.setReason("Unable to decode swap in wallet");
                    responseBean.setStatus(403);
                    responseBean.setMessage(swapResult.getReason());
                } catch (IOException e) {
                    swapResult.setReason("Other error");
                    responseBean.setStatus(500);
                    responseBean.setMessage(swapResult.getReason());
                }
            }

            if (!signedJWT.getJWTClaimsSet().getClaimAsString("command").equals("directswap")) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            return new ResponseEntity<>(responseBean, HttpStatus.OK);
        } catch (ParseException e) {
            return new ResponseEntity<>(responseBean, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
