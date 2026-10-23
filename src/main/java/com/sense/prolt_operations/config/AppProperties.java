package com.sense.prolt_operations.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private String nodeEnv = "development";
    private List<String> corsOrigins = new ArrayList<>();
    private boolean runScraper = false;
    private List<String> webhookReceivers = new ArrayList<>();
    private String webhookSecret;
    private final Admin admin = new Admin();
    private final Mail mail = new Mail();
    private String accuweatherApiKey;
    private String iexCalculatorApiUrl;
    private String puppeteerExecutablePath;
    private final Starrocks starrocks = new Starrocks();
    private String backupDatabaseUrl;
    private String redisUrl;

    public Jwt getJwt() {
        return jwt;
    }

    public String getNodeEnv() {
        return nodeEnv;
    }

    public void setNodeEnv(String nodeEnv) {
        this.nodeEnv = nodeEnv;
    }

    public List<String> getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(List<String> corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public boolean isRunScraper() {
        return runScraper;
    }

    public void setRunScraper(boolean runScraper) {
        this.runScraper = runScraper;
    }

    public List<String> getWebhookReceivers() {
        return webhookReceivers;
    }

    public void setWebhookReceivers(List<String> webhookReceivers) {
        this.webhookReceivers = webhookReceivers;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public Admin getAdmin() {
        return admin;
    }

    public Mail getMail() {
        return mail;
    }

    public String getAccuweatherApiKey() {
        return accuweatherApiKey;
    }

    public void setAccuweatherApiKey(String accuweatherApiKey) {
        this.accuweatherApiKey = accuweatherApiKey;
    }

    public String getIexCalculatorApiUrl() {
        return iexCalculatorApiUrl;
    }

    public void setIexCalculatorApiUrl(String iexCalculatorApiUrl) {
        this.iexCalculatorApiUrl = iexCalculatorApiUrl;
    }

    public String getPuppeteerExecutablePath() {
        return puppeteerExecutablePath;
    }

    public void setPuppeteerExecutablePath(String puppeteerExecutablePath) {
        this.puppeteerExecutablePath = puppeteerExecutablePath;
    }

    public Starrocks getStarrocks() {
        return starrocks;
    }

    public String getBackupDatabaseUrl() {
        return backupDatabaseUrl;
    }

    public void setBackupDatabaseUrl(String backupDatabaseUrl) {
        this.backupDatabaseUrl = backupDatabaseUrl;
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public void setRedisUrl(String redisUrl) {
        this.redisUrl = redisUrl;
    }

    public static class Jwt {
        private String secret;
        private String expiresIn = "7d";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(String expiresIn) {
            this.expiresIn = expiresIn;
        }
    }

    public static class Admin {
        private String username;
        private String email;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Mail {
        private String from = "no-reply@proltenergy.com";

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }

    public static class Starrocks {
        private String host;
        private int port = 9030;
        private String user;
        private String password;
        private String database = "prolt_hes_db";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }
    }
}
