package cn.enjoy.mall.web.controller;

import cn.enjoy.core.utils.response.HttpResponseBody;
import cn.enjoy.mall.vo.KillGoodsSpecPriceDetailVo;
import cn.enjoy.mall.web.service.TestKillGoodsService;
import cn.enjoy.sys.controller.BaseController;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/testkillgoods")
public class TestKillGoodsController extends BaseController {
    @Autowired
    private TestKillGoodsService testKillGoodsService;

    @PostMapping("testkill")
    public HttpResponseBody testkill(int killid){
        KillGoodsSpecPriceDetailVo killGoodsSpecPriceDetailVo = testKillGoodsService.detailVo(killid);
        return HttpResponseBody.successResponse("ok",killGoodsSpecPriceDetailVo);
    }
}
