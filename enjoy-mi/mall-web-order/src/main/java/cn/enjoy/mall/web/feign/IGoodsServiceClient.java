package cn.enjoy.mall.web.feign;

import cn.enjoy.mall.service.IGoodsService;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = /*"MALL-PRODUCT-SERVICE"*/"API-GATEWAY"/*,path = "/product"*/)
public interface IGoodsServiceClient extends IGoodsService {
}
