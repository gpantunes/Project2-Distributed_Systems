package tukano.impl.java.servers;

import static java.lang.String.format;
import static tukano.api.java.Result.*;
import static tukano.api.java.Result.ErrorCode.*;
import static tukano.impl.java.clients.Clients.BlobsClients;
import static tukano.impl.java.clients.Clients.UsersClients;
import static utils.DB.getOne;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import tukano.impl.java.servers.data.Following;
import tukano.impl.java.servers.data.Likes;
import tukano.impl.kafka.lib.KafkaPublisher;
import tukano.impl.kafka.lib.KafkaSubscriber;
import tukano.impl.kafka.lib.RecordProcessor;
import tukano.impl.kafka.sync.SyncPoint;
import tukano.api.Short;
import tukano.api.User;
import tukano.api.java.Blobs;
import tukano.api.java.Result;
import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.discovery.Discovery;
import utils.DB;
import utils.Token;

public class RepJavaShorts implements ExtendedShorts, RecordProcessor {
	static final String KAFKA_BROKERS = "kafka:9092";

	static final String TOPIC = "X-SHORTS";

	final SyncPoint<Result<?>> sync;

	private long lastOffset = -1;

	static final String FROM_BEGINNING = "earliest";

	final KafkaPublisher sender;
	final KafkaSubscriber receiver;

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

	AtomicLong counter = new AtomicLong(totalShortsInDatabase());

	private static final long USER_CACHE_EXPIRATION = 3000;
	private static final long SHORTS_CACHE_EXPIRATION = 3000;
	private static final long BLOBS_USAGE_CACHE_EXPIRATION = 10000;

	public RepJavaShorts() {
		this.sync = new SyncPoint<>();
		this.sender = KafkaPublisher.createPublisher(KAFKA_BROKERS);
		this.receiver = KafkaSubscriber.createSubscriber(KAFKA_BROKERS, List.of(TOPIC), FROM_BEGINNING);

		this.receiver.start(false, this);
	}

	static record Credentials(String userId, String pwd) {
		static JavaShorts.Credentials from(String userId, String pwd) {
			return new JavaShorts.Credentials(userId, pwd);
		}
	}

