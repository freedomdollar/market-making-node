package com.zanable.marketmaking.bot.endpoints;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.WalletTransaction;
import com.zanable.marketmaking.bot.beans.WalletTransactionsResponse;
import com.zanable.marketmaking.bot.services.DatabaseService;
import com.zanable.marketmaking.bot.services.ZanoWalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class WalletTransactions {

    @RequestMapping(value="/api/get-wallet-transactions", produces="application/json", method= RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<WalletTransactionsResponse> getWalletTransactions(HttpServletRequest request, HttpServletResponse response,
                                                                            @RequestParam String wallet, @RequestParam int page, @RequestParam int limit) {
        Gson gson = new Gson();

        WalletTransactionsResponse responseBean = new WalletTransactionsResponse();
        responseBean.setMessage("Error");
        responseBean.setStatus(500);
        long pageSize = limit;

        try {
            List<WalletTransaction> txList = DatabaseService.getWalletTransactions(null, wallet, page, pageSize);
            responseBean.setMessage("Success");
            responseBean.setStatus(200);
            responseBean.setType("walletTransactionList");
            responseBean.setData(txList);
            long totalItems = DatabaseService.getWalletTransactionsItems(null, wallet);
            responseBean.setTotalItems(totalItems);
            responseBean.setTotalPages((totalItems/pageSize)+1);
            responseBean.setCurrentPage(page);
            responseBean.setWalletInfo(ZanoWalletService.getInfo());

            return new ResponseEntity<>(responseBean, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(responseBean, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
