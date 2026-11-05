package com.jjinmak.recommend.in;

import com.jjinmak.recommend.app.RecommendFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecommendEventListener {

    private final RecommendFacade recommendFacade;
}
