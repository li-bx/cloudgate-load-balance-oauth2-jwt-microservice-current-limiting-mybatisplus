package com.hd.gateway.conf;

import com.alibaba.fastjson.JSON;
import com.hd.gateway.GatewayApplication;
import com.hd.gateway.model.RetResult;
import com.hd.gateway.model.TokenInfo;
import com.hd.gateway.utils.HttpUtil;
import com.hd.gateway.utils.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: liwei
 */
@Slf4j
public class DealGatewayFilter implements GatewayFilter, Ordered
{
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        log.info("DealGatewayFilter:");
        //获取header的参数
        String tokenInfoJson = exchange.getRequest().getHeaders().getFirst("token-info");
        TokenInfo tokenInfo= JSON.parseObject(tokenInfoJson,TokenInfo.class);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder()
    {
        return Ordered.LOWEST_PRECEDENCE - 1000;
    }
}