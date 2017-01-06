package util;

import com.sforce.async.AsyncApiException;
import com.sforce.async.BulkConnection;
import com.sforce.soap.enterprise.Connector;
import com.sforce.soap.enterprise.EnterpriseConnection;
import com.sforce.soap.enterprise.LoginResult;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

public class ConnectionUtil {
	
	public static final String API_VERSION = "38.0";
	
	private static final String LOGIN_URL = "https://login.salesforce.com/services/Soap/c/" + API_VERSION;

    public static EnterpriseConnection createEPC() throws ConnectionException {
        ConnectorConfig config = getConfig();
        return Connector.newConnection(config);
    }
    
    public static BulkConnection createBulk() throws ConnectionException, AsyncApiException {
    	ConnectorConfig config = new ConnectorConfig();
    	LoginResult loginResult = loginToSalesforce();
    	config.setRestEndpoint("https://ap4.salesforce.com/services/async/" + API_VERSION);
        config.setSessionId(loginResult.getSessionId());
    	return new BulkConnection(config);
    }
    
    public static MetadataConnection createMetadata() throws ConnectionException {
    	ConnectorConfig config = new ConnectorConfig();
    	LoginResult loginResult = loginToSalesforce();
    	config.setServiceEndpoint(loginResult.getMetadataServerUrl());
        config.setSessionId(loginResult.getSessionId());
        return new MetadataConnection(config);
    }

    private static ConnectorConfig getConfig() {
        String userName = System.getProperty("SF_USER");
        String password = System.getProperty("SF_PASSWORD");
        ConnectorConfig config = new ConnectorConfig();
        config.setUsername(userName);
        config.setPassword(password);
        return config;
    }
    
    private static LoginResult loginToSalesforce() throws ConnectionException {
    	String userName = System.getProperty("SF_USER");
        String password = System.getProperty("SF_PASSWORD");
        final ConnectorConfig config = new ConnectorConfig();
        config.setAuthEndpoint(LOGIN_URL);
        config.setServiceEndpoint(LOGIN_URL);
        config.setManualLogin(true);
        return (new EnterpriseConnection(config)).login(userName, password);
    }

}
