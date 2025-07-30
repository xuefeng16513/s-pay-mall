package com.zidong.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.google.common.eventbus.EventBus;
import com.zidong.common.constants.Constants;
import com.zidong.dao.IOrderDao;
import com.zidong.domain.po.PayOrder;
import com.zidong.domain.req.ShopCartReq;
import com.zidong.domain.res.PayOrderRes;
import com.zidong.domain.vo.ProductVO;
import com.zidong.service.IOrderService;
import com.zidong.service.rpc.ProductRPC;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.List;

/*
* @description 订单服务
* */
@Slf4j
@Service
public class OrderServiceImpl implements IOrderService {

    @Resource
    private IOrderDao orderDao;

    @Resource
    private ProductRPC productRPC;

    @Resource
    private AlipayClient alipayClient;

    @Resource
    private EventBus eventBus;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${alipay.notify_url}")
    private String notifyUrl;
    @Value("${alipay.return_url}")
    private String returnUrl;

    @Override
    public PayOrderRes createOrder(ShopCartReq shopCartReq) throws Exception {
        //查询当前用户是否存在未支付订单或掉单订单
//        PayOrder payOrderReq = new PayOrder();
//        payOrderReq.setUserId(shopCartReq.getUserId());
//        payOrderReq.setProductId (shopCartReq.getProductId());
//
//        PayOrder unpaidOrder = orderDao.queryUnPayOrder(payOrderReq);

        String orderId = RandomStringUtils.randomNumeric(16);
        // 查是否有未支付订单（Redis 检查）
//        String redisKey = "order:temp:" + shopCartReq.getUserId() + ":" + shopCartReq.getProductId();
        String redisKey = "order:temp:" + orderId;
        String existingOrderJson = stringRedisTemplate.opsForValue().get(redisKey);
        if (existingOrderJson != null) {
            PayOrder existing = JSON.parseObject(existingOrderJson, PayOrder.class);
            return PayOrderRes.builder()
                    .orderId(existing.getOrderId())
                    .payUrl(existing.getPayUrl())
                    .build();
        }

//        if(unpaidOrder != null && Constants.OrderStatusEnum.PAY_WAIT.getCode ().equals (unpaidOrder.getStatus ())){
//            log.info("创建订单-存在，已存在未支付订单。userId:{} productId:{} orderId:{}", shopCartReq.getUserId(), shopCartReq.getProductId(), unpaidOrder.getOrderId());
//            return PayOrderRes.builder()
//                    .orderId(unpaidOrder.getOrderId())
//                    .payUrl(unpaidOrder.getPayUrl())
//                    .build();
//        } else if(unpaidOrder != null && Constants.OrderStatusEnum.CREATE.getCode ().equals (unpaidOrder.getStatus ())){
//            //处于流水单创建但支付单为创建的状态
//            log.info("创建订单-存在，存在未创建支付单订单，创建支付单开始 userId:{} productId:{} orderId:{}", shopCartReq.getUserId(), shopCartReq.getProductId(), unpaidOrder.getOrderId());
//            // 设置订单状态缓存为 UNPAID（设置 30 分钟有效）
//            String redisKey = "order:status:" + unpaidOrder.getOrderId();
//            stringRedisTemplate.opsForValue().set(redisKey, "UNPAID", Duration.ofMinutes(30));
//            PayOrder payOrder = doPrePayOrder(unpaidOrder.getProductName(), unpaidOrder.getOrderId(), unpaidOrder.getTotalAmount());
//            return PayOrderRes.builder()
//                    .orderId(payOrder.getOrderId())
//                    .payUrl(payOrder.getPayUrl())
//                    .build();
//        }

//        //查询商品并且创建订单
//        ProductVO productVO = productRPC.queryProductByProductId(shopCartReq.getProductId());
//        //String orderId = RandomStringUtils.randomNumeric(16);
//        orderDao.insert(PayOrder.builder()
//                .userId(shopCartReq.getUserId())
//                .productId(shopCartReq.getProductId())
//                .productName(productVO.getProductName())
//                .orderId(orderId)
//                .totalAmount(productVO.getPrice())
//                .orderTime(new Date ())
//                .status(Constants.OrderStatusEnum.CREATE.getCode())
//                .build());

        // 正常构建订单（但不写入数据库）
        ProductVO productVO = productRPC.queryProductByProductId(shopCartReq.getProductId());
//        String orderId = RandomStringUtils.randomNumeric(16);

        PayOrder payOrder = PayOrder.builder()
                .userId(shopCartReq.getUserId())
                .productId(shopCartReq.getProductId())
                .productName(productVO.getProductName())
                .orderId(orderId)
                .totalAmount(productVO.getPrice())
                .orderTime(new Date())
                .status(Constants.OrderStatusEnum.PAY_WAIT.getCode())
                .build();

//        // 生成支付链接（调用原有 doPrePayOrder）
//        PayOrder payOrder = doPrePayOrder(ProductVO.getProductName(), orderId, productVO.getPrice());

        //创建支付单
        PayOrder payOrderUrl = doPrePayOrder(productVO.getProductName(), orderId, productVO.getPrice());
        payOrder.setPayUrl(payOrderUrl.getPayUrl());

        // 写入 Redis（临时未支付订单缓存）
        stringRedisTemplate.opsForValue().set(
                redisKey,
                JSON.toJSONString(payOrder),
                Duration.ofMinutes(30)
        );

        return PayOrderRes.builder()
                .orderId(orderId)
                .payUrl(payOrderUrl.getPayUrl())
                .build();
    }

    @Override
    public void changeOrderPaySuccess(String orderId) {
        PayOrder payOrderReq = new PayOrder();
        payOrderReq.setOrderId(orderId);
        payOrderReq.setStatus(Constants.OrderStatusEnum.PAY_SUCCESS.getCode());
        orderDao.changeOrderPaySuccess (payOrderReq);

        eventBus.post (JSON.toJSONString(payOrderReq));
    }

    @Override
    public List<String> queryNoPayNotifyOrder() {
        return orderDao.queryNoPayNotifyOrder();
    }

    @Override
    public List<String> queryTimeoutCloseOrderList() {
        return orderDao.queryTimeoutCloseOrderList();
    }

    @Override
    public boolean changeOrderClose(String orderId) {
        return orderDao.changeOrderClose(orderId);
    }

    private PayOrder doPrePayOrder(String productName, String orderId, BigDecimal totalAmount) throws AlipayApiException {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderId);
        bizContent.put("total_amount", totalAmount.toString());
        bizContent.put("subject", productName);
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(bizContent.toString());

        String form = alipayClient.pageExecute(request).getBody();

        PayOrder payOrder = new PayOrder();
        payOrder.setOrderId(orderId);
        payOrder.setPayUrl(form);
        payOrder.setStatus(Constants.OrderStatusEnum.PAY_WAIT.getCode());

        orderDao.updateOrderPayInfo(payOrder);

        return payOrder;
    }

}
