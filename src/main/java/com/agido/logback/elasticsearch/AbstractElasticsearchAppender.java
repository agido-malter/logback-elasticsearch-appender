package com.agido.logback.elasticsearch;

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.agido.logback.elasticsearch.config.Authentication;
import com.agido.logback.elasticsearch.config.ElasticsearchProperties;
import com.agido.logback.elasticsearch.config.HttpRequestHeaders;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.config.Operation;
import com.agido.logback.elasticsearch.util.ErrorReporter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public abstract class AbstractElasticsearchAppender<T> extends UnsynchronizedAppenderBase<T> {

    protected Settings settings;
    protected ElasticsearchProperties elasticsearchProperties;
    protected AbstractElasticsearchPublisher<T> publisher;
    protected ErrorReporter errorReporter;
    protected HttpRequestHeaders headers;

    public AbstractElasticsearchAppender() {
        this.settings = new Settings();
        this.headers = new HttpRequestHeaders();
        registerShutdownHook();
    }

    public AbstractElasticsearchAppender(Settings settings) {
        this.settings = settings;
        registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook()));
    }

    private class ShutdownHook implements Runnable {
        @Override
        public void run() {
            stop();
            if (publisher != null) {
                publisher.close();
            }
        }
    }

    @Override
    public void start() {
        super.start();
        this.errorReporter = getErrorReporter();
        try {
            this.publisher = buildElasticsearchPublisher();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void publishEvent(T eventObject) {
        publisher.addEvent(eventObject);
    }

    //VisibleForTesting
    protected ErrorReporter getErrorReporter() {
        return new ErrorReporter(settings, getContext());
    }

    //VisibleForTesting
    protected abstract AbstractElasticsearchPublisher<T> buildElasticsearchPublisher() throws IOException;

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    protected void append(T eventObject) {
        appendInternal(eventObject);
    }

    protected abstract void appendInternal(T eventObject);

    public void setProperties(ElasticsearchProperties elasticsearchProperties) {
        this.elasticsearchProperties = elasticsearchProperties;
    }

    public void setSleepTime(int sleepTime) {
        settings.setSleepTime(sleepTime);
    }

    public void setMaxRetries(int maxRetries) {
        settings.setMaxRetries(maxRetries);
    }

    public void setConnectTimeout(int connectTimeout) {
        settings.setConnectTimeout(connectTimeout);
    }

    public void setReadTimeout(int readTimeout) {
        settings.setReadTimeout(readTimeout);
    }

    public void setIncludeCallerData(boolean includeCallerData) {
        settings.setIncludeCallerData(includeCallerData);
    }

    public void setErrorsToStderr(boolean errorsToStderr) {
        settings.setErrorsToStderr(errorsToStderr);
    }

    public void setLogsToStderr(boolean logsToStderr) {
        settings.setLogsToStderr(logsToStderr);
    }

    public void setMaxQueueSize(int maxQueueSize) {
        settings.setMaxQueueSize(maxQueueSize);
    }

    public void setIndex(String index) {
        settings.setIndex(index);
    }

    public void setType(String type) {
        settings.setType(type);
    }

    public void setUrl(String url) throws MalformedURLException {
        settings.setUrl(new URL(url));
    }

    public void setLoggerName(String logger) {
        settings.setLoggerName(logger);
    }

    public void setErrorLoggerName(String logger) {
        settings.setErrorLoggerName(logger);
    }

    public void setHeaders(HttpRequestHeaders httpRequestHeaders) {
        this.headers = httpRequestHeaders;
    }

    public void setRawJsonMessage(boolean rawJsonMessage) {
        settings.setRawJsonMessage(rawJsonMessage);
    }
    public void setTimestampFormat(String timestampFormat) {
        settings.setTimestampFormat(timestampFormat);
    }

    public void setIncludeMdc(boolean includeMdc) {
        settings.setIncludeMdc(includeMdc);
    }

    public void setAuthentication(Authentication auth) {
        settings.setAuthentication(auth);
    }

    public void setMaxMessageSize(int maxMessageSize) {
        settings.setMaxMessageSize(maxMessageSize);
    }

    public void setKeyPrefix(String keyPrefix) {
        settings.setKeyPrefix(keyPrefix);
    }

    public void setObjectSerialization(boolean objectSerialization) {
        settings.setObjectSerialization(objectSerialization);
    }

    public void setAutoStackTraceLevel(String level) {
        settings.setAutoStackTraceLevel(level);
    }

    public void setOperation( String operation ) {
        settings.setOperation( Operation.of( operation )
                .orElseGet(
                    () -> {
                        addWarn( "Invalid value provided for [operation] setting, assuming create" );
                        return Operation.create;
                    } )
            );
    }

    public void setIncludeKvp(boolean includeKvp) {
      settings.setIncludeKvp(includeKvp);
    }
}
