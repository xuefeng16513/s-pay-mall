package com.zidong.service;

/*
* @description: 订单接口服务
* */

import com.zidong.domain.req.ShopCartReq;
import com.zidong.domain.res.PayOrderRes;

public interface IOrderService {

    PayOrderRes createOrder(ShopCartReq shopCartReq) throws Exception;

}
