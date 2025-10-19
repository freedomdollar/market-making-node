package com.zanable.marketmaking.bot.tools;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.zanable.shared.exceptions.NoApiResponseException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class BackendRequestUtil {
    public static String sendBackendRequestGET(String url) throws NoApiResponseException {
        HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
        HttpHeaders headers = new HttpHeaders();
        headers.put("accept", "application/json");

        try {
            HttpResponse httpResponse = requestFactory.buildGetRequest(
                            new GenericUrl(url)).setHeaders(headers).setReadTimeout(40000)
                    .execute();
            if (httpResponse.isSuccessStatusCode()) {
                try (InputStream is = httpResponse.getContent(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    String text = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));
                    return text;
                } catch (Exception e) {
                    throw new NoApiResponseException();
                }
            }
        } catch (IOException e) {
            throw new NoApiResponseException();
        }
        throw new NoApiResponseException();
    }

    public static String sendBackendRequestPost(String url, String rawBody) throws NoApiResponseException {
        HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
        HttpHeaders headers = new HttpHeaders();
        headers.put("accept", "application/json");

        try {
            HttpResponse httpResponse = requestFactory.buildPostRequest(
                            new GenericUrl(url), ByteArrayContent.fromString("text/plain", rawBody)).setHeaders(headers).setReadTimeout(40000)
                    .execute();
            if (httpResponse.isSuccessStatusCode()) {
                try (InputStream is = httpResponse.getContent(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    String text = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));
                    return text;
                } catch (Exception e) {
                    throw new NoApiResponseException();
                }
            }
        } catch (IOException e) {
            throw new NoApiResponseException();
        }
        throw new NoApiResponseException();
    }
}
