/**
 * Created by nakamura_jun on 2016/12/26.
 */

import com.sforce.soap.enterprise.EnterpriseConnection;
import com.sforce.soap.enterprise.SaveResult;
import com.sforce.soap.enterprise.UpsertResult;
import com.sforce.soap.enterprise.sobject.Account;
import com.sforce.soap.enterprise.sobject.SObject;
import com.sforce.ws.ConnectionException;
import java.time.LocalDate;
import util.ConnectionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SObjectの登録のサンプル
 * @author nakamura_jun
 *
 */
public class SObjectSample {
	
	private static final Logger logger = LoggerFactory.getLogger(SObjectSample.class);

    public static void main(String[] args) throws ConnectionException, InterruptedException {
        EnterpriseConnection connection = ConnectionUtil.createEPC();

        SObject[] so = new SObject[200];
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Account account = new Account();
            account.setName("000test-upsert-" + i + "-" + LocalDate.now());
            account.setExt_id__c(UUID.randomUUID().toString());
            so[i] = account;
            accounts.add(account);
        }
        
        SaveResult[] res = connection.create(so);

        for (SaveResult result: res) {
        	logger.info(result.toString());
        }
        
        //ランダム要素のためだけ
        Random random = new Random();
        
        // 更新日時が分までなので差を出すために待機
        //TimeUnit.MINUTES.sleep(1);
        
        List<Account> _accounts = new ArrayList<>();
        
        for (Account account: accounts) {
        	account.setActive__c("yes");
        	if (random.nextBoolean()) {
        		//　ランダムで新規作成になるものを混ぜる
        		account.setActive__c("no");
        		account.setExt_id__c(UUID.randomUUID().toString());
        	}
        	_accounts.add(account);
        }
        
        // 200件を超えるために追加
        Account dummy = new Account();
        dummy.setName("00-test-dummy");
        dummy.setExt_id__c(UUID.randomUUID().toString());
        _accounts.add(dummy);
        
        
        UpsertResult[] res2 = connection.upsert("ext_id__c", _accounts.toArray(new Account[_accounts.size()]));
        for (UpsertResult result: res2) {
        	logger.info("upsert: {}", result);
        }
        
        

    }

}
