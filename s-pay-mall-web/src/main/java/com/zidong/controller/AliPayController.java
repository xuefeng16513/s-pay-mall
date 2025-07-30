package com.zidong.controller;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.zidong.common.constants.Constants;
import com.zidong.common.response.Response;
import com.zidong.controller.dto.CreatePayRequestDTO;
import com.zidong.dao.IOrderDao;
import com.zidong.domain.po.PayOrder;
import com.zidong.domain.req.ShopCartReq;
import com.zidong.domain.res.PayOrderRes;
import com.zidong.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/alipay/")
public class AliPayController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IOrderDao orderDao;

    @Value("${alipay.alipay_public_key}")
    private String alipayPublicKey;

    @Resource
    private IOrderService orderService;

    /**
     * http://localhost:8080/api/v1/alipay/create_pay_order
     *
     * {
     *     "userId": "10001",
     *     "productId": "100001"
     * }
     */
    @RequestMapping(value = "create_pay_order", method =  RequestMethod.POST)
    public Response<String> createPayOrder(@RequestBody CreatePayRequestDTO createPayRequestDTO){
        try {
            log.info("商品下单，根据商品ID创建支付单开始 userId:{} productId:{}", createPayRequestDTO.getUserId(), createPayRequestDTO.getUserId());
            String userId = createPayRequestDTO.getUserId();
            String productId = createPayRequestDTO.getProductId();
            // 下单
            PayOrderRes payOrderRes = orderService.createOrder(ShopCartReq.builder()
                    .userId(userId)
                    .productId(productId)
                    .build());

            log.info("商品下单，根据商品ID创建支付单完成 userId:{} productId:{} orderId:{}", userId, productId, payOrderRes.getOrderId());
            return Response.<String>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(payOrderRes.getPayUrl())
                    .build();
        } catch (Exception e) {
            log.error("商品下单，根据商品ID创建支付单失败 userId:{} productId:{}", createPayRequestDTO.getUserId(), createPayRequestDTO.getUserId(), e);
            return Response.<String>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * http://webnet.natapp1.cc/api/v1/alipay/alipay_notify_url
     */
    @RequestMapping(value = "alipay_notify_url", method = RequestMethod.POST)
    public String payNotify(HttpServletRequest request) throws AlipayApiException {
        log.info("支付回调，消息接收 {}", request.getParameter("trade_status"));

        if (!request.getParameter("trade_status").equals("TRADE_SUCCESS")) {
            return "false";
        }

        Map<String, String> params = new HashMap<> ();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (String name : requestParams.keySet()) {
            params.put(name, request.getParameter(name));
        }

        String tradeNo = params.get("out_trade_no");
        String gmtPayment = params.get("gmt_payment");
        String alipayTradeNo = params.get("trade_no");

        String sign = params.get("sign");
        String content = AlipaySignature.getSignCheckContentV1(params);
        boolean checkSignature = AlipaySignature.rsa256CheckContent(content, sign, alipayPublicKey, "UTF-8"); // 验证签名
        // 支付宝验签
        if (!checkSignature) {
            return "false";
        }

        // 验签通过
        log.info("支付回调，交易名称: {}", params.get("subject"));
        log.info("支付回调，交易状态: {}", params.get("trade_status"));
        log.info("支付回调，支付宝交易凭证号: {}", params.get("trade_no"));
        log.info("支付回调，商户订单号: {}", params.get("out_trade_no"));
        log.info("支付回调，交易金额: {}", params.get("total_amount"));
        log.info("支付回调，买家在支付宝唯一id: {}", params.get("buyer_id"));
        log.info("支付回调，买家付款时间: {}", params.get("gmt_payment"));
        log.info("支付回调，买家付款金额: {}", params.get("buyer_pay_amount"));
        log.info("支付回调，支付回调，更新订单 {}", tradeNo);

        // 从 Redis 读取订单
        String orderKey = "order:temp:" + tradeNo;
        String orderJson = stringRedisTemplate.opsForValue().get(orderKey);
        if (orderJson == null) {
            log.error("支付成功，但Redis中找不到订单数据，orderId={}", tradeNo);
            return "false";
        }
        PayOrder payOrder = JSON.parseObject(orderJson, PayOrder.class);
        try {
            // 写入数据库
            orderDao.insert(payOrder);

            // 更新订单状态字段为 PAID
            orderService.changeOrderPaySuccess(tradeNo);

            // 更新 Redis 状态缓存（供前端轮询）
//            String statusKey = "order:status:" + tradeNo;
//            stringRedisTemplate.opsForValue().set(orderKey, "PAID", Duration.ofMinutes(30));
//            stringRedisTemplate.opsForValue().set(orderKey, "PAID");

            // 删除临时订单缓存
            stringRedisTemplate.delete(orderKey);
            log.info("订单{}已支付成功，已从Redis中删除临时订单缓存，存入数据库中", tradeNo);

        } catch (Exception e) {
            log.error("写入订单失败", e);
            return "false";
        }

        return "success";


//        orderService.changeOrderPaySuccess (tradeNo);
//
//        String redisKey = "order:status:" + tradeNo;
//        stringRedisTemplate.opsForValue().set(redisKey, "PAID", Duration.ofMinutes(30));


//        return "success";
    }


}
