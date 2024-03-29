package com.dnhua.miaosha.service;

import java.util.Date;

import com.dnhua.miaosha.dao.OrderDao;
import com.dnhua.miaosha.domain.MiaoshaOrder;
import com.dnhua.miaosha.domain.MiaoshaUser;
import com.dnhua.miaosha.domain.OrderInfo;
import com.dnhua.miaosha.redis.OrderKey;
import com.dnhua.miaosha.redis.RedisService;
import com.dnhua.miaosha.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
	
	@Autowired
	OrderDao orderDao;

	@Autowired
	RedisService redisService;

	public MiaoshaOrder getMiaoshaOrderByUserIdGoodsId(long userId, long goodsId) {
//		return orderDao.getMiaoshaOrderByUserIdGoodsId(userId, goodsId);
        return redisService.get(OrderKey.getMiaoshaOrderByUidGid, ""+userId+"_"+goodsId, MiaoshaOrder.class);
	}

	@Transactional
	public OrderInfo createOrder(MiaoshaUser user, GoodsVo goods) {
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setCreateDate(new Date());
		orderInfo.setDeliveryAddrId(0L);
		orderInfo.setGoodsCount(1);
		orderInfo.setGoodsId(goods.getId());
		orderInfo.setGoodsName(goods.getGoodsName());
		orderInfo.setGoodsPrice(goods.getMiaoshaPrice());
		orderInfo.setOrderChannel(1);
		orderInfo.setStatus(0);
		orderInfo.setUserId(user.getId());
		orderDao.insert(orderInfo);
		MiaoshaOrder miaoshaOrder = new MiaoshaOrder();
		miaoshaOrder.setGoodsId(goods.getId());
		miaoshaOrder.setOrderId(orderInfo.getId());
		miaoshaOrder.setUserId(user.getId());
		orderDao.insertMiaoshaOrder(miaoshaOrder);
        redisService.set(OrderKey.getMiaoshaOrderByUidGid, ""+user.getId()+"_"+goods.getId(), miaoshaOrder);
        return orderInfo;
	}

	public OrderInfo getOrderById(long orderId) {
		return orderDao.getOrderById(orderId);
	}

}
