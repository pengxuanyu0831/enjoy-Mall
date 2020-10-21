package cn.enjoy.mall;


import cn.enjoy.mall.wxsdk.WXPay;
import cn.enjoy.mall.wxsdk.WxPayConfigImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;


/**
 */
//@RestController
//http请求控制类  Contoller
public class WxPayController {
    @Autowired
    WxPayConfigImpl wxPayConfig;
    @Autowired
    WXPay wxPay;
    @Value("${wx.appId}")
    private String appId = "";
    @Value("${wx.mchId}")
    private String mchId = "";
    @Value("${wx.key}")
    private String partnerKey = "";
    @Value("${wx.certPath}")
    private String certPath = "";
    @Value("${wx.notify_url}")
    private String notify_url = "http://www.weixin.qq.com/wxpay/pay.php";
    /**
     * 微信H5 支付
     * 注意：必须再web页面中发起支付且域名已添加到开发配置中
     */
    //微信预支付订单接口 url:/wx/prepay
    @RequestMapping("/wx/test")
    public String test() {
        return "king";
    }
}

