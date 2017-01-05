import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sforce.async.AsyncApiException;
import com.sforce.async.BatchInfo;
import com.sforce.async.BatchStateEnum;
import com.sforce.async.BulkConnection;
import com.sforce.async.ConcurrencyMode;
import com.sforce.async.ContentType;
import com.sforce.async.JobInfo;
import com.sforce.async.OperationEnum;
import com.sforce.async.QueryResultList;
import com.sforce.ws.ConnectionException;

import util.ConnectionUtil;

public class BulkSample {
	
	public static void main(String[] args) throws ConnectionException, AsyncApiException, InterruptedException, ExecutionException, IOException {
		BulkConnection connection = ConnectionUtil.createBulk();
		JobInfo job = createJob(connection);
		String query = "select Id, Name, Phone from Account";
		BatchInfo batch = createBatch(job, connection, query);
		
		// バッチが完了したかどうかを定期的にステータスを取得するリクエストをすることで判定
		ScheduledExecutorService checkBatchStatus = Executors.newSingleThreadScheduledExecutor();
		CompletableFuture<String[]> result = new CompletableFuture<>();
		checkBatchStatus.scheduleAtFixedRate(() -> {
			System.out.println("--- checking batch status ---");
			try {
				BatchInfo info = connection.getBatchInfo(job.getId(), batch.getId());
				if (info.getState() == BatchStateEnum.Completed) {
					QueryResultList queryResults = connection.getQueryResultList(job.getId(), batch.getId());
					result.complete(queryResults.getResult());
				} else if (info.getState() == BatchStateEnum.Failed) {
					System.out.println("batch:" + batch.getId() + " failed.");
					result.complete(new String[]{});
				} else {
					System.out.println("-- in progress --");
				}
			} catch (AsyncApiException e) {
				result.completeExceptionally(e);
			}
		}, 1, 15, TimeUnit.SECONDS);
		
		result.whenComplete((results, thrown) -> {
			checkBatchStatus.shutdownNow();
			System.out.println("--- batch is done. ---");
		});
		
		// バッチ完了後に結果を取得
		String[] resultIds = result.get();
		System.out.println("--- results ---");
		for (String resultId: resultIds) {
			InputStream is = connection.getQueryResultStream(job.getId(), batch.getId(), resultId);
			// とりあえず標準出力. 実際はファイルとして保存してそれをDBに登録するなど
			try(BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
				String line = null;
				while((line = br.readLine()) != null) {
					System.out.println(line);
				}
			}
			
		}
		
		
		
		
	}
	
	private static JobInfo createJob(BulkConnection connection) throws AsyncApiException {
		JobInfo job = new JobInfo();
		job.setObject("Account");
		job.setOperation(OperationEnum.query);
		job.setConcurrencyMode(ConcurrencyMode.Parallel);
		job.setContentType(ContentType.CSV);
		job = connection.createJob(job);
		assert job.getId() != null;
		return connection.getJobStatus(job.getId());
	}
	
	private static BatchInfo createBatch(JobInfo job, BulkConnection connection, String query) throws AsyncApiException {
		ByteArrayInputStream bout = new ByteArrayInputStream(query.getBytes());
		return connection.createBatchFromStream(job, bout);
	}
	
	

}
