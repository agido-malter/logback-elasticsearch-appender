package com.agido.logback.elasticsearch.writer;

import com.agido.logback.elasticsearch.config.HttpRequestHeader;
import com.agido.logback.elasticsearch.config.HttpRequestHeaders;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.util.ErrorReporter;
import org.apache.http.HttpHeaders;

import java.io.*;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;

public class ElasticsearchWriter implements SafeWriter {

    private StringBuilder sendBuffer;

    private ErrorReporter errorReporter;
    private Settings settings;
    private Collection<HttpRequestHeader> headerList;

    private boolean bufferExceeded;
    private boolean compressedTransfer;

    public ElasticsearchWriter(ErrorReporter errorReporter, Settings settings, HttpRequestHeaders headers) {
        this.errorReporter = errorReporter;
        this.settings = settings;
        this.headerList = headers != null && headers.getHeaders() != null
                ? headers.getHeaders()
                : Collections.<HttpRequestHeader>emptyList();

        this.sendBuffer = new StringBuilder();
        compressedTransfer = false;
        for (HttpRequestHeader header : this.headerList) {
            if (header.getName().toLowerCase().equals(HttpHeaders.CONTENT_ENCODING.toLowerCase()) && header.getValue().equals("gzip")) {
                compressedTransfer = true;
                break;
            }
        }
    }

    public void write(char[] cbuf, int off, int len) {
        if (bufferExceeded) {
            return;
        }

        sendBuffer.append(cbuf, off, len);

        if (sendBuffer.length() >= settings.getMaxQueueSize()) {
            errorReporter.logWarning("Send queue maximum size exceeded - log messages will be lost until the buffer is cleared");
            bufferExceeded = true;
        }
    }

    public void sendData() throws IOException {
        if (sendBuffer.length() <= 0) {
            return;
        }

        HttpURLConnection urlConnection = (HttpURLConnection) (settings.getUrl().openConnection());
        try {
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setReadTimeout(settings.getReadTimeout());
            urlConnection.setConnectTimeout(settings.getConnectTimeout());
            urlConnection.setRequestMethod("POST");

            String body = sendBuffer.toString();

            if (!headerList.isEmpty()) {
                for (HttpRequestHeader header : headerList) {
                    urlConnection.setRequestProperty(header.getName(), header.getValue());
                }
            }

            if (settings.getAuthentication() != null) {
                settings.getAuthentication().addAuth(urlConnection, body);
            }

            writeData(urlConnection, body);

            int rc = urlConnection.getResponseCode();
            if (rc != 200) {
                String data = slurpErrors(urlConnection);
                if(rc >= 400 && rc < 500){
                    // no chance to recover form these errors and has to drop the log messages in order to avoid dead loop.
                    errorReporter.logInfo("Send queue cleared - drop log messages due to http 4xx error.");
                    sendBuffer.setLength(0);
                    bufferExceeded = false;
                }
                throw new IOException("Got response code [" + rc + "] from server with data " + data);
            }
        } finally {
            urlConnection.disconnect();
        }

        sendBuffer.setLength(0);
        if (bufferExceeded) {
            errorReporter.logInfo("Send queue cleared - log messages will no longer be lost");
            bufferExceeded = false;
        }
    }

    public boolean hasPendingData() {
        return sendBuffer.length() != 0;
    }

    protected String slurpErrors(HttpURLConnection urlConnection) {
        try (InputStream stream = urlConnection.getErrorStream()) {
            if (stream == null) {
                return "<no data>";
            }

            StringBuilder builder = new StringBuilder();
            try (InputStreamReader reader = new InputStreamReader(stream, "UTF-8")) {
                char[] buf = new char[2048];
                int numRead;
                while ((numRead = reader.read(buf)) > 0) {
                    builder.append(buf, 0, numRead);
                }
            }
            return builder.toString();
        } catch (Exception e) {
            return "<error retrieving data: " + e.getMessage() + ">";
        }
    }

    private void writeData(HttpURLConnection urlConnection, String body) throws IOException {
        if (this.compressedTransfer) {
            try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(urlConnection.getOutputStream()), "UTF-8")) {
                writer.write(body);
                writer.flush();
            }
        } else {
            try (Writer writer = new OutputStreamWriter(urlConnection.getOutputStream(), "UTF-8")) {
                writer.write(body);
                writer.flush();
            }
        }
    }

    public StringBuilder getSendBuffer() {
        return sendBuffer;
    }

    public Settings getSettings() {
        return settings;
    }

    public Collection<HttpRequestHeader> getHeaderList() {
        return headerList;
    }

    public ErrorReporter getErrorReporter() {
        return errorReporter;
    }

    public boolean isBufferExceeded() {
        return bufferExceeded;
    }

    public void setBufferExceeded(boolean bufferExceeded) {
        this.bufferExceeded = bufferExceeded;
    }

}
