package com.zanable.marketmaking.bot.beans;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

@Getter
public class TelegramGetUpdatesResponse {
    private boolean ok;
    private List<Update> result;

    @Getter
    public static class Update {
        @SerializedName("update_id")
        private long updateId;

        @SerializedName("my_chat_member")
        private MyChatMember myChatMember;
    }

    @Getter
    public static class MyChatMember {
        private Chat chat;
        private User from;
        /** Unix time (seconds) */
        private long date;

        @SerializedName("old_chat_member")
        private ChatMember oldChatMember;

        @SerializedName("new_chat_member")
        private ChatMember newChatMember;
    }

    @Getter
    public static class Chat {
        private long id;
        private String title;
        private String type;

        @SerializedName("all_members_are_administrators")
        private boolean allMembersAreAdministrators;

        @SerializedName("accepted_gift_types")
        private AcceptedGiftTypes acceptedGiftTypes;
    }

    @Getter
    public static class AcceptedGiftTypes {
        @SerializedName("unlimited_gifts")
        private boolean unlimitedGifts;

        @SerializedName("limited_gifts")
        private boolean limitedGifts;

        @SerializedName("unique_gifts")
        private boolean uniqueGifts;

        @SerializedName("premium_subscription")
        private boolean premiumSubscription;
    }

    @Getter
    public static class User {
        private long id;

        @SerializedName("is_bot")
        private boolean isBot;

        @SerializedName("first_name")
        private String firstName;

        private String username;

        @SerializedName("language_code")
        private String languageCode;
    }

    @Getter
    public static class ChatMember {
        private User user;
        private String status;
    }
}
