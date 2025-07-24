package com.zidong.service.impl;

import com.google.common.cache.Cache;
import com.zidong.domain.vo.WechatTemplateMessageVO;
import com.zidong.domain.req.WechatQrCodeReq;
import com.zidong.domain.res.WechatQrCodeRes;
import com.zidong.domain.res.WechatTokenRes;
import com.zidong.service.ILoginService;
import com.zidong.service.wechat.IWechatApiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Call;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class WechatLoginServiceImpl implements ILoginService {
    @Value("${weixin.config.app-id}")
    private String appid;
    @Value("${weixin.config.app-secret}")
    private String appSecret;
    @Value("${weixin.config.template_id}")
    private String template_id;

    @Resource
    private Cache<String, String> weixinAccessToken;
    @Resource
    private IWechatApiService weixinApiService;
    @Resource
    private Cache<String, String> openidToken;

    @Override
    public String createQrCodeTicket() throws Exception {
        // 1. 获取 accessToken
        String accessToken = weixinAccessToken.getIfPresent(appid);
        if (null == accessToken) {
            Call<WechatTokenRes> call = weixinApiService.getToken("client_credential", appid, appSecret);
            WechatTokenRes weixinTokenRes = call.execute().body();
            assert weixinTokenRes != null;
            accessToken = weixinTokenRes.getAccess_token();
            weixinAccessToken.put(appid, accessToken);
        }

        // 2. 生成 ticket
        WechatQrCodeReq weixinQrCodeReq = WechatQrCodeReq.builder()
                .expire_seconds(2592000)
                .action_name(WechatQrCodeReq.ActionNameTypeVO.QR_SCENE.getCode())
                .action_info(WechatQrCodeReq.ActionInfo.builder()
                        .scene(WechatQrCodeReq.ActionInfo.Scene.builder()
                                .scene_id(100601)
                                .build())
                        .build())
                .build();

        Call<WechatQrCodeRes> call = weixinApiService.createQrCode(accessToken, weixinQrCodeReq);
        WechatQrCodeRes weixinQrCodeRes = call.execute().body();
        assert null != weixinQrCodeRes;
        return weixinQrCodeRes.getTicket();
    }

    @Override
    public String checkLogin(String ticket) {
        return openidToken.getIfPresent(ticket);
    }

    @Override
    public void saveLoginState(String ticket, String openid) throws IOException {
        openidToken.put(ticket, openid);

        // 1. 获取 accessToken 【实际业务场景，按需处理下异常】
        String accessToken = weixinAccessToken.getIfPresent(appid);
        if (null == accessToken){
            Call<WechatTokenRes> call = weixinApiService.getToken("client_credential", appid, appSecret);
            WechatTokenRes weixinTokenRes = call.execute().body();
            assert weixinTokenRes != null;
            accessToken = weixinTokenRes.getAccess_token();
            weixinAccessToken.put(appid, accessToken);
        }

        // 2. 发送模板消息
        Map<String, Map<String, String>> data = new HashMap<> ();
        WechatTemplateMessageVO.put(data, WechatTemplateMessageVO.TemplateKey.USER, openid);

        WechatTemplateMessageVO templateMessageDTO = new WechatTemplateMessageVO(openid, template_id);
        templateMessageDTO.setUrl("https://github.com/xuefeng16513/s-pay-mall");
        templateMessageDTO.setData(data);

        Call<Void> call = weixinApiService.sendMessage(accessToken, templateMessageDTO);
        call.execute();

    }
}
