import com.sforce.async.*;
import com.sforce.ws.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.ConnectionUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class BulkChunkSaveSample {
	
	private static final Logger logger = LoggerFactory.getLogger(BulkChunkSaveSample.class);
	
	public static void main(String[] args) throws ConnectionException, AsyncApiException, InterruptedException, ExecutionException, IOException {
		BulkConnection connection = ConnectionUtil.createBulk();
		JobInfo job = createJob(connection);
		logger.info("jobId: " + job.getId());
		String query = "select Id, Name, Phone from Account";
		// PK-chunkが有効の場合、クエリ全体を処理するためのバッチが自動で追加されるので、最初のバッチは実行されない.
		createBatch(job, connection, query);
		
		// クエリ全体を処理するために追加されたバッチの情報を取得
		List<BatchInfo> batchList = getChunkBatch(job, connection);
		
		logger.info("chunked batch size : " + batchList.size());
		
		// バッチごとにステータスチェック+結果の取得を、非同期に行う
		ExecutorService executor = Executors.newFixedThreadPool(batchList.size());
		CompletionService<CompletableFuture<ChunkBatchResult>> completionService = new ExecutorCompletionService<>(executor);
		for (BatchInfo chunkBatch: batchList) {
			completionService.submit(() -> BulkChunkSaveSample.getResultIds(job, connection, chunkBatch));
		}
		
		logger.info("submitting is done.");
		
		// 終わったものから結果を取得
		for (int i = 0; i < batchList.size(); i++) {
			CompletableFuture<ChunkBatchResult> resultIds = completionService.take().get();
			// XXX getした方が見通しは良さそう。効率的にはwhenCompleteの方がいいはず
			resultIds.whenComplete((batchResult, thrown) -> {
				logger.info("--- results --- : batchId:" + batchResult.batchInfo.getId());
				// XXX とりあえず標準出力. マルチスレッドなので、順番はでたらめになる
				// 本当はひとつのファイルに書き込むなどして、結果を集約させる.
				for (String resultId : batchResult.resultIds) {
					// ラムダ式内では例外スローできないので、try-catch
					try {
						InputStream is = connection.getQueryResultStream(job.getId(), batchResult.batchInfo.getId(), resultId);
						try(BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
							String line = null;
							while((line = br.readLine()) != null) {
								logger.info(line);
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					} catch (AsyncApiException e) {
						e.printStackTrace();
					} 
				}
				logger.info("--- end --- : batchId:" + batchResult.batchInfo.getId());
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
	
	private static List<BatchInfo> getChunkBatch(JobInfo job, BulkConnection connection) throws InterruptedException, ExecutionException {
		logger.info("--- getting chunk batch list ---");
		logger.info("jobId: {}", job.getId());
		// PK-chunkが有効の場合、クエリ全体を処理するためのバッチが自動で追加される
		// 最初のバッチがNotProcessedになった後にそれらが実行されるので、それまで遅延させる.
        ScheduledExecutorService checkBatchStatus = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<List<BatchInfo>> result = new CompletableFuture<>();
        checkBatchStatus.scheduleAtFixedRate(() -> {
            logger.info("--- checking chunk batch status --- : {}", job.getId());
            try {
                BatchInfoList batchInfoList = connection.getBatchInfoList(job.getId());
                List<BatchInfo> infoList = new ArrayList<>(Arrays.asList(batchInfoList.getBatchInfo()));
                BatchInfo batchInfo = infoList.get(0);
                switch (batchInfo.getState()) {
                    case NotProcessed:
                        infoList.remove(0);
                        result.complete(infoList);
                        break;
                    case Failed:
                        logger.warn("batch:" + job.getId() + " failed.");
                        result.complete(Collections.emptyList());
                        break;
                    default:
                        logger.info("-- waiting --");
                        logger.info("state: " + batchInfo.getState());
                }
            } catch (AsyncApiException e) {
                result.completeExceptionally(e);
            }
        }, 5, 15, TimeUnit.SECONDS); //TODO initial delay, periodを設定ファイルへの外だし
        result.whenComplete((results, thrown) -> {
            checkBatchStatus.shutdownNow();
            logger.info("--- batch is done. --- : " + job.getId());
        });
        return result.get();
	}
	
	private static CompletableFuture<ChunkBatchResult> getResultIds(JobInfo job, BulkConnection connection, BatchInfo chunkBatch) {
		logger.info("getting resultIds for " + chunkBatch.getId());
		ScheduledExecutorService checkBatchStatus = Executors.newSingleThreadScheduledExecutor();
		CompletableFuture<ChunkBatchResult> result = new CompletableFuture<>();
		checkBatchStatus.scheduleAtFixedRate(() -> {
			logger.info("--- checking batch status --- : " + chunkBatch.getId());
			try {
				BatchInfo info = connection.getBatchInfo(job.getId(), chunkBatch.getId());
				switch (info.getState()) {
				case Completed:
					QueryResultList queryResults = connection.getQueryResultList(job.getId(), chunkBatch.getId());
					result.complete(ChunkBatchResult.newInstance(chunkBatch, queryResults.getResult()));
					break;
				case Failed:
					logger.warn("batch:" + chunkBatch.getId() + " failed.");
					result.complete(ChunkBatchResult.newInstance(chunkBatch, new String[]{}));
					break;
				default:
					logger.info("-- waiting --");
					logger.info("state: " + info.getState());
				}
			} catch (AsyncApiException e) {
				result.completeExceptionally(e);
			}
		}, 1, 15, TimeUnit.SECONDS);
		
		result.whenComplete((results, thrown) -> {
			checkBatchStatus.shutdownNow();
			logger.info("--- batch is done. --- : " + chunkBatch.getId());
		});
		
		// バッチ完了後に結果を取得
		return result;
	}
	
	/**
	 * 分割されたバッチごとの結果を表すクラス
	 * クエリの結果を受け取るには、resultIdの配列とbatchIdが必要なため作成.
	 * @author nakamura_jun
	 *
	 */
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
