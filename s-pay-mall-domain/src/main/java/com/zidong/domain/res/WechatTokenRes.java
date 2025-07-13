package com.zidong.domain.res;


import lombok.Data;

/**
 *
 * @description: 获取Access Token DTO对象
 *
 *
 */
@Data
public class WechatTokenRes {

    private String access_token;
    private String expires_in;
    private String errcode;
    private String errmsg;
}
