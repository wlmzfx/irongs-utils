package irongs.utils.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class HttpCall {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private int connectTimeoutInMs = 150000;

    private int readTimeoutInMs = 150000;

    private String proxyHost;
    private Integer proxyPort;
    private Proxy httpProxy;
    private OkHttpClient client = null;

    private Map<String, String> mapping;

    private static final String exclude = "true";
    private static final String EXCLUDE = "exclude";

    private HttpCall() {

    }

    public HttpCall(String proxyHost, Integer proxyPort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        init();
    }

    public HttpCall(HttpCall oldHttpCall, int readTimeoutInMs) {
        this.proxyHost = oldHttpCall.proxyHost;
        this.proxyPort = oldHttpCall.proxyPort;
        this.readTimeoutInMs = readTimeoutInMs;
        init();
    }

    public HttpCall(HttpCall oldHttpCall, int readTimeoutInMs, int connectTimeoutInMs ) {
        this.proxyHost = oldHttpCall.proxyHost;
        this.proxyPort = oldHttpCall.proxyPort;
        this.readTimeoutInMs = readTimeoutInMs;
        this.connectTimeoutInMs = connectTimeoutInMs;
        init();
    }

    public static Map<String, String> addBasicAuthHeader(Map<String, String> headers, String username, String password) {
        if (StringUtils.isEmpty(username)) {
            return headers;
        }
        if (headers == null) {
            headers = new HashMap<>(1);
        }

        String encoding = Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes(StandardCharsets.UTF_8));
        headers.put("Authorization", "Basic " + encoding);
        headers.put(EXCLUDE, exclude);

        return headers;
    }

    public void init() {
        final OkHttpClient.Builder builder = new OkHttpClient().newBuilder();
        if (proxyHost == null || proxyHost.isEmpty()) {
            proxyHost = System.getProperty("system.proxy.host");
            final String systemProxyPort = System.getProperty("system.proxy.port");
            if (systemProxyPort != null && !systemProxyPort.isEmpty()) {
                proxyPort = Integer.parseInt(systemProxyPort);
            }
        }

        if (proxyHost != null) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        } else if (httpProxy != null) {
            builder.proxy(httpProxy);
        }

        builder.connectTimeout(connectTimeoutInMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutInMs, TimeUnit.MILLISECONDS);

        client = builder.build();
    }

    /**
     * Use the OKHttp3 to make a http call.
     * Read https://github.com/square/okhttp on how to use it.
     *
     * @param request
     * @return
     * @throws IOException
     */
    public Response makeCall(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    public void makeCall(Request request, Callback callback) throws IOException {
        client.newCall(request).enqueue(callback);
    }

    private Request.Builder getRequestBuilder(final Map<String, String> headers) {
        final Request.Builder builder = new Request.Builder();
        builder.addHeader("Content-Type", "application/json");
        builder.addHeader(EXCLUDE, exclude);
        if (headers != null) {
            for (String key : headers.keySet()) {

                if (key.toLowerCase().equals("content-type")) {
                    if (headers.get(key) != null) {
                        builder.header(key, headers.get(key));
                    } else {
                        builder.removeHeader(key);
                    }
                } else {
                    builder.addHeader(key, headers.get(key));
                }
            }
        }

        return builder;
    }

    private Request.Builder getFormRequestBuilder(final Map<String, String> headers) {
        final Request.Builder builder = new Request.Builder();
        builder.addHeader(EXCLUDE, exclude);
        if (headers != null) {
            for (String key : headers.keySet()) {

                builder.addHeader(key, headers.get(key));
            }
        }

        return builder;
    }

    public boolean makeHead(String url, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);

        final Request request = builder
                .url(url)
                .head()
                .build();
        final Response response = makeCall(request);
        return response.isSuccessful();
    }

    public String makeGetBase(String url, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);

        final Request request = builder
                .url(url)
                .get()
                .build();
        final Response response = makeCall(request);
        if (response.isSuccessful()) {
            return response.body().string();
        } else {
            throw new HttpCallException(response.code(), response.body() != null ? response.body().string() : response.message());
        }
    }

    public Map<String, Object> makeGet(String url, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);

        final Request request = builder
                .url(url)
                .get()
                .build();
        final Response response = makeCall(request);
        return readValue(response, new TypeReference<Map<String, Object>>() {
        });
    }

    public <T> T makeGet(String url, Map<String, String> headers, Class<T> tClass) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);

        final Request request = builder
                .url(url)
                .get()
                .build();
        final Response response = makeCall(request);
        return readValue(response, tClass);
    }

    public void makeGet(String url, Map<String, String> headers, Callback callback) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);

        final Request request = builder
                .url(url)
                .get()
                .build();
        makeCall(request, callback);
    }

    public Map<String, Object> makePut(String url, String body, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .put(RequestBody.create(null, body))
                .build();
        final Response response = makeCall(request);
        return readValue(response, new TypeReference<Map<String, Object>>() {
        });
    }

    public void makePut(String url, String body, Map<String, String> headers, Callback callback) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .put(RequestBody.create(null, body))
                .build();
        makeCall(request, callback);
    }

    public Map<String, Object> makeFormPost(String url, Map<String, String> body, Map<String, String> headers) throws IOException {

        final Request.Builder builder = getFormRequestBuilder(headers);
        final FormBody.Builder rbodyBuilder = new FormBody.Builder();

        for (String key : body.keySet()) {
            rbodyBuilder.add(key, body.get(key));
        }

        RequestBody rbody = rbodyBuilder.build();

        final Request request = builder
                .url(url)
                .post(rbody)
                .build();
        final Response response = makeCall(request);
        return readValue(response, new TypeReference<Map<String, Object>>() {
        });
    }


    public Map<String, Object> makePost(String url, String body, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .post(RequestBody.create(null, body))
                .build();
        final Response response = makeCall(request);
        return readValue(response, new TypeReference<Map<String, Object>>() {
        });
    }

    public <T> T makePost(String url, String body, Map<String, String> headers, TypeReference<T> typeReference) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .post(RequestBody.create(null, body))
                .build();
        final Response response = makeCall(request);
        return readValue(response, typeReference);
    }

    public Map<String, Object> makePost(String url, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .post(RequestBody.create(null, new byte[0]))
                .build();
        final Response response = makeCall(request);
        return readValue(response, new TypeReference<Map<String, Object>>() {
        });
    }

    public void makePost(String url, String body, Map<String, String> headers, Callback callback) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .post(RequestBody.create(null, body))
                .build();
        makeCall(request, callback);
    }

    public void makeVoidPost(String url, String body, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .post(RequestBody.create(null, body))
                .build();
        makeCall(request);
    }

    public Map<String, Object> makeDelete(String url, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .delete()
                .build();
        final Response response = makeCall(request);
        return readValue(response, new TypeReference<Map<String, Object>>() {
        });
    }

    public <T> T makeDelete(String url, Map<String, String> headers, TypeReference<T> typeReference) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .delete()
                .build();
        final Response response = makeCall(request);
        return readValue(response, typeReference);
    }

