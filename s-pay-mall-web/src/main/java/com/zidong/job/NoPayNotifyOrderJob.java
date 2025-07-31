package com.zidong.job;


import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.zidong.common.constants.Constants;
import com.zidong.dao.IOrderDao;
import com.zidong.domain.po.PayOrder;
import com.zidong.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/*
 * @description  检测未接收到或未正确处理的支付回调通知
 */
@Slf4j
@Component()
public class NoPayNotifyOrderJob {

    @Resource
    private IOrderService orderService;
    @Resource
    private AlipayClient alipayClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IOrderDao orderDao;

    @Scheduled(cron = "0/3 * * * * ?")
    public void exec() {
        try {
            log.info("任务；检测未接收到或未正确处理的支付回调通知");
//            List<String> orderIds = orderService.queryNoPayNotifyOrder();
//            if (null == orderIds || orderIds.isEmpty()) return;
//
//            for (String orderId : orderIds) {
//                AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
//                AlipayTradeQueryModel bizModel = new AlipayTradeQueryModel();
//                bizModel.setOutTradeNo(orderId);
//                request.setBizModel(bizModel);
//
//                AlipayTradeQueryResponse alipayTradeQueryResponse = alipayClient.execute(request);
//                String code = alipayTradeQueryResponse.getCode();
//                // 判断状态码
//                if ("10000".equals(code)) {
//                    orderService.changeOrderPaySuccess(orderId);
//                }
//            }
            Set<String> keys = stringRedisTemplate.keys("order:status:*");
            if (keys == null || keys.isEmpty()) return;

            for (String key : keys) {
                String json = stringRedisTemplate.opsForValue().get(key);
                if (json == null || json.isEmpty()) continue;

                // 解析 JSON 成 PayOrder 对象
                PayOrder payOrder;
                try {
                    payOrder = JSON.parseObject(json, PayOrder.class);
                } catch (Exception e) {
                    log.warn("Redis JSON 解析失败，key={}, 内容={}", key, json);
                    continue;
                }

                if (!"PAY_WAIT".equals(payOrder.getStatus())) continue;

                // 提取 orderId
                String orderId = payOrder.getOrderId();

                // 调用支付宝查单
                AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
                AlipayTradeQueryModel bizModel = new AlipayTradeQueryModel();
                bizModel.setOutTradeNo(orderId);
                request.setBizModel(bizModel);

                AlipayTradeQueryResponse response = alipayClient.execute(request);
                String code = response.getCode();
                String tradeStatus = response.getTradeStatus();

                if ("10000".equals(code) && "TRADE_SUCCESS".equals(tradeStatus)) {
                    // 支付已成功，补偿写入数据库、更新 Redis
                    log.info("检测到 Redis 中订单 {} 实际已支付，进行状态补偿", orderId);

                    // 更新 Redis 为 PAID
                    payOrder.setStatus(Constants.OrderStatusEnum.PAY_SUCCESS.getCode());
                    stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(payOrder));

                    //插入数据库
                    orderDao.insert(payOrder);
                }
            }
        } catch (Exception e) {
            log.error("检测未接收到或未正确处理的支付回调通知失败", e);
        }
    }

}
