package com.alastbing.covid19.service.impl;

import com.alastbing.covid19.service.Covid19Service;
import com.alastbing.covid19.utils.FeedResult;
import com.alastbing.covid19.utils.HttpClientUtil;
import com.alibaba.fastjson.JSONArray;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.util.*;

@Service("covid19Service")
public class Covid19ServiceImpl implements Covid19Service {

    private final static String dxyUrl = "https://ncov.dxy.cn/ncovh5/view/pneumonia";
    private final static String baiduUrl = "https://voice.baidu.com/act/newpneumonia/newpneumonia";

    @Override
    public FeedResult getDxyData() {
        FeedResult rs = new FeedResult();
        try {
            List<Object> list = new ArrayList<>();
            Map<String, Object> baiduPageMap = new HashMap<>();
            Map<String, String> map = new HashMap<String, String>();
            HttpClient client = HttpClientUtil.getHttpClient();
            HttpUriRequest method = HttpClientUtil.getRequestMethod(map, dxyUrl, "get");
            HttpResponse response = client.execute(method);
            if (response != null) {
                HttpEntity resEntity = response.getEntity();
                if (resEntity != null) {
                    String result = EntityUtils.toString(resEntity, "utf-8");
                    String[] data = result.split("<script");
                    for (int i = 0; i < data.length; i++) {
                        if (data[i].contains("try ")) {
                            String[] subData = data[i].split("try \\{ ");
                            for (int j = 0; j < subData.length; j++) {
                                if (subData[j].contains("window.")) {
                                    String json = subData[j].replace("window.", "{\"");
                                    json = json.replace(" = ", "\":");
                                    json = json.replace("catch(e){}</script>", "");

//                                    System.out.println(json);
                                    list.add(JSONArray.parse(json));
                                }
                            }
                        }
                    }

                    HttpUriRequest baiduMethod = HttpClientUtil.getRequestMethod(map, baiduUrl, "get");
                    HttpResponse baiduResponse = client.execute(baiduMethod);
                    if (baiduResponse != null) {
                        HttpEntity baiduResEntity = baiduResponse.getEntity();
                        if (baiduResEntity != null) {
                            String baiduResult = EntityUtils.toString(baiduResEntity, "utf-8");
                            String[] baiduData = baiduResult.split("<script type=\"application/json\" id=\"captain-config\">");
                            for (int i = 0; i < baiduData.length; i++) {
                                if (baiduData[i].contains("\"page\":")) {
                                    String[] subBaiduData = baiduData[i].split("</script>");
//                                    System.out.println(subBaiduData[0]);
                                    baiduPageMap = (Map<String, Object>) JSONArray.parse(subBaiduData[0]);
                                }
                            }
                        }
                    }
                    baiduPageMap.put("dxyData", list);
                    rs.setMessage("获取数据成功");
                    rs.setResult(baiduPageMap);
                    rs.setErrorCode(200);
                    rs.setStatus(true);
                } else {
                    throw new Exception("远程获取数据出错，请重试。");
                }
            } else {
                throw new Exception("远程获取数据出错，请重试。");
            }
        } catch (Exception e) {
            e.printStackTrace();
            rs.setMessage("获取数据失败：" + e.getMessage());
            rs.setErrorCode(303);
            rs.setStatus(false);
        }
        return rs;
    }
}
