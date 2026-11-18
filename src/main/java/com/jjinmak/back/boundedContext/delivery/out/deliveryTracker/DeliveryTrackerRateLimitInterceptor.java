package com.jjinmak.back.boundedContext.delivery.out.deliveryTracker;

import com.jjinmak.back.global.exception.BusinessException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;

import static com.jjinmak.back.boundedContext.delivery.exception.DeliveryErrorCode.DELIVERY_TRACKING_UNAVAILABLE;

public class DeliveryTrackerRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final Bucket bucket;
    private final Duration maxWait;

    DeliveryTrackerRateLimitInterceptor(Bucket bucket, Duration maxWait){
        this.bucket = bucket;
        this.maxWait = maxWait;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (maxWait.isZero()){
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()){
                throw new BusinessException(DELIVERY_TRACKING_UNAVAILABLE);
            }
        } else {
            try {
                if (!bucket.asBlocking().tryConsume(1, maxWait)){
                    throw new BusinessException(DELIVERY_TRACKING_UNAVAILABLE);
                }
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
                throw new BusinessException(DELIVERY_TRACKING_UNAVAILABLE);
            }
        }
        return execution.execute(request, body);
    }
}
