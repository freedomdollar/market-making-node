/*M!999999\- enable the sandbox mode */ 
-- MariaDB dump 10.19  Distrib 10.11.13-MariaDB, for debian-linux-gnu (x86_64)
--
-- Host: 127.0.0.1    Database: marketmaker
-- ------------------------------------------------------
-- Server version	10.11.13-MariaDB-0ubuntu0.24.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `app_settings`
--

DROP TABLE IF EXISTS `app_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;

CREATE TABLE `app_settings` (
                                `key` varchar(256) NOT NULL,
                                `value` varchar(256) DEFAULT NULL,
                                PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

--
-- Table structure for table `audit_wallet_transfers`
--

DROP TABLE IF EXISTS `audit_wallet_transfers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_wallet_transfers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `requester` varchar(256) DEFAULT NULL,
  `comment` varchar(256) DEFAULT NULL,
  `error` varchar(256) DEFAULT NULL,
  `txid` varchar(256) DEFAULT NULL,
  `address` varchar(256) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `timestamp` timestamp NULL DEFAULT NULL,
  `amount` decimal(32,12) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `audit_wallet_transfers_status_index` (`status`),
  KEY `audit_wallet_transfers_txid_index` (`txid`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cex_exchanges`
--

DROP TABLE IF EXISTS `cex_exchanges`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `cex_exchanges` (
  `exchange_id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(128) DEFAULT NULL,
  `subaccount` varchar(128) DEFAULT NULL,
  `trade_pair` varchar(128) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  PRIMARY KEY (`exchange_id`)
) ENGINE=InnoDB AUTO_INCREMENT=92 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cex_order_settings`
--

DROP TABLE IF EXISTS `cex_order_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `cex_order_settings` (
  `exchange_id` int(11) NOT NULL,
  `highest_trade_id` varchar(128) DEFAULT NULL,
  `timestamp` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`exchange_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cex_trade_log`
--

DROP TABLE IF EXISTS `cex_trade_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `cex_trade_log` (
  `exchange_id` int(11) NOT NULL,
  `orderId` varchar(128) NOT NULL,
  `tradeId` varchar(128) NOT NULL,
  `amountBase` decimal(32,12) DEFAULT NULL,
  `baseCurrency` varchar(32) DEFAULT NULL,
  `amountCounter` decimal(32,12) DEFAULT NULL,
  `counterCurrency` varchar(32) DEFAULT NULL,
  `type` varchar(32) DEFAULT NULL,
  `price` decimal(32,12) DEFAULT NULL,
  `timestamp` timestamp NULL DEFAULT NULL,
  `fee` decimal(32,12) DEFAULT NULL,
  `feeCurrency` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`exchange_id`,`tradeId`),
  KEY `cex_trade_log_exchange_id_index` (`exchange_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cex_wallet_states`
--

DROP TABLE IF EXISTS `cex_wallet_states`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `cex_wallet_states` (
  `exchange_id` int(11) NOT NULL,
  `timestamp` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`exchange_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cex_wallet_transfer_log`
--

DROP TABLE IF EXISTS `cex_wallet_transfer_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `cex_wallet_transfer_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exchange` bigint(20) DEFAULT NULL,
  `timestamp` timestamp NULL DEFAULT NULL,
  `type` varchar(64) DEFAULT NULL,
  `source` varchar(64) DEFAULT NULL,
  `asset` varchar(128) DEFAULT NULL,
  `address` varchar(256) DEFAULT NULL,
  `network` varchar(128) DEFAULT NULL,
  `assetid` varchar(128) DEFAULT NULL,
  `amount` decimal(32,12) DEFAULT NULL,
  `fee` decimal(32,12) DEFAULT NULL,
  `txid` varchar(128) DEFAULT NULL,
  `inttxid` varchar(128) DEFAULT NULL,
  `subtxid` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cross_chain_swaps`
--

DROP TABLE IF EXISTS `cross_chain_swaps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `cross_chain_swaps` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `src_chain` varchar(32) DEFAULT NULL,
  `dst_chain` varchar(32) DEFAULT NULL,
  `src_amount` bigint(20) DEFAULT NULL,
  `dst_amount` bigint(20) DEFAULT NULL,
  `src_txid` varchar(128) DEFAULT NULL,
  `dst_txid` varchar(128) DEFAULT NULL,
  `src_asset_id` varchar(128) DEFAULT NULL,
  `dst_asset_id` varchar(128) DEFAULT NULL,
  `src_confirmations` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `timestamp` timestamp NULL DEFAULT NULL,
  `signed_payload` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `cross_chain_swaps_pk` (`src_txid`),
  KEY `cross_chain_swaps_dst_chain_index` (`dst_chain`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fusd_buy_back_log`
--

DROP TABLE IF EXISTS `fusd_buy_back_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `fusd_buy_back_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `timestamp` timestamp NOT NULL,
  `usdt_amount` decimal(24,6) NOT NULL DEFAULT 0.000000,
  `fusd_amount` decimal(32,12) DEFAULT NULL,
  `fusd_amount_filled` decimal(24,6) NOT NULL DEFAULT 0.000000,
  `price` decimal(24,6) NOT NULL DEFAULT 0.000000,
  `status` int(11) NOT NULL DEFAULT 0,
  `cex_order_id` varchar(128) DEFAULT NULL,
  `seq_id` uuid DEFAULT NULL,
  `connected_order` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `zano_buy_back_log_status_index` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `interchain_ious`
--

DROP TABLE IF EXISTS `interchain_ious`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `interchain_ious` (
  `orig_chain` varchar(32) DEFAULT NULL,
  `asset` varchar(32) DEFAULT NULL,
  `asset_id` varchar(128) DEFAULT NULL,
  `amount` decimal(32,4) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `login_accounts`
--

DROP TABLE IF EXISTS `login_accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `login_accounts` (
  `login_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `email` varchar(256) DEFAULT NULL,
  `password` char(128) DEFAULT NULL,
  `salt` char(32) DEFAULT NULL,
  `created` timestamp NULL DEFAULT NULL,
  `created_by` varchar(256) NOT NULL,
  `role` varchar(256) DEFAULT NULL,
  `change_password` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`login_id`),
  UNIQUE KEY `login_accounts_pk` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=1009 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `login_user_assign`
--

DROP TABLE IF EXISTS `login_user_assign`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `login_user_assign` (
  `user_id` bigint(20) DEFAULT NULL,
  `login_id` bigint(20) DEFAULT NULL,
  KEY `login_user_assign_login_id_index` (`login_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_log`
--

DROP TABLE IF EXISTS `order_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_log` (
  `order_id` bigint(20) NOT NULL,
  `total` decimal(32,12) DEFAULT NULL,
  `price` decimal(32,12) DEFAULT NULL,
  `amount_start` decimal(32,12) DEFAULT NULL,
  `amount_left` decimal(32,12) DEFAULT NULL,
  `zano_usdt_price` decimal(32,12) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `type` varchar(32) DEFAULT NULL,
  `opened` timestamp NULL DEFAULT NULL,
  `closed` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `other_chains_meta_data`
--

DROP TABLE IF EXISTS `other_chains_meta_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `other_chains_meta_data` (
  `chain` varchar(32) DEFAULT NULL,
  `liabilities` bigint(20) DEFAULT NULL,
  `assets` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `settings`
--

DROP TABLE IF EXISTS `settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `settings` (
  `transaction_index` bigint(20) DEFAULT NULL,
  `wallet_ident` varchar(256) NOT NULL,
  PRIMARY KEY (`wallet_ident`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `simplified_trade`
--

DROP TABLE IF EXISTS `simplified_trade`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `simplified_trade` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `first_currency` varchar(32) DEFAULT NULL,
  `first_amount` decimal(32,18) DEFAULT NULL,
  `second_currency` varchar(32) DEFAULT NULL,
  `second_amount` decimal(32,18) DEFAULT NULL,
  `fee` decimal(32,18) DEFAULT NULL,
  `fee_currency` varchar(32) DEFAULT NULL,
  `timestamp` timestamp NULL DEFAULT NULL,
  `seq_no` int(11) NOT NULL,
  `connected_order` bigint(20) DEFAULT NULL,
  `seq_id` uuid DEFAULT NULL,
  `side` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `swaps`
--

DROP TABLE IF EXISTS `swaps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `swaps` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `txid` varchar(128) DEFAULT NULL,
  `accepted` tinyint(1) DEFAULT NULL,
  `to_finalizer_amount` bigint(20) DEFAULT NULL,
  `to_finalizer_asset_id` varchar(256) DEFAULT NULL,
  `to_initiator_amount` bigint(20) DEFAULT NULL,
  `to_initiator_asset_id` varchar(256) DEFAULT NULL,
  `reason` varchar(256) DEFAULT NULL,
  `my_order_id` bigint(20) DEFAULT NULL,
  `other_order_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=97 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `trade_log`
--

DROP TABLE IF EXISTS `trade_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `my_order_id` bigint(20) NOT NULL,
  `other_order_id` bigint(20) NOT NULL,
  `token_amount` bigint(20) DEFAULT NULL,
  `zano_amount` bigint(20) DEFAULT NULL,
  `zano_price` bigint(20) DEFAULT NULL,
  `zano_usdt_price` decimal(16,6) DEFAULT NULL,
  `type` varchar(32) DEFAULT NULL,
  `txid` varchar(128) DEFAULT NULL,
  `asset_id` varchar(128) DEFAULT NULL,
  `timestamp` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=91 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `two_factor_auths`
--

DROP TABLE IF EXISTS `two_factor_auths`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `two_factor_auths` (
  `2fa_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(64) DEFAULT NULL,
  `data` varchar(256) DEFAULT NULL,
  `created` timestamp NULL DEFAULT NULL,
  `login_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`2fa_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `usdt_buy_back_log`
--

DROP TABLE IF EXISTS `usdt_buy_back_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `usdt_buy_back_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `connected_order` bigint(20) NOT NULL,
  `timestamp` timestamp NOT NULL,
  `usdt_amount` decimal(24,6) NOT NULL DEFAULT 0.000000,
  `zano_amount` decimal(32,12) DEFAULT NULL,
  `usdt_amount_filled` decimal(24,6) NOT NULL DEFAULT 0.000000,
  `price` decimal(24,6) NOT NULL DEFAULT 0.000000,
  `status` int(11) NOT NULL DEFAULT 0,
  `cex_order_id` varchar(128) DEFAULT NULL,
  `seq_id` uuid DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `zano_buy_back_log_status_index` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_accounts`
--

DROP TABLE IF EXISTS `user_accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_accounts` (
  `user_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `first_name` varchar(256) DEFAULT NULL,
  `last_name` varchar(256) DEFAULT NULL,
  `display_name` varchar(256) DEFAULT NULL,
  `company_name` varchar(256) DEFAULT NULL,
  `type` int(11) DEFAULT NULL,
  `created` timestamp NULL DEFAULT NULL,
  `email` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1001 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zano_assets`
--

DROP TABLE IF EXISTS `zano_assets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `zano_assets` (
  `asset_id` varchar(128) NOT NULL,
  `current_supply` bigint(20) unsigned DEFAULT NULL,
  `decimals` int(11) DEFAULT NULL,
  `full_name` varchar(64) DEFAULT NULL,
  `hidden_supply` tinyint(1) DEFAULT NULL,
  `meta_info` varchar(256) DEFAULT NULL,
  `owner` varchar(128) DEFAULT NULL,
  `owner_eth_pub_key` varchar(128) DEFAULT NULL,
  `ticker` varchar(128) DEFAULT NULL,
  `total_max_supply` bigint(20) unsigned DEFAULT NULL,
  `is_whitelisted` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zano_buy_back_log`
--

DROP TABLE IF EXISTS `zano_buy_back_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `zano_buy_back_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `connected_order` bigint(20) NOT NULL,
  `timestamp` timestamp NOT NULL,
  `usdt_amount` decimal(24,6) NOT NULL DEFAULT 0.000000,
  `zano_amount` decimal(32,12) DEFAULT NULL,
  `zano_amount_filled` decimal(24,6) NOT NULL DEFAULT 0.000000,
  `price` decimal(24,6) NOT NULL DEFAULT 0.000000,
  `status` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `zano_buy_back_log_status_index` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zano_moves`
--

DROP TABLE IF EXISTS `zano_moves`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `zano_moves` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `from_ident` varchar(128) DEFAULT NULL,
  `to_ident` varchar(128) DEFAULT NULL,
  `to_address` varchar(512) DEFAULT NULL,
  `txid` varchar(128) DEFAULT NULL,
  `amount` decimal(32,12) DEFAULT NULL,
  `timestamp` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zano_price_history`
--

DROP TABLE IF EXISTS `zano_price_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `zano_price_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `timestamp` timestamp NULL DEFAULT NULL,
  `USDT_price` decimal(16,6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zano_wallet_balance`
--

DROP TABLE IF EXISTS `zano_wallet_balance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `zano_wallet_balance` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `timestamp` timestamp NULL DEFAULT NULL,
  `asset_id` varchar(128) DEFAULT NULL,
  `wallet_address` varchar(128) DEFAULT NULL,
  `ident` varchar(128) DEFAULT NULL,
  `utxos` int(11) DEFAULT NULL,
  `total` bigint(20) DEFAULT NULL,
  `unlocked` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `zano_wallet_balance_asset_id_index` (`asset_id`),
  KEY `zano_wallet_balance_asset_id_timestamp_index` (`asset_id`,`timestamp`)
) ENGINE=InnoDB AUTO_INCREMENT=4599 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zano_wallet_transactions`
--

DROP TABLE IF EXISTS `zano_wallet_transactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `zano_wallet_transactions` (
  `txid` varchar(128) NOT NULL,
  `wallet_ident` varchar(128) NOT NULL,
  `tx_index` bigint(20) DEFAULT NULL,
  `comment` varchar(256) DEFAULT NULL,
  `fee` bigint(20) unsigned DEFAULT NULL,
  `height` bigint(20) unsigned DEFAULT NULL,
  `is_mining` tinyint(1) DEFAULT NULL,
  `is_mixing` tinyint(1) DEFAULT NULL,
  `is_service` tinyint(1) DEFAULT NULL,
  `payment_id` varchar(256) DEFAULT NULL,
  `remote_address` varchar(256) DEFAULT NULL,
  `remote_alias` varchar(256) DEFAULT NULL,
  `service_entries` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`service_entries`)),
  `vout` int(11) NOT NULL,
  `amount` bigint(20) unsigned DEFAULT NULL,
  `asset_id` varchar(256) DEFAULT NULL,
  `is_income` tinyint(1) DEFAULT NULL,
  `timestamp` timestamp NULL DEFAULT NULL,
  `unlock_time` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`txid`,`vout`,`wallet_ident`),
  KEY `zano_wallet_transactions_timestamp_index` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zano_wallets`
--

DROP TABLE IF EXISTS `zano_wallets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `zano_wallets` (
  `ident` varchar(128) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-09-30 11:38:55

INSERT INTO app_settings (`key`, value) VALUES ('ask_multiplier', '1.005');
INSERT INTO app_settings (`key`, value) VALUES ('bid_multiplier', '0.995');
INSERT INTO app_settings (`key`, value) VALUES ('price_movement_threshold', '0.5');
INSERT INTO app_settings (`key`, value) VALUES ('minimum_token_volume_buy', '3001');
INSERT INTO app_settings (`key`, value) VALUES ('maximum_token_volume_buy', '5002');
INSERT INTO app_settings (`key`, value) VALUES ('minimum_token_volume_sell', '15000');
INSERT INTO app_settings (`key`, value) VALUES ('maximum_token_volume_sell', '100000');
INSERT INTO app_settings (`key`, value) VALUES ('mexc_apikey', '');
INSERT INTO app_settings (`key`, value) VALUES ('mexc_apisecret', '');
INSERT INTO app_settings (`key`, value) VALUES ('zano_usdt_margin', '0.01');
INSERT INTO app_settings (`key`, value) VALUES ('fusd_usdt_margin', '0.01');
INSERT INTO app_settings (`key`, value) VALUES ('mexc_deposit_address_zano', '');
INSERT INTO app_settings (`key`, value) VALUES ('mexc_deposit_address_fusd', '');
INSERT INTO app_settings (`key`, value) VALUES ('zano_sell_volume_multiplier', '1');
INSERT INTO app_settings (`key`, value) VALUES ('zano_sell_price_multiplier', '1');
INSERT INTO app_settings (`key`, value) VALUES ('fusd_withdraw_threshold', '200');
INSERT INTO app_settings (`key`, value) VALUES ('zano_move_threshold', '60');
INSERT INTO app_settings (`key`, value) VALUES ('zano_min_balance_floor', '40');
INSERT INTO app_settings (`key`, value) VALUES ('fusd_move_threshold', '50');
INSERT INTO app_settings (`key`, value) VALUES ('fusd_min_balance_floor', '30');
INSERT INTO app_settings (`key`, value) VALUES ('trade_token_asset_id', '86143388bd056a8f0bab669f78f14873fac8e2dd8d57898cdb725a2d5e2e4f8f');
INSERT INTO app_settings (`key`, value) VALUES ('min_utxo', '30');
INSERT INTO app_settings (`key`, value) VALUES ('max_utxo', '60');
INSERT INTO app_settings (`key`, value) VALUES ('enable_zano_sell', '0');
INSERT INTO app_settings (`key`, value) VALUES ('enable_fusd_buy', '0');
INSERT INTO app_settings (`key`, value) VALUES ('enable_move_to_cex', '0');
INSERT INTO app_settings (`key`, value) VALUES ('enable_move_to_wallet', '0');
INSERT INTO app_settings (`key`, value) VALUES ('zano_move_to_cex_threshold', '2000');
INSERT INTO app_settings (`key`, value) VALUES ('zano_move_to_cex_min_trans', '500');

INSERT INTO app_settings (`key`, value) VALUES ('fusd_move_to_wallet_threshold', '20000');
INSERT INTO app_settings (`key`, value) VALUES ('fusd_move_to_wallet_min_trans', '5000');
INSERT INTO app_settings (`key`, value) VALUES ('enable_autostart_dex_bot', '0');
INSERT INTO app_settings (`key`, value) VALUES ('enable_fusd_sell', '0');
INSERT INTO app_settings (`key`, value) VALUES ('enable_zano_buy', '0');


INSERT INTO settings (transaction_index, wallet_ident) VALUES (0, 'main');

alter table trade_log
    add seq_id varchar(128) null;

alter table zano_buy_back_log
    add seq_id varchar(128) not null;

alter table fusd_buy_back_log
    add error varchar(512) null;

CREATE TABLE `fusd_to_usdt_cex_trade_log` (
                                              `id` bigint(20) NOT NULL AUTO_INCREMENT,
                                              `timestamp` timestamp NOT NULL,
                                              `usdt_amount` decimal(24,6) DEFAULT NULL,
                                              `fusd_amount` decimal(32,12) DEFAULT NULL,
                                              `fusd_amount_filled` decimal(24,6) NOT NULL DEFAULT 0.000000,
                                              `price` decimal(24,6) NOT NULL DEFAULT 0.000000,
                                              `status` int(11) NOT NULL DEFAULT 0,
                                              `cex_order_id` varchar(128) DEFAULT NULL,
                                              `seq_id` uuid DEFAULT NULL,
                                              `connected_order` bigint(20) DEFAULT NULL,
                                              `error` varchar(512) DEFAULT NULL,
                                              PRIMARY KEY (`id`),
                                              KEY `status_index` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;



CREATE TABLE `usdt_to_zano_cex_trade_log` (
                                              `id` bigint(20) NOT NULL AUTO_INCREMENT,
                                              `timestamp` timestamp NOT NULL,
                                              `zano_amount` decimal(24,6) DEFAULT NULL,
                                              `usdt_amount` decimal(32,12) NOT NULL DEFAULT 0.000000000000,
                                              `zano_amount_filled` decimal(24,6) NOT NULL DEFAULT 0.000000,
                                              `price` decimal(24,6) NOT NULL DEFAULT 0.000000,
                                              `status` int(11) NOT NULL DEFAULT 0,
                                              `cex_order_id` varchar(128) DEFAULT NULL,
                                              `seq_id` uuid DEFAULT NULL,
                                              `connected_order` bigint(20) DEFAULT NULL,
                                              `error` varchar(512) DEFAULT NULL,
                                              PRIMARY KEY (`id`),
                                              KEY `usdt_to_zano_cex_trade_log_status_index` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;