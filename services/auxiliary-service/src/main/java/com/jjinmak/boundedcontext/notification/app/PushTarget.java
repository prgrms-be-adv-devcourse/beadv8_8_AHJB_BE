package com.jjinmak.boundedcontext.notification.app;

import com.jjinmak.boundedcontext.notification.out.push.PushMessage;

import java.util.List;

public record PushTarget(PushMessage message, List<String> tokens) {}