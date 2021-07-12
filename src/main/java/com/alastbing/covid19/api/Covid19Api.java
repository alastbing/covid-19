package com.alastbing.covid19.api;

import com.alastbing.covid19.service.Covid19Service;
import com.alastbing.covid19.utils.FeedResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/client")
public class Covid19Api {

    @Autowired
    private Covid19Service covid19Service;

    @RequestMapping("/covid19")
    @ResponseBody
    public FeedResult getCovid19Data() {
        return covid19Service.getDxyData();
    }
}
