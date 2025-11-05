package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.api.ApiResponseBean;
import com.zanable.marketmaking.bot.services.TelegramService;
import com.zanable.marketmaking.bot.services.ZanoTradeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class BotCommandV2 {

    private final static Logger logger = LoggerFactory.getLogger(BotCommandV2.class);

    @RequestMapping(value="/api/bot/start", produces="application/json", method= RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> startBot(HttpServletRequest request, HttpServletResponse response, @RequestBody String jsonBody) {
        Gson gson = new Gson();

        logger.info("Sending bot trading start command to trade service");
        ZanoTradeService.openTrading();

        ApiResponseBean responseBean = new ApiResponseBean();
        responseBean.setMessage("OK");
        responseBean.setStatus(200);

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }

    @RequestMapping(value="/api/bot/stop", produces="application/json", method=RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> stopBot(HttpServletRequest request, HttpServletResponse response, @RequestBody String jsonBody) {
        Gson gson = new Gson();

        logger.info("Sending bot trading stop command to trade service");
        ZanoTradeService.closeTrading();
        ApiResponseBean responseBean = new ApiResponseBean();
        responseBean.setMessage("OK");
        responseBean.setStatus(200);

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }

    @RequestMapping(value="/api/telegram-test", produces="application/json", method=RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<ApiResponseBean> telegramTest(HttpServletRequest request, HttpServletResponse response) {
        Gson gson = new Gson();

        ApiResponseBean responseBean = new ApiResponseBean();
        responseBean.setMessage("OK");
        responseBean.setStatus(200);
        TelegramService.sendMessageToAllChannels("This is a test");

        return new ResponseEntity<>(responseBean, HttpStatus.OK);
    }
}
