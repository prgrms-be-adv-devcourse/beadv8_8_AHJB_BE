package com.jjinmak.boundedContext.notification.app;

import com.jjinmak.boundedContext.notification.out.push.PushMessage;

import java.util.List;

public record PushTarget(PushMessage message, List<String> tokens) {}