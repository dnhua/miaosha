package com.dnhua.miaosha.redis;

import com.dnhua.miaosha.redis.BasePrefix;

public class MiaoshaKey extends BasePrefix {

    private MiaoshaKey(String prefix) {
        super(prefix);
    }
    public static MiaoshaKey isGoodsOver = new MiaoshaKey("go");
}
