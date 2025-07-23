package com.zidong.service;

/*
* @description: 订单接口服务
* */

import com.zidong.domain.req.ShopCartReq;
import com.zidong.domain.res.PayOrderRes;

import java.util.List;

public interface IOrderService {

    PayOrderRes createOrder(ShopCartReq shopCartReq) throws Exception;

    void changeOrderPaySuccess(String orderId);

    List<String> queryNoPayNotifyOrder();

    List<String> queryTimeoutCloseOrderList();

    boolean changeOrderClose(String orderId);



}
