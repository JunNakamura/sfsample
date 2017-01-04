import com.sforce.soap.enterprise.EnterpriseConnection;
import com.sforce.soap.enterprise.QueryResult;
import com.sforce.soap.enterprise.sobject.Contact;
import com.sforce.soap.enterprise.sobject.SObject;
import com.sforce.ws.ConnectionException;
import util.ConnectionUtil;

public class QuerySample {

    public static void main(String[] args) throws ConnectionException {
        EnterpriseConnection connection = ConnectionUtil.createEPC();
        // 全てのフィールドが返却されるが、値がセットされるのはselect句で指定されたもののみ
        String sql = "select Id, FirstName, LastName, LastModifiedDate from Contact limit 5";
        QueryResult results = connection.query(sql);
        if (results.getSize() > 0) {
            for (SObject so: results.getRecords()) {
                Contact c = (Contact) so;
                System.out.println("contact:" + c);
            }
        }
        
        System.out.println("-----");
        System.out.println("Custom Objects");
        EnterpriseConnection c2 = ConnectionUtil.createEPC();
        // カスタムオブジェクトの取得. テーブル、カラム名にはsuffixとして`__c`をつける. 標準項目のカラム名はそのままでよい.
        String sql2 = "select id__c, note__c, Name from s1__c";
        QueryResult res = c2.query(sql2);
        if (res.getSize() > 0) {
        	for (SObject so: res.getRecords()) {
                System.out.println("custom object as so:" + so);
            }
        }

    }

}
