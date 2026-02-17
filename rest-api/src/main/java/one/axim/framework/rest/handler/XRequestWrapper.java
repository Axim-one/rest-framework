package one.axim.framework.rest.handler;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class XRequestWrapper extends HttpServletRequestWrapper {
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024; // 10 MB
    private byte[] bytes;

    public XRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        int contentLength = request.getContentLength();
        if (contentLength > MAX_BODY_SIZE) {
            throw new IOException("Request body too large: " + contentLength + " bytes (max " + MAX_BODY_SIZE + ")");
        }
        bytes = super.getInputStream().readAllBytes();
    }

    public String getRequestBody() {
        if (bytes != null && bytes.length > 0) {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        return null;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        return new BufferedServletInputStream(in, super.getInputStream());
    }

    private static final class BufferedServletInputStream extends ServletInputStream {
        private final ServletInputStream inputStream;
        private ByteArrayInputStream bais;

        public BufferedServletInputStream(ByteArrayInputStream bais, ServletInputStream inputStream) {
            this.bais = bais;
            this.inputStream = inputStream;
        }

        @Override
        public int available() {
            return this.bais.available();
        }

        @Override
        public int read() {
            return this.bais.read();
        }

        @Override
        public int read(byte[] buf, int off, int len) {
            return this.bais.read(buf, off, len);
        }

        @Override
        public boolean isFinished() {
            return bais.available() == 0;
        }

        @Override
        public boolean isReady() {
            return bais.available() > 0;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            inputStream.setReadListener(listener);
        }
    }


}
