/**
 * Created by nakamura_jun on 2016/12/26.
 */

import com.sforce.soap.enterprise.EnterpriseConnection;
import com.sforce.soap.enterprise.SaveResult;
import com.sforce.soap.enterprise.sobject.Account;
import com.sforce.soap.enterprise.sobject.SObject;
import com.sforce.ws.ConnectionException;
import util.ConnectionUtil;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SObjectの登録のサンプル
 * @author nakamura_jun
 *
 */
public class SObjectSample {
	
	private static final Logger logger = LoggerFactory.getLogger(SObjectSample.class);

    public static void main(String[] args) throws ConnectionException {
        EnterpriseConnection connection = ConnectionUtil.createEPC();

        Account account = new Account();
        account.setName("00test-" + LocalDateTime.now());
        SObject[] so = new SObject[]{account};
        SaveResult[] res = connection.create(so);

        for (SaveResult result: res) {
        	logger.info(result.toString());
        }

    }

}
