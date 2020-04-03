package com.alastbing.covid19.utils;

import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.*;

public class HttpClientUtil {

    // org.apache.http.impl.client.CloseableHttpClient
    private static CloseableHttpClient httpclient = null;

    // 这里就直接默认固定了,因为以下三个参数在新建的method中仍然可以重新配置并被覆盖.
    static final int connectionRequestTimeout = 5000;// ms毫秒,从池中获取链接超时时间
    static final int connectTimeout = 5000;// ms毫秒,建立链接超时时间
    static final int socketTimeout = 30000;// ms毫秒,读取超时时间

    // 总配置,主要涉及是以下两个参数,如果要作调整没有用到properties会比较后麻烦,但鉴于一经粘贴,随处可用的特点,就不再做依赖性配置化处理了.
    // 而且这个参数同一家公司基本不会变动.
    static final int maxTotal = 500;// 最大总并发,很重要的参数
    static final int maxPerRoute = 100;// 每路并发,很重要的参数

    // 正常情况这里应该配成MAP或LIST
    // 细化配置参数,用来对每路参数做精细化处理,可以管控各ip的流量,比如默认配置请求baidu:80端口最大100个并发链接,
    static final String detailHostName = "http://localhost:9097/";
            //PropertiesUtil.getProperty("yonyou.cloud.ugoods.api.host.name");// 每个细化配置之ip(不重要,在特殊场景很有用)
    static final int detailPort = 80;// 每个细化配置之port(不重要,在特殊场景很有用)
    static final int detailMaxPerRoute = 100;// 每个细化配置之最大并发数(不重要,在特殊场景很有用)

    public static CloseableHttpClient getHttpClient() {
        if (null == httpclient) {
            synchronized (HttpClientUtil.class) {
                if (null == httpclient) {
                    httpclient = init();
                }
            }
        }
        return httpclient;
    }

