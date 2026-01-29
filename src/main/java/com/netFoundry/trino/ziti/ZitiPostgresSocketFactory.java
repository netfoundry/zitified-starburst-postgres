package com.netFoundry.trino.ziti;

import org.openziti.Ziti;
import org.openziti.ZitiContext;
import org.openziti.IdentityConfig;
import org.openziti.net.dns.ZitiDNSManager;
import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Properties;

public class ZitiPostgresSocketFactory extends SocketFactory {
    private static volatile ZitiContext zitiContext;
    private final String interceptedHost;
    private final int targetPort;

    public ZitiPostgresSocketFactory(Properties props) {
        // Read from URL parameters passed through by the Driver
        this.interceptedHost = props.getProperty("pg_host", "zitified-pg");
        String portStr = props.getProperty("pg_port");
        this.targetPort = (portStr != null) ? Integer.parseInt(portStr) : 5432;

        String identityLocation = props.getProperty("ziti_identity");
        // Initialize Ziti Context if not already done
        if (zitiContext == null) {
            synchronized (ZitiPostgresSocketFactory.class) {
                if (zitiContext == null) {
                    try {
                        System.err.println(">>>> [ZITI] Loading Identity: " + identityLocation);
                        IdentityConfig config = IdentityConfig.load(identityLocation);
                        zitiContext = Ziti.newContext(config);
                        // Mandatory settle time for background service discovery
                        Thread.sleep(2500); 
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                }
            }
        }
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        String dialTarget = interceptedHost;
        try {
            // Dynamically lookup the IP registered by Ziti for this host
            InetAddress addr = ZitiDNSManager.INSTANCE.resolve(interceptedHost);
            if (addr != null) {
                dialTarget = addr.getHostAddress();
                System.err.println(">>>> [ZITI DEBUG] Resolved " + interceptedHost + " to " + dialTarget);
            }
        } catch (Exception e) {
            System.err.println(">>>> [ZITI WARN] DNS Resolution failed for " + interceptedHost);
        }

        try {
            System.err.println(">>>> [ZITI DEBUG] Dialing: " + dialTarget + ":" + targetPort);
            Socket s = zitiContext.connect(dialTarget, targetPort);
            if (s == null) throw new IOException("Ziti connect returned null");
            return s;
        } catch (Throwable t) {
            System.err.println(">>>> [ZITI CRITICAL ERROR] Dial failed for " + dialTarget);
            t.printStackTrace(System.err);
            throw new IOException("Ziti connection failed", t);
        }
    }

    @Override public Socket createSocket() throws IOException { return createSocket(interceptedHost, targetPort); }
    @Override public Socket createSocket(InetAddress a, int p) throws IOException { return createSocket(interceptedHost, p); }
    @Override public Socket createSocket(String h, int p, InetAddress lh, int lp) throws IOException { return createSocket(h, p); }
    @Override public Socket createSocket(InetAddress a, int p, InetAddress la, int lp) throws IOException { return createSocket(a.getHostAddress(), p); }
}
