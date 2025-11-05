CREATE TABLE `telegram_channels` (
                                     `channel_id` bigint(20) NOT NULL,
                                     `channel_name` varchar(256) DEFAULT NULL,
                                     PRIMARY KEY (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;