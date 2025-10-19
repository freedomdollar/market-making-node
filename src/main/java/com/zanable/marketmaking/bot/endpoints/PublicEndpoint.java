package com.zanable.marketmaking.bot.endpoints;

import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.beans.market.api.PublicStatusResponse;
import com.zanable.marketmaking.bot.services.ZanoDaemonService;
import com.zanable.marketmaking.bot.services.ZanoTradeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class PublicEndpoint {
    @RequestMapping(value="/api/get-public-status", produces={"application/json"}, method={RequestMethod.GET})
    @ResponseBody
    public ResponseEntity<PublicStatusResponse> getMmStatus() {
        try {

            PublicStatusResponse statusResponse = new PublicStatusResponse();
            statusResponse.setStatus(200);
            statusResponse.setMsg("OK");
            statusResponse.setActiveOrders(ZanoTradeService.getActiveUserOrders());

            return new ResponseEntity<PublicStatusResponse>(statusResponse, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<PublicStatusResponse>(new PublicStatusResponse(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @RequestMapping(value="/api/get-daemon-status", produces={"application/json"}, method={RequestMethod.GET})
    @ResponseBody
    public ResponseEntity<ApiResponseBean> getDaemonStatus() {
        try {

            ApiResponseBean statusResponse = new ApiResponseBean();
            statusResponse.setStatus(200);
            statusResponse.setMessage("OK");
            statusResponse.setPayload(ZanoDaemonService.getInfo());

            return new ResponseEntity<ApiResponseBean>(statusResponse, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<ApiResponseBean>(new ApiResponseBean(), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
