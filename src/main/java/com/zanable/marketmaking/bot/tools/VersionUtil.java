package com.zanable.marketmaking.bot.tools;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionUtil {

    public static String getVersion() {
        try (InputStream is = VersionUtil.class.getClassLoader()
                .getResourceAsStream("META-INF/build-info.properties")) {

            if (is == null) {
                return "UNKNOWN";
            }

            Properties props = new Properties();
            props.load(is);
            return props.getProperty("build.version", "UNKNOWN");

        } catch (IOException e) {
            return "UNKNOWN";
        }
    }
}