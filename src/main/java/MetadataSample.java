import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sforce.soap.metadata.CustomObject;
import com.sforce.soap.metadata.Metadata;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.metadata.ReadResult;
import com.sforce.ws.ConnectionException;

import util.ConnectionUtil;

/**
 * Metadata APIのサンプル.
 * @author nakamura_jun
 *
 */
public class MetadataSample {
	
	private static final Logger logger = LoggerFactory.getLogger(MetadataSample.class);

    public static void main(String[] args) throws ConnectionException {
    	// CustomObjectのmetadataを取得
        MetadataConnection connection = ConnectionUtil.createMetadata();
        ReadResult readResult = connection.readMetadata("CustomObject", new String[]{"test__c"});
        Metadata[] metadata = readResult.getRecords();
        System.out.println("records: " + metadata.length);
        for (Metadata data: metadata) {
        	if (data != null) {
        		logger.info("metadata: " + data);
                CustomObject co = (CustomObject) data;
                logger.info("co: " + co);
        	} else {
        		logger.info("Empty metadata.");
        	}
            
        }
    }

}
