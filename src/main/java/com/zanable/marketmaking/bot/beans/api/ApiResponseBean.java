package com.zanable.marketmaking.bot.beans.api;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponseBean {
    private int status = 500;
    private String message = "Error";
    private Object payload;
}
