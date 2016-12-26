package util;

import com.sforce.soap.enterprise.Connector;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import com.sforce.soap.enterprise.EnterpriseConnection;

/**
 * Created by nakamura_jun on 2016/12/26.
 */
public class ConnectionUtil {

    public static EnterpriseConnection createEPC() throws ConnectionException {
        ConnectorConfig config = getConfig();
        return Connector.newConnection(config);
    }

    private static ConnectorConfig getConfig() {
        String userName = System.getProperty("SF_USER");
        String password = System.getProperty("SF_PASSWORD");
        ConnectorConfig config = new ConnectorConfig();
        config.setUsername(userName);
        config.setPassword(password);
        return config;
    }

}