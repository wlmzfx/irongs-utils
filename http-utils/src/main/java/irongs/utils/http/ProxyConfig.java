package irongs.utils.http;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class ProxyConfig {
    private String proxyHost;
    private Integer proxyPort;

    public ProxyConfig(String proxyHost, Integer proxyPort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    /***
     * Read the proxy configuration from the system environment variables if not defined.
     */
    public Proxy getHttpProxy() {
        if (proxyHost == null || proxyHost.isEmpty()) {
            proxyHost = System.getProperty("system.proxy.host");
            final String systemProxyPort = System.getProperty("system.proxy.port");
            if (systemProxyPort != null && !systemProxyPort.isEmpty()) {
                proxyPort = Integer.parseInt(systemProxyPort);
            }
        }

        if (proxyHost != null) {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
        }

        return null;
    }

}
