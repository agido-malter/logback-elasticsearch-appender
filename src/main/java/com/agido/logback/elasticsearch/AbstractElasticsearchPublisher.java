package com.agido.logback.elasticsearch;

import ch.qos.logback.core.Context;
import com.agido.logback.elasticsearch.config.ElasticsearchProperties;
import com.agido.logback.elasticsearch.config.HttpRequestHeaders;
import com.agido.logback.elasticsearch.config.Property;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.util.AbstractPropertyAndEncoder;
import com.agido.logback.elasticsearch.util.ErrorReporter;
import com.agido.logback.elasticsearch.writer.ElasticsearchWriter;
import com.agido.logback.elasticsearch.writer.LoggerWriter;
import com.agido.logback.elasticsearch.writer.StdErrWriter;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractElasticsearchPublisher<T> implements Runnable {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);
    private static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        }
    };
    private ThreadLocal<DateFormat> customDateFormat=null;

    public static final String THREAD_NAME_PREFIX = "es-writer-";


    private volatile List<T> events;
    private ElasticsearchOutputAggregator outputAggregator;
    private List<AbstractPropertyAndEncoder<T>> propertyList;

    private AbstractPropertyAndEncoder<T> indexPattern;
    private JsonFactory jf;
    private JsonGenerator jsonGenerator;

    private ErrorReporter errorReporter;
    protected Settings settings;

    private final Object lock;

    private volatile boolean working;

    private final PropertySerializer propertySerializer;

    private Thread thread;

    public AbstractElasticsearchPublisher(Context context, ErrorReporter errorReporter, Settings settings, ElasticsearchProperties properties, HttpRequestHeaders headers) throws IOException {
        this.errorReporter = errorReporter;
        this.events = new ArrayList<T>();
        this.lock = new Object();
        this.settings = settings;

        this.outputAggregator = configureOutputAggregator(settings, errorReporter, headers);

        this.jf = buildJsonFactory(settings);

        this.jf.setRootValueSeparator(null);
        this.jsonGenerator = jf.createGenerator(outputAggregator);

        this.indexPattern = buildPropertyAndEncoder(context, new Property("<index>", settings.getIndex(), false));
        this.propertyList = generatePropertyList(context, properties);

        this.propertySerializer = new PropertySerializer();

        if(this.settings.getTimestampFormat()!=null && !"".equals(this.settings.getTimestampFormat())&& !"long".equals(this.settings.getTimestampFormat())){
            final String f=this.settings.getTimestampFormat();
            this.customDateFormat =new ThreadLocal<DateFormat>() {
                @Override
                protected DateFormat initialValue() {
                    return new SimpleDateFormat(f);
                }
            };
        }

    }

    public void close() {
        if (thread != null) {
            thread.interrupt();
        }
    }

    private JsonFactory buildJsonFactory(Settings settings) {
        if (settings.isObjectSerialization()) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            return mapper.getFactory();
        }
        return new JsonFactory();
    }

    protected ElasticsearchOutputAggregator configureOutputAggregator(Settings settings, ErrorReporter errorReporter, HttpRequestHeaders httpRequestHeaders) {
        ElasticsearchOutputAggregator spigot = new ElasticsearchOutputAggregator(settings, errorReporter);

        if (settings.isLogsToStderr()) {
            spigot.addWriter(new StdErrWriter());
        }

        if (settings.getLoggerName() != null) {
            spigot.addWriter(new LoggerWriter(settings.getLoggerName()));
        }

        if (settings.getUrl() != null) {
            spigot.addWriter(new ElasticsearchWriter(errorReporter, settings, httpRequestHeaders));
        }

        return spigot;
    }

    private List<AbstractPropertyAndEncoder<T>> generatePropertyList(Context context, ElasticsearchProperties properties) {
        List<AbstractPropertyAndEncoder<T>> list = new ArrayList<AbstractPropertyAndEncoder<T>>();
        if (properties != null) {
            for (Property property : properties.getProperties()) {
                list.add(buildPropertyAndEncoder(context, property));
            }
        }
        return list;
    }

    protected abstract AbstractPropertyAndEncoder<T> buildPropertyAndEncoder(Context context, Property property);

    public void addEvent(T event) {
        if (!outputAggregator.hasOutputs()) {
            return;
        }

        synchronized (lock) {
            events.add(event);
            if (!working) {
                working = true;
                thread = new Thread(this, THREAD_NAME_PREFIX + THREAD_COUNTER.getAndIncrement());
                thread.start();
            }
        }
    }

    public void run() {
        int currentTry = 1;
        int maxRetries = settings.getMaxRetries();
        while (true) {
            try {
                try {
                    Thread.sleep(settings.getSleepTime());
                } catch (InterruptedException e) {
                    // we are waking up the thread
                }

                List<T> eventsCopy = null;
                synchronized (lock) {
                    if (!events.isEmpty()) {
                        eventsCopy = events;
                        events = new ArrayList<T>();
                        currentTry = 1;
                    }

                    if (eventsCopy == null) {
                        if (!outputAggregator.hasPendingData()) {
                            // all done
                            working = false;
                            return;
                        } else {
                            // Nothing new, must be a retry
                            if (currentTry > maxRetries) {
                                // Oh well, better luck next time
                                working = false;
                                return;
                            }
                        }
                    }
                }

                if (eventsCopy != null) {
                    serializeEvents(jsonGenerator, eventsCopy, propertyList);
                }

                if (!outputAggregator.sendData()) {
                    currentTry++;
                }

            } catch (Exception e) {
                errorReporter.logError("Internal error handling log data: " + e.getMessage(), e);
                currentTry++;
            }
        }
    }


    private void serializeEvents(JsonGenerator gen, List<T> eventsCopy, List<AbstractPropertyAndEncoder<T>> propertyList) throws IOException {
        for (T event : eventsCopy) {
            serializeIndexString(gen, event);
            gen.writeRaw('\n');
            serializeEvent(gen, event, propertyList);
            gen.writeRaw('\n');
        }
        gen.flush();
    }

    private void serializeIndexString(JsonGenerator gen, T event) throws IOException {
        gen.writeStartObject();
        gen.writeObjectFieldStart(settings.getOperation().name());
        gen.writeObjectField("_index", indexPattern.encode(event));
        String type = settings.getType();
        if (type != null) {
            gen.writeObjectField("_type", type);
        }
        gen.writeEndObject();
        gen.writeEndObject();
    }

    private void serializeEvent(JsonGenerator gen, T event, List<AbstractPropertyAndEncoder<T>> propertyList) throws IOException {
        try {
            gen.writeStartObject();

            serializeCommonFields(gen, event);

            for (AbstractPropertyAndEncoder<T> pae : propertyList) {
                propertySerializer.serializeProperty(gen, event, pae);
            }
        } finally {
            gen.writeEndObject();
        }
    }

    protected abstract void serializeCommonFields(JsonGenerator gen, T event) throws IOException;

    protected  Object getTimestamp(long timestamp) {
        if(settings.getTimestampFormat()!=null && "long".equals(settings.getTimestampFormat())){
            return Long.valueOf(timestamp);
        }
        if(this.customDateFormat!=null){
            return this.customDateFormat.get().format(new Date(timestamp));
        }
        return DATE_FORMAT.get().format(new Date(timestamp));
    }

}
