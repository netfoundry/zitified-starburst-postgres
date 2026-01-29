package com.netFoundry.trino.ziti;

import io.trino.spi.Plugin;
import io.trino.spi.connector.ConnectorFactory;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Proxy;

public class ZitiPostgresPlugin implements Plugin {
    @Override
    public Iterable<ConnectorFactory> getConnectorFactories() {
        try {
            ClassLoader classLoader = this.getClass().getClassLoader();
            
            // Load the native Postgres plugin logic bundled in this JAR
            Class<?> pgPluginClass = Class.forName("io.trino.plugin.postgresql.PostgreSqlPlugin", true, classLoader);
            Object pgPluginInstance = pgPluginClass.getDeclaredConstructor().newInstance();

            @SuppressWarnings("unchecked")
            Iterable<ConnectorFactory> factories = (Iterable<ConnectorFactory>) pgPluginClass
                    .getMethod("getConnectorFactories")
                    .invoke(pgPluginInstance);

            List<ConnectorFactory> result = new ArrayList<>();
            for (ConnectorFactory originalFactory : factories) {
                // Wrap in a Proxy to change the connector name to 'ziti_postgres'
                // This satisfies the 479 server's naming requirements and avoids conflicts
                ConnectorFactory proxiedFactory = (ConnectorFactory) Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[] { ConnectorFactory.class },
                    (proxy, method, args) -> {
                        if (method.getName().equals("getName")) {
                            return "ziti_postgres";
                        }
                        try {
                            return method.invoke(originalFactory, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                );
                result.add(proxiedFactory);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Ziti-Postgres Bridge Failure: " + e.getMessage(), e);
        }
    }
}