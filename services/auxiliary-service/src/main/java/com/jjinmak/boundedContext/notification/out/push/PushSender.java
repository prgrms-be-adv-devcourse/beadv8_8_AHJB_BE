package com.jjinmak.boundedContext.notification.out.push;

import java.util.List;

public interface PushSender {

    List<PushResult> send(PushMessage message, List<String> tokens);
}