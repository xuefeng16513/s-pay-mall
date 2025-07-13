package com.zidong.domain.res;

import lombok.Data;

/**
 * @description 获取微信登录二维码响应对象
 */
@Data
public class WechatQrCodeRes {
    private String ticket;
    private Long expire_seconds;
    private String url;
}
