import com.sforce.soap.enterprise.EnterpriseConnection;
import com.sforce.soap.enterprise.QueryResult;
import com.sforce.soap.enterprise.sobject.Contact;
import com.sforce.soap.enterprise.sobject.SObject;
import com.sforce.ws.ConnectionException;
import util.ConnectionUtil;

/**
 * Created by nakamura_jun on 2016/12/26.
 */
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

    }

}
