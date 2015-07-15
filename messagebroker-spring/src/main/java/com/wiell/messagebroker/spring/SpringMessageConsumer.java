package com.wiell.messagebroker.spring;

import com.wiell.messagebroker.KeepAliveMessageHandler;
import com.wiell.messagebroker.MessageConsumer;
import com.wiell.messagebroker.MessageHandler;
import com.wiell.messagebroker.ThrottlingStrategy;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.TimeUnit;

public final class SpringMessageConsumer<T> implements InitializingBean {
    private final MessageConsumer.Builder<T> builder;
    private MessageConsumer<T> consumer;

    private boolean blocking = true;
    private int workerCount = 1;
    private Integer retries = null;
    private ThrottlingStrategy throttlingStrategy = new ThrottlingStrategy.ExponentialBackoff(1, TimeUnit.MINUTES);
    private int timeoutSeconds = 600;

    public SpringMessageConsumer(String consumerId, MessageHandler<T> messageHandler) {
        this(consumerId, messageHandler, null);
    }

    public SpringMessageConsumer(String consumerId, KeepAliveMessageHandler<T> keepAliveMessageHandler) {
        this(consumerId, null, keepAliveMessageHandler);
    }

    private SpringMessageConsumer(String consumerId, MessageHandler<T> messageHandler, KeepAliveMessageHandler<T> keepAliveMessageHandler) {
        builder = messageHandler == null
                ? MessageConsumer.builder(consumerId, keepAliveMessageHandler)
                : MessageConsumer.builder(consumerId, messageHandler);


    }

    MessageConsumer<T> getDelegate() {
        return consumer;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public void setThrottlingStrategy(ThrottlingStrategy throttlingStrategy) {
        this.throttlingStrategy = throttlingStrategy;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public void afterPropertiesSet() throws Exception {
        if (blocking && workerCount > 1)
            throw new IllegalArgumentException("A consumer cannot be blocking and have a workerCount greater than one");
        if (workerCount < 1)
            throw new IllegalArgumentException("A consumer must have a workerCount of at least one");
        if (blocking)
            builder.blocking();
        else
            builder.nonBlocking(workerCount);

        ThrottlingStrategy actualThrottlingStrategy = throttlingStrategy == null
                ? ThrottlingStrategy.NO_THROTTLING
                : throttlingStrategy;
        if (retries == null)
            builder.retry(ThrottlingStrategy.NO_THROTTLING);
        else if (retries <= 0)
            builder.neverRetry();
        else
            builder.retry(retries, actualThrottlingStrategy);


        if (timeoutSeconds < 1)
            throw new IllegalArgumentException("A consumer must have a timeoutSeconds of at least one");
        builder.timeout(timeoutSeconds, TimeUnit.SECONDS);

        consumer = builder.build();
    }
}
