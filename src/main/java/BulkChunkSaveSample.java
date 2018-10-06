import com.sforce.async.*;
import com.sforce.ws.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.ConnectionUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;

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

		// 分割されたクエリ結果をパイプを使って、ひとつのファイルにまとめて、gzip圧縮して保存
        Path resultFile = Paths.get("result.csv.gz");
		try (PipedOutputStream pipedOut = new PipedOutputStream();
             PipedInputStream pipedIn = new PipedInputStream(pipedOut);
             BufferedWriter pipedWriter = new BufferedWriter(new OutputStreamWriter(pipedOut));
             BufferedReader pipedReader = new BufferedReader(new InputStreamReader(pipedIn, StandardCharsets.UTF_8));
             OutputStream os = Files.newOutputStream(resultFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
             GZIPOutputStream gzip = new GZIPOutputStream(os);
             OutputStreamWriter ow = new OutputStreamWriter(gzip, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(ow);) {

            ExecutorService executor = Executors.newFixedThreadPool(batchList.size() + 1);
            // 読み取り用パイプの内容をファイルに書き込みを別スレッドで開始
            executor.submit(() -> {
                try {
                    String line;
                    while ((line = pipedReader.readLine()) != null) {
                        bw.write(line);
                        bw.newLine();
                    }
                } catch (Exception e) {
                    logger.error("Failed.", e);
                }
            });

            // バッチごとにステータスチェック+結果をパイプに書き込み
            for (BatchInfo chunkBatch: batchList) {
                // ネットワークの通信量に制約がなければ非同期で行う.
                // executor.submit(() -> BulkChunkSaveSample.retrieveResult(job, connection, chunkBatch, pipedWriter));
                BulkChunkSaveSample.retrieveResult(job, connection, chunkBatch, pipedWriter);
            }


        } catch (Exception e) {
		    logger.error("Failed.", e);
        } finally {
            // ジョブ内のバッチを終了させるのと、モニタリングのため
            connection.closeJob(job.getId());
        }
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
                        // 先頭以降のバッチがクエリ結果に関するもの
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

	private static void retrieveResult(JobInfo job, BulkConnection connection, BatchInfo chunkBatch, BufferedWriter pipedWriter)  {
        logger.info("getting resultIds for " + chunkBatch.getId());
        try {
            ScheduledExecutorService checkBatchStatus = Executors.newSingleThreadScheduledExecutor();
            CompletableFuture<List<String>> result = new CompletableFuture<>();
            checkBatchStatus.scheduleAtFixedRate(() -> {
                logger.info("--- checking batch status --- : " + chunkBatch.getId());
                try {
                    BatchInfo info = connection.getBatchInfo(job.getId(), chunkBatch.getId());
                    switch (info.getState()) {
                        case Completed:
                            QueryResultList queryResults = connection.getQueryResultList(job.getId(), chunkBatch.getId());
                            result.complete(Arrays.asList(queryResults.getResult()));
                            break;
                        case Failed:
                            logger.warn("batch:" + chunkBatch.getId() + " failed.");
                            result.complete(Collections.emptyList());
                            break;
                        default:
                            logger.info("-- waiting --");
                            logger.info("state: " + info.getState());
                    }
                } catch (AsyncApiException e) {
                    result.completeExceptionally(e);
                }
            }, 1, 15, TimeUnit.SECONDS);


            List<String> resultIds = result.get();
            checkBatchStatus.shutdownNow();
            logger.info("--- batch is done. --- : " + chunkBatch.getId());
            // パイプへの書き込み
            for (String resultId: resultIds) {
                try (InputStream is = connection.getQueryResultStream(job.getId(), chunkBatch.getId(), resultId);
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        pipedWriter.write(line);
                        pipedWriter.newLine();
                    }
                } catch (Exception e) {
                    logger.error("Failed to save result at " + resultId, e);
                    throw  new RuntimeException("Failed to save result");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to save result");
            throw  new RuntimeException(e);
        }

    }

}