	protected final LoadingCache<Credentials, Result<User>> usersCache = CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofMillis(USER_CACHE_EXPIRATION)).removalListener((e) -> {
			}).build(new CacheLoader<>() {
				@Override
				public Result<User> load(Credentials u) {
					var res = UsersClients.get().getUser(u.userId(), u.pwd());
					if (res.error() == TIMEOUT)
						return error(BAD_REQUEST);
					return res;
				}
			});

	protected final LoadingCache<String, Result<Short>> shortsCache = CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofMillis(SHORTS_CACHE_EXPIRATION)).removalListener((e) -> {
			}).build(new CacheLoader<>() {
				@Override
				public Result<Short> load(String shortId) throws Exception {

					var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
					var likes = DB.sql(query, Long.class);
					return errorOrValue( getOne(shortId, Short.class), shrt -> shrt.copyWith( likes.get(0) ) );
				}
			});

	protected final LoadingCache<String, Map<String,Long>> blobCountCache = CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofMillis(BLOBS_USAGE_CACHE_EXPIRATION)).removalListener((e) -> {
			}).build(new CacheLoader<>() {
				@Override
				public Map<String,Long> load(String __) throws Exception {
					final var QUERY = "SELECT REGEXP_SUBSTRING(s.blobUrl, '^(\\w+:\\/\\/)?([^\\/]+)\\/([^\\/]+)') AS baseURI, count('*') AS usage From Short s GROUP BY baseURI";
					var hits = DB.sql(QUERY, JavaShorts.BlobServerCount.class);

					var candidates = hits.stream().collect( Collectors.toMap( JavaShorts.BlobServerCount::baseURI, JavaShorts.BlobServerCount::count));

					for( var uri : BlobsClients.all() )
						candidates.putIfAbsent( uri.toString(), 0L);

					return candidates;
				}
			});


	@Override
	public void onReceive(ConsumerRecord<String, String> record) {
		Log.info(() -> format("onReceive : key = %s, value = %s\n", record.key(), record.value()));
		var vrs = record.offset();

		if (vrs <= lastOffset) {
			return;
		}

		var parts = record.value().split(",");
		String userId1;
		String userId2;
		String shortId;
		String password;
		String token;
		Long time;
		boolean isFollowing;
		boolean isLiked;

		switch (record.key()) {
			case "createShort":
				userId1 = parts[0];
				password = parts[1];
				time = Long.parseLong(parts[2]);
				sync.setResult(vrs, createShortKafka(userId1, password, time));
				break;
			case "deleteShort":
				shortId = parts[0];
				password = parts[1];
				sync.setResult(vrs, deleteShortKafka(shortId, password));
				break;
			case "follow":
				userId1 = parts[0];
				userId2 = parts[1];
				isFollowing = Boolean.parseBoolean(parts[2]);
				password = parts[3];
				sync.setResult(vrs, followKafka(userId1, userId2, isFollowing, password));
				break;
			case "like":
				shortId = parts[0];
				userId1 = parts[1];
				isLiked = Boolean.parseBoolean(parts[2]);
				password = parts[3];
				sync.setResult(vrs, likeKafka(shortId, userId1, isLiked, password));
				break;
			case "deleteAllShorts":
				userId1 = parts[0];
				password = parts[1];
				token = parts[2];
				sync.setResult(vrs, deleteAllShortsKafka(userId1, password, token));
				break;
		}

		lastOffset = vrs;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		var res = okUser(userId, password);

		if(!res.isOK()){
			return Result.error(res.error());
		}

		var VRS = sender.publish(TOPIC, "createShort", userId + "," + password  + "," + System.currentTimeMillis());
		return  (Result<Short>) sync.waitForResult(VRS);
	}

	public Result<Short> createShortKafka(String userId, String password, long time) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {

			var shortId = format("%s-%d", userId, counter.incrementAndGet());

			var blobUrl = generateBlobUrl(shortId);
			var shrt = new Short(shortId, userId, blobUrl, time, 0);

			return DB.insertOne(shrt);
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null)
			return error(BAD_REQUEST);

		var res = getShortKafka(shortId);

		if(!res.isOK()){
			Log.info(() -> format("getShort : error = %s,\n", res.error()));
		}

		return res;
	}

	protected Result<Short> getShortKafka(String shortId) {
		var res = shortFromCache(shortId);
		if(!res.isOK())
			return error(NOT_FOUND);

		Short vid = res.value();

		var blobUrl = generateBlobUrl(shortId);
		vid.setBlobUrl(blobUrl);

		Result update = verifyConsistency(vid.getBlobUrl(), blobUrl);
		if(!update.isOK())
			return error(INTERNAL_ERROR);

		DB.updateOne(vid);
		return ok(vid);
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : userId = %s, pwd = %s\n", shortId, password));

		var VRS = sender.publish(TOPIC, "deleteShort", shortId + "," + password);
		sync.waitForResult(VRS);
		return ok();
	}

	public Result<Void> deleteShortKafka(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {

			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
				return DB.transaction( hibernate -> {

					shortsCache.invalidate( shortId );
					hibernate.remove( shrt);

					var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
					hibernate.createNativeQuery( query, Likes.class).list().forEach( hibernate::remove);

					BlobsClients.get().delete(shrt.getBlobUrl(), Token.get() );
				});
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : shortId = %s\n", userId));

		if (userId == null)
			return error(BAD_REQUEST);

		var res = getShortsKafka(userId);

		if(!res.isOK()){
			Log.info(() -> format("getShorts : error = %s,\n", res.error()));
		}

		return res;
	}

	public Result<List<String>> getShortsKafka(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
		return errorOrValue( okUser(userId), DB.sql( query, String.class));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		var VRS = sender.publish(TOPIC, "follow", userId1 + "," + userId2 + "," + isFollowing + "," + password);
		sync.waitForResult(VRS);
		return ok();
	}

	public Result<Void> followKafka(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

		return errorOrResult( okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			return errorOrVoid( okUser( userId2), isFollowing ? DB.insertOne( f ) : DB.deleteOne( f ));
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : shortId = %s\n", userId));

		if (userId == null)
			return error(BAD_REQUEST);

		var res = followersKafka(userId, password);

		if(!res.isOK()){
			Log.info(() -> format("likes : error = %s,\n", res.error()));
		}

		return res;
	}

	public Result<List<String>> followersKafka(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
		return errorOrValue( okUser(userId, password), DB.sql(query, String.class));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : userId = %s, pwd = %s\n", shortId, password));

		var VRS = sender.publish(TOPIC, "like", shortId + "," + userId + "," + isLiked + "," + password);
		sync.waitForResult(VRS);
		return ok();
	}

	public Result<Void> likeKafka(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));


		return errorOrResult( getShort(shortId), shrt -> {
			shortsCache.invalidate( shortId );

			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null)
			return error(BAD_REQUEST);

		var res = likesKafka(shortId, password);

		if(!res.isOK()){
			Log.info(() -> format("likes : error = %s,\n", res.error()));
		}

		return res;
	}

	public Result<List<String>> likesKafka(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {

			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

			return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, String.class));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : shortId = %s\n", userId));

		if (userId == null)
			return error(BAD_REQUEST);

		var res = getFeedKafka(userId, password);

		if(!res.isOK()){
			Log.info(() -> format("getFeed : error = %s,\n", res.error()));
		}

		return res;
	}

	public Result<List<String>> getFeedKafka(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		//throw new RuntimeException();

		final var QUERY_FMT = """
				SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'				
				UNION			
				SELECT s.shortId, s.timestamp FROM Short s, Following f 
					WHERE 
						f.followee = s.ownerId AND f.follower = '%s' 
				ORDER BY s.timestamp DESC""";

		return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId, userId), String.class));
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, pwd = %s\n", userId, password));

		var VRS = sender.publish(TOPIC, "deleteAllShorts", userId + "," + password + "," + token);
		sync.waitForResult(VRS);
		return ok();
	}

	public Result<Void> deleteAllShortsKafka(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.matches( token ) )
			return error(FORBIDDEN);

		return DB.transaction( (hibernate) -> {

			usersCache.invalidate( new JavaShorts.Credentials(userId, password) );

			//delete shorts
			var query1 = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
			hibernate.createNativeQuery(query1, Short.class).list().forEach( s -> {
				shortsCache.invalidate( s.getShortId() );
				hibernate.remove(s);
			});

			//delete follows
			var query2 = format("SELECT * FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
			hibernate.createNativeQuery(query2, Following.class).list().forEach( hibernate::remove );

			//delete likes
			var query3 = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
			hibernate.createNativeQuery(query3, Likes.class).list().forEach( l -> {
				shortsCache.invalidate( l.getShortId() );
				hibernate.remove(l);
			});
		});
	}


	protected Result<Short> shortFromCache( String shortId ) {
		try {
			return shortsCache.get(shortId);
		} catch (ExecutionException e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}

	protected Result<User> okUser(String userId, String pwd) {
		try {
			var res = UsersClients.get().getUser(userId, pwd);
			if (res.error() == TIMEOUT)
				return error(BAD_REQUEST);
			return res;
		} catch (Exception x) {
			x.printStackTrace();
			return Result.error(INTERNAL_ERROR);
		}
	}

	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}

	private long totalShortsInDatabase() {
		var hits = DB.sql("SELECT count('*') FROM Short", Long.class);
		return 1L + (hits.isEmpty() ? 0L : hits.get(0));
	}

	private String generateBlobUrl(String shortId) {
		StringBuilder concat = new StringBuilder();

		List<String> uriList = new ArrayList<>();
		var uris = Discovery.getInstance().knownUrisOf("blobs", 0);
		for(URI uri : uris){
			uriList.add(uri.toString());
		}

		for(int i = 0; i < uriList.size(); i++){
			if(i < uriList.size()-1)
				concat.append(format("%s/%s/%s|", uriList.get(i), Blobs.NAME, shortId));
			else concat.append(format("%s/%s/%s", uriList.get(i), Blobs.NAME, shortId));
		}

		return concat.toString();
	}


	private Result verifyConsistency(String originalUrl, String workingUrl) {
		String[] original = originalUrl.split("\\|");
		String[] working = workingUrl.split("\\|");
		String uriToDownload = "";
		List<String> urisToUpdate = new ArrayList<>();

		for(String url : working){
			boolean needUpdate = true;
			for(String check : original){
				if(url.equals(check)) {
					needUpdate = false;
					uriToDownload = check;
					break;
				}
			}
			if(needUpdate){
				urisToUpdate.add(url);
			}
		}

		String[] uriParts = uriToDownload.split("/blobs/");
		String uri = uriParts[0];
		var data = BlobsClients.get(URI.create(uri)).download(uriParts[1]);

		if(data.isOK()){
			byte[] blob = data.value();

			for(String uriToUpdate : urisToUpdate){
				BlobsClients.get(URI.create(uriToUpdate)).upload(uriParts[1], blob);
			}
		}
		return ok();
	}

}