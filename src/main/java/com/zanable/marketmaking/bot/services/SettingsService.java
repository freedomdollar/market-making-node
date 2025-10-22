package com.zanable.marketmaking.bot.services;

import com.google.gson.Gson;
import com.zanable.shared.interfaces.ApplicationService;
import lombok.Data;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NameNotFoundException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SettingsService implements ApplicationService {

    private final static Logger logger = LoggerFactory.getLogger(SettingsService.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    @Getter
    private static HashMap<String, String> appSettings = new HashMap<>();
    private static Gson gson = new Gson();

    @Override
    public void init() {

        updateAppSettings();
        logger.info(gson.toJson(appSettings));

        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    updateAppSettings();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        scheduledExecutorService.shutdown();
    }

    public static void updateAppSettings() {
        appSettings = DatabaseService.getAppSettingsFromDb();
    }

    public static String getAppSetting(String key) throws NameNotFoundException {
        if (appSettings.containsKey(key)) {
            if (appSettings.get(key).equals("")) {
                return null;
            }
            return appSettings.get(key);
        }
        throw new NameNotFoundException("No value for key " + key);
    }
    public static String getAppSettingSafe(String key) {
        if (appSettings.containsKey(key)) {
            return appSettings.get(key);
        }
        return null;
    }

    public static void saveAppSetting(String key, String value) {
        DatabaseService.upsertAppSetting(key, value);
    }
}
