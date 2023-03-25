package irongs.utils.http;

import java.io.IOException;

public class HttpCallException extends IOException {

    private int code;

    public HttpCallException(int code, String message) {
        super(message);
        this.code = code;
    }

    public HttpCallException(String message) {
        super(message);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }
}
