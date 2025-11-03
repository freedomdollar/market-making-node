package com.zanable.marketmaking.bot.services;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.gson.Gson;
import com.zanable.shared.exceptions.NoApiResponseException;
import com.zanable.shared.interfaces.ApplicationService;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ZanoDaemonService implements ApplicationService {

    private final static Logger logger = LoggerFactory.getLogger(ZanoDaemonService.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Gson gson = new Gson();

    private static String zanoRpcAddress = "zanod";
    private static long zanoRpcPort = 11211;

    private static boolean isChainDownloading = false;
    private static boolean isZanoReady = false;

    public ZanoDaemonService() {

    }

    @Override
    public void init() {

        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    checkZanoDaemonStatus();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 5000, TimeUnit.MILLISECONDS);

    }

    @Override
    public void destroy() {
        scheduledExecutorService.shutdown();
    }

    private void checkZanoDaemonStatus() {
        try {
            JSONObject status = getInfo();
            if (status == null) {
                logger.error("Unable to get ZANO daemon status, zanod could be syncing");
                return;
            }
            long networkState = (long) status.get("daemon_network_state");
            long height = (long) status.get("height");
            long maxHeight = (long) status.get("max_net_seen_height");
            double percent = (double) height / (double) maxHeight;
            // logger.info("ZANO daemon is in state " + networkState + " and height " + height + " of " + maxHeight + " (" + percent + "%)");
            if (height > 0 && networkState == 2) {
                isZanoReady = true;
                isChainDownloading = true;
            } else if (height > 0) {
                isChainDownloading = true;
            }
        } catch (Exception e) {
            logger.error("Unable to get ZANO daemon status, zanod could be syncing", e);
        }
    }

    public static boolean isZanoReady() {
        return isZanoReady;
    }

    public static boolean isIsChainDownloading() {
        return isChainDownloading;
    }

    public static JSONObject getInfo() throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "getinfo");
        JSONObject params = new JSONObject();
        params.put("flags", 1);
        payload.put("params", params);
        JSONObject rpcResult = null;
        rpcResult = sendRequest("http://" + zanoRpcAddress + ":" + zanoRpcPort + "/json_rpc", payload);
        if (rpcResult == null) {
            return null;
        }
        if (rpcResult.containsKey("result")) {
            JSONObject result = (JSONObject) rpcResult.get("result");
            return result;
        }

        return null;
    }

    public static JSONObject getHeight() throws NoApiResponseException {
        JSONObject payload = new JSONObject();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 0);
        payload.put("method", "getheight");
        JSONObject params = new JSONObject();
        JSONObject rpcResult = null;
        rpcResult = sendRequest("http://" + zanoRpcAddress + ":" + zanoRpcPort + "/getheight", payload);
        if (rpcResult == null) {
            return null;
        } else {
            return rpcResult;
        }
    }

    private static JSONObject sendRequest(String url, JSONObject payload) throws NoApiResponseException {
        HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
        HttpHeaders headers = new HttpHeaders();
        headers.put("Content-Type", "application/json");
        headers.put("accept", "application/json");

        try {
            HttpResponse httpResponse = requestFactory.buildPostRequest(
                            new GenericUrl(url), ByteArrayContent.fromString("application/json", payload.toJSONString())).setHeaders(headers).setReadTimeout(40000)
                    .execute();
            if (httpResponse.isSuccessStatusCode()) {
                try (InputStream is = httpResponse.getContent(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    String text = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));
                    return (JSONObject) new JSONParser().parse(text);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }
}
