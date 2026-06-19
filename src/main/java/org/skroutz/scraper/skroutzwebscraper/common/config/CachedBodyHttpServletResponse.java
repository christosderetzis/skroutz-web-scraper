package org.skroutz.scraper.skroutzwebscraper.common.config;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class CachedBodyHttpServletResponse extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream cachedBody = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;

    public CachedBodyHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() already called!");
        }
        if (outputStream == null) {
            outputStream = new CachedServletOutputStream(cachedBody, super.getOutputStream());
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() {
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() already called!");
        }
        if (writer == null) {
            writer = new PrintWriter(cachedBody, true);
        }
        return writer;
    }

    public byte[] getBody() {
        return cachedBody.toByteArray();
    }

    private static class CachedServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream cachedBody;
        private final ServletOutputStream original;

        public CachedServletOutputStream(ByteArrayOutputStream cachedBody, ServletOutputStream original) {
            this.cachedBody = cachedBody;
            this.original = original;
        }

        @Override
        public void write(int b) throws IOException {
            cachedBody.write(b);
            original.write(b);
        }

        @Override
        public boolean isReady() {
            return original.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            original.setWriteListener(writeListener);
        }
    }
}
