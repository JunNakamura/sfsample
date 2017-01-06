import com.sforce.soap.metadata.CustomObject;
import com.sforce.soap.metadata.Metadata;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.metadata.ReadResult;
import com.sforce.ws.ConnectionException;
import util.MetadataLoginUtil;

/**
 * Metadata APIのサンプル.
 * @author nakamura_jun
 *
 */
public class MetadataSample {

    public static void main(String[] args) throws ConnectionException {
    	// CustomObjectのmetadataを取得
        MetadataConnection connection = MetadataLoginUtil.login();
        ReadResult readResult = connection.readMetadata("CustomObject", new String[]{"test__c"});
        Metadata[] metadata = readResult.getRecords();
        System.out.println("records: " + metadata.length);
        for (Metadata data: metadata) {
        	if (data != null) {
        		System.out.println("metadata: " + data);
                CustomObject co = (CustomObject) data;
                System.out.println("co: " + co);
        	} else {
        		System.out.println("Empty metadata.");
        	}
            
        }
    }

}
