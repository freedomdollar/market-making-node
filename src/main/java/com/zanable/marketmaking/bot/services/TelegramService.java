package com.zanable.marketmaking.bot.services;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.TelegramChannel;
import com.zanable.marketmaking.bot.beans.TelegramGetUpdatesResponse;
import com.zanable.marketmaking.bot.tools.TelegramBot;
import com.zanable.shared.interfaces.ApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TelegramService implements ApplicationService {

    private final static Logger logger = LoggerFactory.getLogger(TelegramService.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private static Gson gson = new Gson();

    private static TelegramBot telegramBot = null;

    public TelegramService() throws Exception {
        String token = SettingsService.getAppSettingSafe("telegram_bot_api_key");
        if (token == null) {
            throw new Exception("No API token set, can't start service");
        }
        telegramBot = new TelegramBot(token);
    }

    @Override
    public void init() {

        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                checkChannelJoinsQuits();
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

    }

    public static void sendMessageToAllChannels(String message) {
        if (telegramBot == null) {
            return;
        }
        List<TelegramChannel> channels = DatabaseService.getTelegramChannels(null);
        for (TelegramChannel channel : channels) {
            if (telegramBot != null) {
                try {
                    telegramBot.sendMessage(String.valueOf(channel.getChannelId()), message);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void checkChannelJoinsQuits() {
        try {
            String offsetString = SettingsService.getAppSettingSafe("telegram_bot_api_offset");
            TelegramGetUpdatesResponse resp = telegramBot.getMyChatUpdates(offsetString);
            long lastUpdateId = 0;
            if (resp.getResult().isEmpty()) {
                return;
            }
            for (TelegramGetUpdatesResponse.Update update : resp.getResult()) {
                if (update.getMyChatMember() != null) {
                    if (update.getMyChatMember().getNewChatMember().getStatus().equals("left")) {
                        DatabaseService.removeTelegramChannel(null, update.getMyChatMember().getChat().getId());
                        logger.info("Removing telegram channel " + update.getMyChatMember().getChat().getTitle());
                    } else if (update.getMyChatMember().getNewChatMember().getStatus().equals("member")) {
                        DatabaseService.insertTelegramChannel(null, update.getMyChatMember().getChat().getId(), update.getMyChatMember().getChat().getTitle());
                        logger.info("Adding telegram channel " + update.getMyChatMember().getChat().getTitle());
                    }
                }
                lastUpdateId = update.getUpdateId();
            }
            long offset = 0;
            long lastUpdateIdlong = Long.parseLong(offsetString);
            long lastUpdateIdPlusOne = lastUpdateIdlong + 1;
            if (offsetString != null) {
                offset = Long.valueOf(offsetString);
            }
            logger.info("Last update id is " + lastUpdateId + " and offsetString is " + offset);
            logger.info("Setting last update id is " + lastUpdateIdPlusOne);

            if (lastUpdateId >= offset) {
                logger.info("Setting Telegram offset to " + lastUpdateIdPlusOne);
                SettingsService.saveAppSetting("telegram_bot_api_offset", String.valueOf(lastUpdateIdPlusOne));
                SettingsService.updateAppSettings();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void destroy() {

    }
}