//    public Map<String, String> makeBiDelete(String url, Map<String, String> headers) throws IOException {
//        final Request.Builder builder = getRequestBuilder(headers);
//        final Request request = builder
//                .url(url)
//                .delete()
//                .build();
//        final Response response = makeCall(request);
//        return readValue(response, new TypeReference<Map<String, String>>() {
//        });
//    }

    public Map<String, Object> makeDelete(String url, String body, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .delete(RequestBody.create(null, body))
                .build();
        final Response response = makeCall(request);
        return readValue(response, new TypeReference<Map<String, Object>>() {
        });
    }

    public void makeDelete(String url, Map<String, String> headers, Callback callback) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .delete()
                .build();
        makeCall(request, callback);
    }

    public void makeDelete(String url, String body, Map<String, String> headers, Callback callback) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder
                .url(url)
                .delete(RequestBody.create(null, body))
                .build();
        makeCall(request, callback);
    }

    public String makePostBody(String url, String body, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);
        builder.url(url);
        if (StringUtils.isNoneBlank(body)) {
            builder.post(RequestBody.create(null, body));
        }
        final Request request = builder.build();
        final Response response = makeCall(request);
        if (response.isSuccessful()) {
            return response.body().string();
        } else {
            throw new HttpCallException(response.code(), response.body().toString());
        }
    }

    public <T> T makeGet(String url, Map<String, String> headers, TypeReference<T> typeReference) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);

        final Request request = builder
                .url(url)
                .get()
                .build();
        final Response response = makeCall(request);
        return readValue(response, typeReference);
    }


    public <T> T readValue(final Response response, final TypeReference<T> typeReference) throws IOException {
        if (response.isSuccessful()) {
            String body = response.body().string();
            if (!StringUtils.isEmpty(body)) {
                return objectMapper.readValue(body, typeReference);
            } else {
                return null;
            }
        } else {
            throw new HttpCallException(response.code(), response.body() != null ? response.body().string() : response.message());
        }
    }

    public <T> T readValue(final Response response, final Class<T> tClass) throws IOException {
        if (response.isSuccessful()) {
            String body = response.body().string();
            if (!StringUtils.isEmpty(body)) {
                return objectMapper.readValue(body, tClass);
            } else {
                return null;
            }
        } else {
            throw new HttpCallException(response.code(), response.body() != null ? response.body().string() : response.message());
        }
    }

    public Map<String, Object> makeGetStreamWithFileName(String url, Map<String, String> headers) throws IOException {
        final Map<String, Object> result = new HashMap<>();
        final Request.Builder builder = getRequestBuilder(headers);

        final Request request = builder
                .url(url)
                .get()
                .build();
        final Response response = makeCall(request);
        final String disposition = response.header("Content-Disposition");
        if (disposition != null) {
            final String filename = StringUtils.substringBefore(StringUtils.substringAfter(disposition, "filename=\""), "\"");
            if (StringUtils.isNotEmpty(filename)) {
                result.put("fileName", filename);
            }
        }
        result.put("inputStream", response.body().byteStream());
        return result;
    }

    public InputStream makeGetStream(String url, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);

        final Request request = builder
                .url(url)
                .get()
                .build();
        final Response response = makeCall(request);
        final String disposition = response.header("Content-Disposition");
        return response.body().byteStream();
    }

    public Response makeHttpCall(String serviceName, String api, String method, String owner, Object param) throws IOException {
        if (StringUtils.isEmpty(serviceName)) {
            throw new HttpCallException("serviceName can not be null");
        }
        final Map<String, String> header = new HashMap<>(1);
        RequestBody requestBody = null;
        if (Objects.nonNull(param)) {
            requestBody = RequestBody.create(MediaType.parse("application/json"), objectMapper.writeValueAsString(param));
        }
        final String serivceUrl = mapping.get(String.format("%s-service", serviceName));
        if (serivceUrl == null) {
            throw new HttpCallException(String.format("serviceName:%s can not found the property", serviceName));
        }

        final String url = String.format("%s%s", serivceUrl, api);
        if (!StringUtils.isEmpty(owner)) {
            header.put("_secret", owner);
        }
        final Request.Builder builder = getRequestBuilder(header);
        final Request request = builder
                .url(url)
                .method(method.toUpperCase(), requestBody)
                .build();
        return this.makeCall(request);
    }

    public Response makeHttpCall(String serviceName, String api, String method, String owner, Object param, Map<String, String> headers) throws IOException {
        if (StringUtils.isEmpty(serviceName)) {
            throw new HttpCallException("serviceName can not be null");
        }
        final Map<String, String> header = new HashMap<>(1);
        RequestBody requestBody = null;
        if (Objects.nonNull(param)) {
            requestBody = RequestBody.create(MediaType.parse("application/json"), objectMapper.writeValueAsString(param));
        }
        final String serivceUrl = mapping.get(String.format("%s-service", serviceName));
        if (serivceUrl == null) {
            throw new HttpCallException(String.format("serviceName:%s can not found the property", serviceName));
        }

        final String url = String.format("%s%s", serivceUrl, api);
        if (!StringUtils.isEmpty(owner)) {
            header.put("_secret", owner);
            if (headers != null) {
                header.putAll(headers);
            }
        }
        final Request.Builder builder = getRequestBuilder(header);
        final Request request = builder
                .url(url)
                .method(method.toUpperCase(), requestBody)
                .build();
        return this.makeCall(request);
    }

    public Response makeGetResponse(String url, Map<String, String> headers) throws IOException {
        final Request.Builder builder = getRequestBuilder(headers);

        final Request request = builder
                .url(url)
                .get()
                .build();
        final Response response = makeCall(request);
        return response;
    }

    public Map<String, Object> uploadFile(String url, InputStream inputStream, String fileName, Map<String, String> headers) throws IOException {
        final RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, RequestBody.create(MediaType.parse("multipart/form-data"), IOUtils.toByteArray(inputStream)))
                .build();
        final Request.Builder builder = getRequestBuilder(headers);
        final Request request = builder.url(url)
                .post(requestBody)
                .build();
        final Response response = client.newCall(request).execute();


        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

        return readValue(response, new TypeReference<Map<String, Object>>() {
        });

    }

}
