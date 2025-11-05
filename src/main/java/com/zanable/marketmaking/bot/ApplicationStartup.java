package com.zanable.marketmaking.bot;

import com.google.gson.Gson;
import com.zanable.marketmaking.bot.beans.GitHubRelease;
import com.zanable.marketmaking.bot.services.*;
import com.zanable.marketmaking.bot.tools.VersionUtil;
import com.zanable.shared.interfaces.ApplicationService;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ApplicationStartup implements ApplicationListener<ApplicationReadyEvent> {

    private final static Logger logger = LoggerFactory.getLogger(ApplicationStartup.class);
    private List<ApplicationService> serviceList = new ArrayList<ApplicationService>();

    @Value("${SPRING_DATASOURCE_URL}")
    private String dataSourceUrl;
    private static String dataSourceUrlStatic;

    @Value("${SPRING_DATASOURCE_USERNAME}")
    private String dataSourceUser;
    private static String dataSourceUserStatic;

    @Value("${SPRING_DATASOURCE_PASSWORD}")
    private String dataSourcePass;
    private static String dataSourcePassStatic;

    @Getter
    private static boolean isServicesStarted = false;
    @Getter
    private static boolean isDexTradeServiceStarted = false;
    @Getter
    private static boolean isZanoDaemonReady = false;
    @Getter
    private static boolean isZanoWalletStarted = false;
    @Getter
    private static boolean isPriceServiceStarted = false;
    @Getter
    private static boolean isCexTradeServiceStarted = false;
    @Getter
    @Setter
    private static boolean isDatabaseServiceStarted = false;
    @Getter
    private static boolean isTelegramServiceStarted = false;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledExecutorService scheduledExecutorService2 = Executors.newSingleThreadScheduledExecutor();

    @Getter
    @Setter
    private static List<String> errors = new ArrayList<>();

    @Getter
    private static String latestReleaseVersion = null;
    @Getter
    private static String currentAppVersion = null;
    @Getter
    private static boolean updateAvailable = false;


    /**
     * This event is executed as late as conceivably possible to indicate that
     * the application is ready to service requests.
     */
    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {

        DatabaseService databaseService = new DatabaseService(dataSourceUrl, dataSourceUser, dataSourcePass);
        databaseService.init();
        serviceList.add(databaseService);

        SettingsService settingsService = new SettingsService();
        settingsService.init();
        serviceList.add(settingsService);

        isServicesStarted = true;
        logger.info("Starting application for real.");

        currentAppVersion = VersionUtil.getVersion();
        logger.info("Application version is " + currentAppVersion + ", cached app version is " + SettingsService.getAppSettingSafe("application_version"));
        if (SettingsService.getAppSettingSafe("application_version") == null) {
            SettingsService.saveAppSetting("application_version", currentAppVersion);
        } else {
            String compareVersion = SettingsService.getAppSettingSafe("application_version");
            if (compareVersions(currentAppVersion, compareVersion) > 0) {
                logger.info("Application version has increased, new version is "+currentAppVersion+ ", old version is " + compareVersion +". Checking if there is a SQL script for updating DB. ");

                try {
                    DatabaseService.applyPatch("db/patches/" + currentAppVersion + ".sql");
                    logger.info("Database patch is complete");
                } catch (Exception e) {
                    logger.error("Database patch failed", e.getMessage());
                }
                SettingsService.saveAppSetting("application_version", currentAppVersion);
            }
        }

        ZanoDaemonService zanoDaemonService = new ZanoDaemonService();
        zanoDaemonService.init();
        serviceList.add(zanoDaemonService);

        OrderBookAggregatorService aggregatorService = new OrderBookAggregatorService();
        aggregatorService.init();
        serviceList.add(aggregatorService);

        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    startDependentServices();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 4000, TimeUnit.MILLISECONDS);

        scheduledExecutorService2.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    checkLatestReleaseVersion();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 120000, TimeUnit.MILLISECONDS);

    }

    @PreDestroy
    public void destroy() {
        scheduledExecutorService.shutdown();
        for (ApplicationService service : serviceList) {
            service.destroy();
        }
    }

    private void startDependentServices() {

        if (!isZanoDaemonReady && ZanoDaemonService.isZanoReady()) {
            isZanoDaemonReady = true;
        }

        if (!isZanoWalletStarted && isZanoDaemonReady) {
            isZanoWalletStarted = true;
            ZanoWalletService zanoWalletService = new ZanoWalletService();
            zanoWalletService.init();
            serviceList.add(zanoWalletService);
        }

        if (!isDexTradeServiceStarted && ZanoWalletService.getWalletAlias() != null && isZanoDaemonReady) {
            try {
                isDexTradeServiceStarted = true;
                logger.info("Starting DEX trade service.");
                ZanoTradeService zanoTradeService = new ZanoTradeService();
                serviceList.add(zanoTradeService);
                zanoTradeService.init();
            } catch (Exception e) {
                logger.error("Could not start Zano Trade service");
            }

        }
        if (isDexTradeServiceStarted && !isPriceServiceStarted) {
            logger.info("Starting CEX price service.");
            isPriceServiceStarted = true;
            ZanoPriceService zanoPriceService = new ZanoPriceService();
            zanoPriceService.init();
            serviceList.add(zanoPriceService);
        }
        try {
            if (!isCexTradeServiceStarted && SettingsService.getAppSetting("mexc_apikey") != null && SettingsService.getAppSetting("mexc_apisecret") != null) {
                isCexTradeServiceStarted = true;
                CexTradeService cexTradeSettings = new CexTradeService();
                cexTradeSettings.init();
                serviceList.add(cexTradeSettings);
            }
        } catch (NameNotFoundException e) {
            // NOP
        }
        try {
            if (!isTelegramServiceStarted && SettingsService.getAppSetting("telegram_bot_api_key") != null) {
                TelegramService telegramService = new TelegramService();
                isTelegramServiceStarted = true;
                telegramService.init();
                serviceList.add(telegramService);
            }
        } catch (Exception e) {
            // NOP
        }
    }

    public static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (num1 > num2) {
                return 1;
            } else if (num1 < num2) {
                return -1;
            }
        }

        return 0; // Versions are equal
    }

    private static void checkLatestReleaseVersion() {
        try {
            GitHubRelease gitHubRelease = fetchLatestRelease("freedomdollar", "market-making-node");

            String trimmedVersion = gitHubRelease.getTag_name().replaceFirst("v", "");
            latestReleaseVersion = trimmedVersion;
            if (currentAppVersion == null || latestReleaseVersion == null) {
                return;
            }
            if (compareVersions(latestReleaseVersion ,currentAppVersion) > 0) {
                updateAvailable = true;
            }
        } catch (Exception e) {
            logger.error("Could not get latest release info from Github");
        }
    }

    private static GitHubRelease fetchLatestRelease(String owner, String repo) throws Exception {
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", owner, repo);
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github+json");

        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            return new Gson().fromJson(reader, GitHubRelease.class);
        }
    }
}