    /**
     * 链接池初始化 这里最重要的一点理解就是. 让CloseableHttpClient 一直活在池的世界里, 但是HttpPost却一直用完就消掉.
     * 这样可以让链接一直保持着.
     *
     * @return
     */
    private static CloseableHttpClient init() {
        CloseableHttpClient newHttpclient = null;

        // 设置连接池
        ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
        LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create().register("http", plainsf).register("https", sslsf).build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
        // 将最大连接数增加
        cm.setMaxTotal(maxTotal);
        // 将每个路由基础的连接增加
        cm.setDefaultMaxPerRoute(maxPerRoute);

        // 细化配置开始,其实这里用Map或List的for循环来配置每个链接,在特殊场景很有用.
        // 将每个路由基础的连接做特殊化配置,一般用不着
        HttpHost httpHost = new HttpHost(detailHostName, detailPort);
        // 将目标主机的最大连接数增加
        cm.setMaxPerRoute(new HttpRoute(httpHost), detailMaxPerRoute);
        // cm.setMaxPerRoute(new HttpRoute(httpHost2),
        // detailMaxPerRoute2);//可以有细化配置2
        // cm.setMaxPerRoute(new HttpRoute(httpHost3),
        // detailMaxPerRoute3);//可以有细化配置3
        // 细化配置结束

        // 请求重试处理
        HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                if (executionCount >= 2) {// 如果已经重试了2次，就放弃
                    return false;
                }
                if (exception instanceof NoHttpResponseException) {// 如果服务器丢掉了连接，那么就重试
                    return true;
                }
                if (exception instanceof SSLHandshakeException) {// 不要重试SSL握手异常
                    return false;
                }
                if (exception instanceof InterruptedIOException) {// 超时
                    return false;
                }
                if (exception instanceof UnknownHostException) {// 目标服务器不可达
                    return false;
                }
                if (exception instanceof ConnectTimeoutException) {// 连接被拒绝
                    return false;
                }
                if (exception instanceof SSLException) {// SSL握手异常
                    return false;
                }

                HttpClientContext clientContext = HttpClientContext.adapt(context);
                HttpRequest request = clientContext.getRequest();
                // 如果请求是幂等的，就再次尝试
                if (!(request instanceof HttpEntityEnclosingRequest)) {
                    return true;
                }
                return false;
            }
        };

        // 配置请求的超时设置
        RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(connectionRequestTimeout).setConnectTimeout(connectTimeout).setSocketTimeout(socketTimeout).build();
        newHttpclient = HttpClients.custom().setConnectionManager(cm).setDefaultRequestConfig(requestConfig).setRetryHandler(httpRequestRetryHandler).build();
        return newHttpclient;
    }

    public static HttpClient getHttpsClient() throws Exception {
        if (httpclient != null) {
            return httpclient;
        }
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            FileInputStream instream = new FileInputStream(new File("d:\\tomcat.keystore"));
            try {
                // 加载keyStore d:\\tomcat.keystore
                trustStore.load(instream, "123456".toCharArray());
            } catch (CertificateException e) {
                e.printStackTrace();
            } finally {
                try {
                    instream.close();
                } catch (Exception ignore) {
                }
            }

            // 相信自己的CA和所有自签名的证书
            SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(trustStore, new TrustSelfSignedStrategy()).build();
            // 只允许使用TLSv1协议
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, new String[]{"TLSv1"}, null,
                    SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
            httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
            // 创建http请求(get方式)
            HttpGet httpget = new HttpGet("https://localhost:8443/myDemo/Ajax/serivceJ.action");
            System.out.println("executing request" + httpget.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(httpget);
            try {
                HttpEntity entity = response.getEntity();
                System.out.println("----------------------------------------");
                System.out.println(response.getStatusLine());
                if (entity != null) {
                    System.out.println("Response content length: " + entity.getContentLength());
                    System.out.println(EntityUtils.toString(entity));
                    EntityUtils.consume(entity);
                }
            } finally {
                response.close();
            }

        } catch (ParseException e) {
            e.printStackTrace();
        } catch (
                IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } finally {
            if (httpclient != null) {
                try {
                    httpclient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return httpclient;
    }

    @SuppressWarnings("resource")
    public static String doPost(String url, String json, String charset) {
        HttpClient httpClient = null;
        HttpPost httpPost = null;
        String result = null;
        try {
            httpClient = new SSLClient();
            httpPost = new HttpPost(url);
            httpPost.addHeader("Content-Type", "application/json");
            StringEntity se = new StringEntity(json);
            se.setContentType("text/json");
            se.setContentEncoding(new BasicHeader("Content-Type", "application/json"));
            httpPost.setEntity(se);
            HttpResponse response = httpClient.execute(httpPost);
            if (response != null) {
                HttpEntity resEntity = response.getEntity();
                if (resEntity != null) {
                    result = EntityUtils.toString(resEntity, charset);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

    public static HttpUriRequest getRequestMethod(Map<String, String> map, String url, String method) {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        Set<Map.Entry<String, String>> entrySet = map.entrySet();
        for (Map.Entry<String, String> e : entrySet) {
            String name = e.getKey();
            String value = e.getValue();
            NameValuePair pair = new BasicNameValuePair(name, value);
            params.add(pair);
        }
        HttpUriRequest reqMethod = null;
        if ("post".equals(method)) {
            reqMethod = RequestBuilder.post().setUri(url)
                    .addParameters(params.toArray(new BasicNameValuePair[params.size()])).build();
        } else if ("get".equals(method)) {
            reqMethod = RequestBuilder.get().setUri(url)
                    .addParameters(params.toArray(new BasicNameValuePair[params.size()])).build();
        }
        return reqMethod;
    }

//必美
//appkey:p2c7ChQb
//secret:0c012307fd5a7dbcadeb8591fd12a835c4d9c004
//token:!*rL9qsOu5s5HXemLpU56jfgX0bypxkr~TpQDAwulG@b454UVkr65JvVHRhBPnKXU@dyz1wpNR6Dr16du80SZiBQ**-

//长鹿化工
//    appkey:ZptyKK97
//    secret:b71fec1fefb7254015aa1ec6d8584d035a3faed7
//    token:!*LVzLvw0w2~dlKASR6NvMX2ydYh8~nnVyiihmEUk2q9T@aJU06Ss1SDE8quHbDZYpoDFLAW@PgIChs~Gddm8U~g**-

//https://api.udinghuo.cn/rs/Orders/getSummaryOrders?appkey=p2c7ChQb&token=!*rL9qsOu5s5HXemLpU56jfgX0bypxkr~TpQDAwulG@b454UVkr65JvVHRhBPnKXU@dyz1wpNR6Dr16du80SZiBQ**-&format=json&sign=4FEC8EDFBE4B6B2E59D2182CB9BB723A

    public static void main(String args[]) throws IOException {
        String result = null;
        String secret = "b71fec1fefb7254015aa1ec6d8584d035a3faed7";
        String url =//"https://api.udinghuo.cn/rs/Pays/getSummaryPayVouchers";
//                "https://api.udinghuo.cn/rs/Pays/getPaymentsByDate";
//                "https://api.udinghuo.cn/rs/Pays/getPayVoucher";
//                "https://api.udinghuo.cn/rs/Pays/getSummaryPayVouchersByDate";
//                "https://api.udinghuo.cn/rs/Products/getProducts";
//                "https://api.udinghuo.cn/rs/Products/getProductsDetail";
//                "https://api.udinghuo.cn/rs/Orders/getOrder";
//        "https://api.udinghuo.cn/rs/Products/getProductClass";
                // UO-263b0621804d1908270004
//                 "https://api.udinghuo.cn/rs/Deliverys/getDeliverys";
//                "https://api.udinghuo.cn/rs/Deliverys/getDeliverysByDate";
//                "https://api.udinghuo.cn/rs/Deliverys/getDeliveryByDeliveryNo";
//                "https://api.udinghuo.cn/rs/Agents/getAgents";
// /rs/Agents/getAgents
//                "https://api.udinghuo.cn/rs/Deliverys/getDeliverysByDate";
                //"https://api.udinghuo.cn/rs/Pays/getPayVoucher";
                //"https://api.udinghuo.cn/rs/Pays/getPaymentsByDate";
                //"https://api.udinghuo.cn/rs/Orders/getSummaryOrders";
                //https://api.udinghuo.cn/rs/Orders/getSummaryOrders;
//                "https://api.udinghuo.cn/rs/Rebates/getRebate";
//                "https://api.udinghuo.cn/rs/Rebates/getRebateRecord";
                "https://api.udinghuo.cn/rs/Rebates/getSummaryRebatesByDate";
//                "https://api.udinghuo.cn/rs/Rebates/getSummaryRebates";
//         "https://api.udinghuo.cn/rs/Orders/getSummaryOrdersByDate";
//        "https://api.udinghuo.cn/rs/Orders/getSummaryOrders";

//                "https://api.udinghuo.cn/rs/Orders/getSummaryOrderLists";
//                "https://api.udinghuo.cn/rs/Rebates/getRebate";
//        "https://api.udinghuo.cn/ws/Rebates/uploadRebate";
//                "https://api.udinghuo.cn/ws/Rebates/uploadRebateReturnProduct";

        Map<String, String> map = new HashMap<String, String>();
        map.put("appkey", "ZptyKK97");
        map.put("token", "!*LVzLvw0w2~dlKASR6NvMX2ydYh8~nnVyiihmEUk2q9T@aJU06Ss1SDE8quHbDZYpoDFLAW@PgIChs~Gddm8U~g**-");
        map.put("format", "json");
//        map.put("cCode", "10101001001");
//        map.put("codes", "10101001033");

        map.put("startdate", "2018-12-11 00:00:00");
        map.put("enddate", "2019-12-31 00:00:00");
        //statusCodes
//        map.put("statusCodes", "NOTDELIVER,DELIVERING,DELIVERED");
//        map.put("pageSize", "100");
//        map.put("pageIndex", "1");
//        map.put("agenterpcode", "57906318886542");
//        map.put("pageSize", "3");
//        map.put("orderno", "UO-32a4449181511912230002");
//        map.put("payno", "UF-32a4449181511912190001");
//        map.put("deliveryNo", "UD-32a4449181511912230003");

//        map.put("rebateno", "UF-32a4449181511912190001");
        //{"cUseWayCode": "TOPRODUCT","fRebateMoney": 0.1,"cRebateStatus": "NOTCONFIRM","dValidStartDate": "2019-12-19","dValidEndDate": "9999-12-31","cMemo": "测试上传返利用例"}
//        map.put("rebate", "{\n" +
//                "\t\"cUseWayCode\": \"TOPRODUCT\",\n" +
//                "\t\"fRebateMoney\": 0.1,\n" +
//                "\t\"cRebateStatus\": \"NOTCONFIRM\",\n" +
//                "\t\"dValidStartDate\": \"2019-12-19\",\n" +
//                "\t\"dValidEndDate\": \"9999-12-31\",\n" +
//                "\t\"cMemo\": \"测试上传返利用例\",\n" +
//                "\t\"cOutSysKey\": \"ZptyKK97\",\n" +
//                "\t\"cAgentErpCode\": \"cAgentErpCodeABC274\"\n" +
//                "}");

        //cAgentErpCode 客户档案的erp编码必填
//        map.put("rebate", "{\"cUseWayCode\": \"TOPRODUCT\",\"fRebateMoney\": 0.1,\"cRebateStatus\": \"NOTCONFIRM\",\"dValidStartDate\": \"2019-12-19\",\"dValidEndDate\": \"9999-12-31\",\"cMemo\": \"测试上传返利用例\"}\n");
//        map.put("rebate", "");

        String sign = "";
                //YonyouCloudApiConfigUtil.getSign(map);
        System.out.println(sign);
        map.put("sign", sign);

        HttpClient client = getHttpClient();

        HttpUriRequest method = getRequestMethod(map, url, "get");
//        HttpUriRequest method = getRequestMethod(map, url, "post");

        HttpResponse response = client.execute(method);
        System.out.println(response);
        if (response != null) {
            HttpEntity resEntity = response.getEntity();
            if (resEntity != null) {
                result = EntityUtils.toString(resEntity, "utf-8");
            }
        }
        System.out.println(result);

//        JSONArray jArray = new JSONArray();
//        jArray.add(map);
//        String str = jArray.toString();
//        doPost(url, str, "utf-8");
    }
}
