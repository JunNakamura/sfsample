import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sforce.async.AsyncApiException;
import com.sforce.async.BatchInfo;
import com.sforce.async.BatchInfoList;
import com.sforce.async.BulkConnection;
import com.sforce.async.ConcurrencyMode;
import com.sforce.async.ContentType;
import com.sforce.async.JobInfo;
import com.sforce.async.OperationEnum;
import com.sforce.async.QueryResultList;
import com.sforce.ws.ConnectionException;

import util.ConnectionUtil;

public class BulkChunkSample {
	
	public static void main(String[] args) throws ConnectionException, AsyncApiException, InterruptedException, ExecutionException, IOException {
		BulkConnection connection = ConnectionUtil.createBulk();
		JobInfo job = createJob(connection);
		System.out.println("jobId: " + job.getId());
		String query = "select Id, Name, Phone from Account";
		// PK-chunkが有効の場合、クエリ全体を処理するためのバッチが自動で追加されるので、最初のバッチは実行されない.
		createBatch(job, connection, query);
		
		// クエリ全体を処理するために追加されたバッチの情報を取得
		List<BatchInfo> batchList = getChunkedBatch(job, connection);
		
		System.out.println("chunked batch size : " + batchList.size());
		
		// バッチごとにステータスチェック+結果の取得を、非同期に行う
		ExecutorService executor = Executors.newFixedThreadPool(batchList.size());
		CompletionService<CompletableFuture<ChunkBatchResult>> completionService = new ExecutorCompletionService<>(executor);
		for (BatchInfo chunkBatch: batchList) {
			completionService.submit(() -> BulkChunkSample.getResultIds(job, connection, chunkBatch));
		}
		
		System.out.println("submitting is done.");
		
		// 終わったものから結果を取得
		for (int i = 0; i < batchList.size(); i++) {
			CompletableFuture<ChunkBatchResult> resultIds = completionService.take().get();
			// XXX getした方が見通しは良さそう。効率的にはwhenCompleteの方がいいはず
			resultIds.whenComplete((batchResult, thrown) -> {
				System.out.println("--- results --- : batchId:" + batchResult.batchInfo.getId());
				// XXX とりあえず標準出力. マルチスレッドなので、順番はでたらめになる
				// 本当はひとつのファイルに書き込むなどして、結果を集約させる.
				for (String resultId : batchResult.resultIds) {
					try {
						InputStream is = connection.getQueryResultStream(job.getId(), batchResult.batchInfo.getId(), resultId);
						try(BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
							String line = null;
							while((line = br.readLine()) != null) {
								System.out.println(line);
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					} catch (AsyncApiException e) {
						e.printStackTrace();
					} 
				}
				System.out.println("--- end --- : batchId:" + batchResult.batchInfo.getId());
			});
		}
		// ジョブ内のバッチを終了させるのと、モニタリングのため
		connection.closeJob(job.getId());
	}
	
	private static JobInfo createJob(BulkConnection connection) throws AsyncApiException {
		JobInfo job = new JobInfo();
		job.setObject("Account");
		job.setOperation(OperationEnum.query);
		job.setConcurrencyMode(ConcurrencyMode.Parallel);
		job.setContentType(ContentType.CSV);
		connection.addHeader("Sforce-Enable-PKChunking", "chunksize=5"); // もとのデータがすくないため、わざと分割の単位を小さくする
		job = connection.createJob(job);
		assert job.getId() != null;
		return connection.getJobStatus(job.getId());
	}
	
	private static BatchInfo createBatch(JobInfo job, BulkConnection connection, String query) throws AsyncApiException {
		ByteArrayInputStream bout = new ByteArrayInputStream(query.getBytes());
		return connection.createBatchFromStream(job, bout);
	}
	
	private static List<BatchInfo> getChunkedBatch(JobInfo job, BulkConnection connection) throws AsyncApiException, InterruptedException {
		System.out.println("--- getting chunk batch list ---");
		// PK-chunkが有効の場合、クエリ全体を処理するためのバッチが自動で追加される
		// 最初のバッチがNotProcessedになった後にそれらが実行されるので、それまで遅延させる.
		TimeUnit.SECONDS.sleep(5);
		BatchInfoList infoList = connection.getBatchInfoList(job.getId());
		List<BatchInfo> _infoList = new ArrayList<>(Arrays.asList(infoList.getBatchInfo()));
		_infoList.remove(0);
		if (_infoList.isEmpty()) {
			System.out.println("--- retrying chunk batch list ---");
			// XXX 一回だけretryさせる
			TimeUnit.SECONDS.sleep(5);
			infoList = connection.getBatchInfoList(job.getId());
			_infoList = new ArrayList<>(Arrays.asList(infoList.getBatchInfo()));
		}
		return _infoList;
	}
	
	private static CompletableFuture<ChunkBatchResult> getResultIds(JobInfo job, BulkConnection connection, BatchInfo chunkBatch) {
		System.out.println("getting resultIds for " + chunkBatch.getId());
		ScheduledExecutorService checkBatchStatus = Executors.newSingleThreadScheduledExecutor();
		CompletableFuture<ChunkBatchResult> result = new CompletableFuture<>();
		checkBatchStatus.scheduleAtFixedRate(() -> {
			System.out.println("--- checking batch status --- : " + chunkBatch.getId());
			try {
				BatchInfo info = connection.getBatchInfo(job.getId(), chunkBatch.getId());
				switch (info.getState()) {
				case Completed:
					QueryResultList queryResults = connection.getQueryResultList(job.getId(), chunkBatch.getId());
					result.complete(ChunkBatchResult.newInstance(chunkBatch, queryResults.getResult()));
					break;
				case Failed:
					System.out.println("batch:" + chunkBatch.getId() + " failed.");
					result.complete(ChunkBatchResult.newInstance(chunkBatch, new String[]{}));
					break;
				default:
					System.out.println("-- waiting --");
					System.out.println("state: " + info.getState());
				}
			} catch (AsyncApiException e) {
				result.completeExceptionally(e);
			}
		}, 1, 15, TimeUnit.SECONDS);
		
		result.whenComplete((results, thrown) -> {
			checkBatchStatus.shutdownNow();
			System.out.println("--- batch is done. --- : " + chunkBatch.getId());
		});
		
		// バッチ完了後に結果を取得
		return result;
	}
	
	private static class ChunkBatchResult {
		
		public final BatchInfo batchInfo;
		
		public final String[] resultIds;
		
		public static ChunkBatchResult newInstance(BatchInfo batchInfo, String[] resultIds) {
			 return new ChunkBatchResult(batchInfo, resultIds);
		 }
		
		private ChunkBatchResult(BatchInfo batchInfo, String[] resultIds) {
			this.batchInfo = batchInfo;
			this.resultIds = resultIds;
		}
		 
		 
		
		
	}
	
	

}
