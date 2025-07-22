package com.zidong.service.impl;

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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

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

    @Override
    public PayOrderRes createOrder(ShopCartReq shopCartReq) throws Exception {
        //查询当前用户是否存在未支付订单或掉单订单
        PayOrder payOrderReq = new PayOrder();
        payOrderReq.setUserId(shopCartReq.getUserId());
        payOrderReq.setProductId (shopCartReq.getProductId());

        PayOrder unpaidOrder = orderDao.queryUnPayOrder(payOrderReq);

        if(unpaidOrder != null && Constants.OrderStatusEnum.PAY_WAIT.getCode ().equals (unpaidOrder.getStatus ())){
            log.info("创建订单-存在，已存在未支付订单。userId:{} productId:{} orderId:{}", shopCartReq.getUserId(), shopCartReq.getProductId(), unpaidOrder.getOrderId());
            return PayOrderRes.builder()
                    .orderId(unpaidOrder.getOrderId())
                    .payUrl(unpaidOrder.getPayUrl())
                    .build();
        } else if(unpaidOrder != null && Constants.OrderStatusEnum.CREATE.getCode ().equals (unpaidOrder.getStatus ())){
            //处于流水单创建但支付单为创建的状态
            // todo 下次写
        }

        //查询商品并且创建订单
        ProductVO productVO = productRPC.queryProductByProductId(shopCartReq.getProductId());
        String orderId = RandomStringUtils.randomNumeric(16);
        orderDao.insert(PayOrder.builder()
                .userId(shopCartReq.getUserId())
                .productId(shopCartReq.getProductId())
                .productName(productVO.getProductName())
                .orderId(orderId)
                .totalAmount(productVO.getPrice())
                .orderTime(new Date ())
                .status(Constants.OrderStatusEnum.CREATE.getCode())
                .build());

        //创建支付单
        // todo 下次写

        return PayOrderRes.builder()
                .orderId(orderId)
                .payUrl("暂无")
                .build();
    }
}
