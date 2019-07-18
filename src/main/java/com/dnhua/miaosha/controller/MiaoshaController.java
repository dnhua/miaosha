package com.dnhua.miaosha.controller;

import com.dnhua.miaosha.domain.MiaoshaOrder;
import com.dnhua.miaosha.domain.MiaoshaUser;
import com.dnhua.miaosha.domain.OrderInfo;
import com.dnhua.miaosha.rabbitmq.MQSender;
import com.dnhua.miaosha.rabbitmq.MiaoshaMessage;
import com.dnhua.miaosha.redis.GoodsKey;
import com.dnhua.miaosha.redis.RedisService;
import com.dnhua.miaosha.result.CodeMsg;
import com.dnhua.miaosha.result.Result;
import com.dnhua.miaosha.service.GoodsService;
import com.dnhua.miaosha.service.MiaoshaService;
import com.dnhua.miaosha.service.MiaoshaUserService;
import com.dnhua.miaosha.service.OrderService;
import com.dnhua.miaosha.vo.GoodsVo;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

@Controller
@RequestMapping("/miaosha")
public class MiaoshaController implements InitializingBean {

	@Autowired
	MiaoshaUserService userService;
	
	@Autowired
	RedisService redisService;
	
	@Autowired
	GoodsService goodsService;
	
	@Autowired
	OrderService orderService;
	
	@Autowired
	MiaoshaService miaoshaService;

	@Autowired
	MQSender sender;

	private HashMap<Long, Boolean> localOverMap =  new HashMap<Long, Boolean>();
	
//    @RequestMapping("/do_miaosha")
//    public String list(Model model, MiaoshaUser user,
//					   @RequestParam("goodsId")long goodsId) {
//    	model.addAttribute("user", user);
//    	if(user == null) {
//    		return "login";
//    	}
//    	//判断库存
//    	GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
//    	int stock = goods.getStockCount();
//    	if(stock <= 0) {
//    		model.addAttribute("errmsg", CodeMsg.MIAO_SHA_OVER.getMsg());
//    		return "miaosha_fail";
//    	}
//    	//判断是否已经秒杀到了
//    	MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
//    	if(order != null) {
//    		model.addAttribute("errmsg", CodeMsg.REPEATE_MIAOSHA.getMsg());
//    		return "miaosha_fail";
//    	}
//    	//减库存 下订单 写入秒杀订单
//    	OrderInfo orderInfo = miaoshaService.miaosha(user, goods);
//    	model.addAttribute("orderInfo", orderInfo);
//    	model.addAttribute("goods", goods);
//        return "order_detail";
//    }
	@RequestMapping(value="/{path}//do_miaosha", method=RequestMethod.POST)
	@ResponseBody
	public Result<Integer> miaosha(Model model,MiaoshaUser user,
								   @RequestParam("goodsId")long goodsId,
								   @PathVariable("path") String path) {
		model.addAttribute("user", user);
		if(user == null) {
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		//验证path
		boolean check = miaoshaService.checkPath(user, goodsId, path);
		if(!check){
			return Result.error(CodeMsg.REQUEST_ILLEGAL);
		}
		//内存标记，减少redis访问
		boolean over = localOverMap.get(goodsId);
		if(over) {
			return Result.error(CodeMsg.MIAO_SHA_OVER);
		}
		//预减库存
		long stock = redisService.decr(GoodsKey.getMiaoshaGoodsStock, ""+goodsId);//10
		if(stock < 0) {
			localOverMap.put(goodsId, true);
			return Result.error(CodeMsg.MIAO_SHA_OVER);
		}
		//判断是否已经秒杀到了
		MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
		if(order != null) {
			return Result.error(CodeMsg.REPEATE_MIAOSHA);
		}
		//入队
		MiaoshaMessage mm = new MiaoshaMessage();
		mm.setUser(user);
		mm.setGoodsId(goodsId);
		sender.sendMiaoshaMessage(mm);
		return Result.success(0);//排队中
//		model.addAttribute("user", user);
//		if(user == null) {
//			return Result.error(CodeMsg.SESSION_ERROR);
//		}
//		//判断库存
//		GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);//10个商品，req1 req2
//		int stock = goods.getStockCount();
//		if(stock <= 0) {
//			return Result.error(CodeMsg.MIAO_SHA_OVER);
//		}
//		//判断是否已经秒杀到了
//		MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
//		if(order != null) {
//			return Result.error(CodeMsg.REPEATE_MIAOSHA);
//		}
//		//减库存 下订单 写入秒杀订单
//		OrderInfo orderInfo = miaoshaService.miaosha(user, goods);
//		return Result.success(orderInfo);
	}

//	@AccessLimit(seconds=5, maxCount=5, needLogin=true)
	@RequestMapping(value="/path", method=RequestMethod.GET)
	@ResponseBody
	public Result<String> getMiaoshaPath(HttpServletRequest request, MiaoshaUser user,
										 @RequestParam("goodsId")long goodsId,
										 @RequestParam(value="verifyCode", defaultValue="0")int verifyCode
	) {
		if(user == null) {
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		boolean check = miaoshaService.checkVerifyCode(user, goodsId, verifyCode);
		if(!check) {
			return Result.error(CodeMsg.REQUEST_ILLEGAL);
		}
		String path  = miaoshaService.createMiaoshaPath(user, goodsId);
		return Result.success(path);
	}

	/**
	 * 系统初始化时执行，将商品list加载到redis
	 * @throws Exception
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		List<GoodsVo> goodsList = goodsService.listGoodsVo();
		if(goodsList == null) {
			return;
		}
		for(GoodsVo goods : goodsList) {
			redisService.set(GoodsKey.getMiaoshaGoodsStock, ""+goods.getId(), goods.getStockCount());
			localOverMap.put(goods.getId(), false);
		}
	}

	/**
	 * orderId：成功
	 * -1：秒杀失败
	 * 0： 排队中
	 * @param model
	 * @param user
	 * @param goodsId
	 * @return
	 */
	@RequestMapping(value="/result", method=RequestMethod.GET)
	@ResponseBody
	public Result<Long> miaoshaResult(Model model,MiaoshaUser user,
									  @RequestParam("goodsId")long goodsId) {
		model.addAttribute("user", user);
		if(user == null) {
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		long result  =miaoshaService.getMiaoshaResult(user.getId(), goodsId);
		return Result.success(result);
	}

	@RequestMapping(value="/verifyCode", method=RequestMethod.GET)
	@ResponseBody
	public Result<String> getMiaoshaVerifyCod(HttpServletResponse response, MiaoshaUser user,
											  @RequestParam("goodsId")long goodsId) {
		if(user == null) {
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		try {
			BufferedImage image  = miaoshaService.createVerifyCode(user, goodsId);
			OutputStream out = response.getOutputStream();
			ImageIO.write(image, "JPEG", out);
			out.flush();
			out.close();
			return null;
		}catch(Exception e) {
			e.printStackTrace();
			return Result.error(CodeMsg.MIAOSHA_FAIL);
		}
	}
}
