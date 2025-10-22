package com.zanable.marketmaking.bot;

import com.zanable.marketmaking.bot.services.*;
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
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    @Getter
    @Setter
    private static List<String> errors = new ArrayList<>();


    /**
     * This event is executed as late as conceivably possible to indicate that
     * the application is ready to service requests.
     */
    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {

        logger.info("Starting application, but waiting 10 seconds first.");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        isServicesStarted = true;
        logger.info("Starting application for real.");

        System.out.println("Database URL: " + dataSourceUrl);

        DatabaseService databaseService = new DatabaseService(dataSourceUrl, dataSourceUser, dataSourcePass);
        databaseService.init();
        serviceList.add(databaseService);

        SettingsService settingsService = new SettingsService();
        settingsService.init();
        serviceList.add(settingsService);

        /*
        SigningEngine signingEngine = new SigningEngine(config);
        signingEngine.init();
        serviceList.add(signingEngine);
         */

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


        /*

        CexTradeSettings cexTradeSettings = new CexTradeSettings(config);
        cexTradeSettings.init();
        serviceList.add(cexTradeSettings);
         */
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

        if (!isDexTradeServiceStarted && ZanoWalletService.getWalletAlias() != null) {
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
            if (!isCexTradeServiceStarted&& SettingsService.getAppSetting("mexc_apikey") != null && SettingsService.getAppSetting("mexc_apisecret") != null) {
                isCexTradeServiceStarted = true;
                CexTradeService cexTradeSettings = new CexTradeService();
                cexTradeSettings.init();
                serviceList.add(cexTradeSettings);
            }
        } catch (NameNotFoundException e) {
            // NOP
        }


    }

}