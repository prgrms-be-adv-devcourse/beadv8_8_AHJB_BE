package com.jjinmak.notification.app;

import com.jjinmak.notification.out.push.PushMessage;

import java.util.List;

public record PushTarget(PushMessage message, List<String> tokens) {}