package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.PnlSummaryBean;
import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.beans.api.ExtendedTradeChain;
import com.zanable.marketmaking.bot.services.DatabaseService;
import com.zanable.marketmaking.bot.tools.PnlCalculator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

@Controller
public class PnlEndpointData {
    Gson gson = new Gson();

    @RequestMapping(value="/api/pnl-data", produces="application/json", method= RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> getExtendedTradeDataBuy(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();
        ApiResponseBean responseBean = new ApiResponseBean();

        try {
            List<ExtendedTradeChain> tradeListSell = DatabaseService.getLastTradesExtended(0);
            List<ExtendedTradeChain> tradeListBuy = DatabaseService.getLastTradesExtendedBuy(0);

            List<ExtendedTradeChain> tradeList = new ArrayList<>();
            tradeList.addAll(tradeListSell);
            tradeList.addAll(tradeListBuy);

            PnlCalculator.PnlSummary pnlSummarySell = PnlCalculator.compute(tradeList);
            // PnlCalculator.PnlSummary pnlSummaryBuy = PnlCalculator.compute(tradeListBuy);

            PnlSummaryBean summaryBean = new PnlSummaryBean();
            summaryBean.setSellPnlSummary(pnlSummarySell);;

            responseBean.setPayload(pnlSummarySell);
            responseBean.setMessage("OK");
            responseBean.setStatus(200);

            return new ResponseEntity<ApiResponseBean>(responseBean, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<ApiResponseBean>(responseBean, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
