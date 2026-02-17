package one.axim.framework.rest.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by dudgh on 2017. 6. 17..
 */
@Component
public class XRestEnvironment {

    private static volatile XRestEnvironment instance;

    private final org.springframework.core.env.Environment springEnvironment;
    private final String serverIp;
    private final String serverHostName;

    @Autowired
    public XRestEnvironment(org.springframework.core.env.Environment environment) {
        this.springEnvironment = environment;

        String ip = "";
        String hostName = "";
        try {
            InetAddress addr = InetAddress.getLocalHost();
            ip = addr.getHostAddress();
            hostName = addr.getHostName();
        } catch (UnknownHostException ignored) {
        }
        this.serverIp = ip;
        this.serverHostName = hostName;

        instance = this;
    }

    public static XRestEnvironment getInstance() {
        return instance;
    }

    public Integer getIntValue(String name) {
        return this.springEnvironment.getProperty(name, Integer.class);
    }

    public String getValue(String name) {
        return this.springEnvironment.getProperty(name);
    }

    public boolean getBooleanValue(String name) {
        return Boolean.TRUE.equals(this.springEnvironment.getProperty(name, Boolean.class));
    }

    public String getServerIp() {
        return serverIp;
    }

    public String getServerHostName() {
        return serverHostName;
    }

    public boolean isDevelop() {
        try {
            String profile = getActiveProfile();
            return "develop".equals(profile);
        } catch (Exception e) {
            return false;
        }
    }

    public String getActiveProfile() {
        String[] profiles = this.springEnvironment.getActiveProfiles();
        return (profiles != null && profiles.length > 0) ? profiles[0] : null;
    }

    public String resolvePlaceholders(String text) {
        return this.springEnvironment.resolveRequiredPlaceholders(text);
    }
}