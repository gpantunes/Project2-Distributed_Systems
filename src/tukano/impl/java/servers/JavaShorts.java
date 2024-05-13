package tukano.impl.java.servers;

import static java.lang.String.format;
import static tukano.api.java.Result.ErrorCode.*;
import static tukano.api.java.Result.error;
import static tukano.api.java.Result.errorOrValue;
import static tukano.api.java.Result.ok;
import static tukano.impl.java.clients.Clients.BlobsClients;
import static tukano.impl.java.clients.Clients.UsersClients;

import tukano.api.*;

import static utils.DB.getOne;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import tukano.api.Short;
import tukano.api.java.Result;
import tukano.api.java.Users;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.discovery.Discovery;
import tukano.impl.java.clients.ClientFactory;
import utils.DB;
import utils.Hibernate;

public class JavaShorts implements ExtendedShorts {
	private static final String BLOB_COUNT = "*";

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

	AtomicLong counter = new AtomicLong( totalShortsInDatabase() );
	
	private static final long USER_CACHE_EXPIRATION = 3000;
	private static final long SHORTS_CACHE_EXPIRATION = 3000;
	private static final long BLOBS_USAGE_CACHE_EXPIRATION = 10000;

	Discovery discovery = Discovery.getInstance();
	URI[] blobUris = discovery.knownUrisOf("blobs", 1);
	ClientFactory<Users> client = UsersClients;
	ClientFactory<ExtendedBlobs> blobClient = BlobsClients;

	static record Credentials(String userId, String pwd) {
		static Credentials from(String userId, String pwd) {
			return new Credentials(userId, pwd);
		}
	}

	protected final LoadingCache<Credentials, Result<User>> usersCache = CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofMillis(USER_CACHE_EXPIRATION)).removalListener((e) -> {
			}).build(new CacheLoader<>() {
				@Override
				public Result<User> load(Credentials u) throws Exception {
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
					var hits = DB.sql(QUERY, BlobServerCount.class);
					
					var candidates = hits.stream().collect( Collectors.toMap( BlobServerCount::baseURI, BlobServerCount::count));

					for( var uri : BlobsClients.all() )
						 candidates.putIfAbsent( uri.toString(), 0L);

					return candidates;

				}
			});

	@Override
	public Result<Short> createShort(String userId, String password) {
		var result = client.get().getUser(userId, password);

		if(!result.isOK()) {
			Log.info(String.valueOf(error(result.error())));
			return Result.error(result.error());
		}

		try {
			discovery.addBlobUris(blobUris);
			URI nextUri = discovery.getNextServer();

			String shortId = String.valueOf(UUID.randomUUID());
			String blobId =  nextUri + "/blobs/" + shortId;

			Short vid = new Short(shortId, userId, blobId, System.currentTimeMillis(), 0);

			discovery.updateBlobDistribution(nextUri, 1);

			Hibernate.getInstance().persistOne(vid);

			return ok(vid);
		}catch (Exception e){
			return Result.error(INTERNAL_ERROR);
		}
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		if(badParam(shortId) || badParam(password))
			return error(BAD_REQUEST);

		Result res = getShort(shortId);
		if(!res.isOK())
			return error(NOT_FOUND);

		Short vid = (Short) res.value();

		String ownerId = vid.getOwnerId();

		var result = client.get().getUser(ownerId, password);
		if(!result.isOK())
			return Result.error(result.error());

		Hibernate.getInstance().deleteOne(vid);

		return ok();
	}

	@Override
	public Result<Short> getShort(String shortId) {
		if(badParam(shortId))
			return error(BAD_REQUEST);

		var shortList = Hibernate.getInstance().sql("SELECT * FROM Short WHERE shortId = '" + shortId + "'", Short.class);

		if(shortList.isEmpty())
			return error(NOT_FOUND);

		return ok(shortList.get(0));
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		if(badParam(userId))
			return error(BAD_REQUEST);

		var shortList = Hibernate.getInstance().sql("SELECT * FROM Short WHERE ownerId = '" + userId + "'", Short.class);
		List<String> idList = new ArrayList<>(shortList.size());

		for(int i = 0; i < shortList.size(); i++)
			idList.add(i, shortList.get(i).getShortId());

		return ok(idList);
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		if(badParam(userId1) || badParam(userId2) || badParam(password))
			return error(BAD_REQUEST);

		var result1 = client.get().getUser(userId1, password);
		if(!result1.isOK())
			return Result.error(result1.error());


		var follow = Hibernate.getInstance().sql("SELECT * FROM Follow WHERE followerId = '"
				+ userId1  + "' AND followedId = '" + userId2 + "'", Follow.class);

		if(isFollowing){
			if(follow.isEmpty())
				Hibernate.getInstance().persistOne(new Follow(userId1, userId2));
			else return error(CONFLICT);
		} else {
			if(!follow.isEmpty())
				Hibernate.getInstance().deleteOne(follow.get(0));
		}

		return ok();
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		if(badParam(userId) || badParam(password))
			return error(BAD_REQUEST);

		var result = client.get().getUser(userId, password);

		if(!result.isOK())
			return Result.error(result.error());

		var followerList = Hibernate.getInstance().sql("SELECT * FROM Follow WHERE followedId = '"
				+ userId + "'", Follow.class);

		List<String> idList = new ArrayList<>(followerList.size());

		for(int i = 0; i < followerList.size(); i++)
			idList.add(i, followerList.get(i).getFollowerId());

		return ok(idList);
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info("$$$$$$$$$$$$$$$$$$ shortId " + shortId + " userId " + userId + " isLiked " + isLiked);

		if(badParam(shortId) || badParam(userId) || badParam(password))
			return error(BAD_REQUEST);

		var result = client.get().getUser(userId, password);
		var vid = getShort(shortId).value();
		int totalLikes = vid.getTotalLikes();

		if(!result.isOK())
			return Result.error(result.error());


		var likeList = Hibernate.getInstance().sql("SELECT * FROM Likes WHERE userId = '"
				+ userId + "' AND shortId = '" + shortId + "'" , Likes.class);

		if(isLiked){
			if(!likeList.isEmpty())
				return error(CONFLICT);

            /*var userLikes = Hibernate.getInstance().sql("SELECT * FROM Likes WHERE userId = '"
                    + userId + "'", Likes.class);*/

			Hibernate.getInstance().persistOne(new Likes(userId, shortId));
			vid.setTotalLikes(totalLikes + 1);
			Hibernate.getInstance().updateOne(vid);
		}else {
			if(likeList.isEmpty())
				return error(NOT_FOUND);

			Hibernate.getInstance().deleteOne(likeList.get(0));
			vid.setTotalLikes(totalLikes - 1);
			Hibernate.getInstance().updateOne(vid);
		}
		return ok();
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		if(badParam(shortId) || badParam(password))
			return error(BAD_REQUEST);

		Short vid = getShort(shortId).value();
		if(vid == null)
			return error(NOT_FOUND);

		String ownerId = vid.getOwnerId();
		var result = client.get().getUser(ownerId, password);
		if(!result.isOK())
			return Result.error(result.error());


		var likeList = Hibernate.getInstance().sql("SELECT * FROM Likes WHERE shortId = '"
				+ shortId + "'", Likes.class);

		List<String> likeIdList = new ArrayList<>();
		for(int i = 0; i < likeList.size(); i++){
			likeIdList.add(likeList.get(i).getUserId());
		}

		return ok(likeIdList);
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		var result = client.get().getUser(userId, password);

		if(!result.isOK()) {
			return Result.error(result.error());
		}

		var followList = Hibernate.getInstance().sql("SELECT * FROM Follow WHERE followerId = '"
				+ userId + "'", Follow.class);

		List<String> followedIdList = new ArrayList<>(followList.size());
		for(int i = 0; i < followList.size(); i++)
			followedIdList.add(i, followList.get(i).getFollowedId());

		followedIdList.add(userId);

		List<Short> completeShortList = new ArrayList<>();

		for(int i = 0; i < followedIdList.size(); i++){
			var shortList = Hibernate.getInstance().sql("SELECT * FROM Short WHERE ownerId = '"
					+ followedIdList.get(i) + "'", Short.class);

			completeShortList.addAll(shortList);
		}

		Collections.sort(completeShortList, new ShortTimestampComparator());

		List<String> shortIdList = new ArrayList<>();
		for(int k = 0; k < completeShortList.size(); k++){
			shortIdList.add(k, completeShortList.get(k).getShortId());
		}

		return ok(shortIdList);

	}

	public class ShortTimestampComparator implements Comparator<Short> {
		@Override
		public int compare(Short s1, Short s2) {
			return Long.compare(s2.getTimestamp(), s1.getTimestamp());
		}
	}



	private boolean badParam(String str) {
		return str == null;
	}
		
	protected Result<User> okUser( String userId, String pwd) {
		try {
			return usersCache.get( new Credentials(userId, pwd));
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
	
	protected Result<Short> shortFromCache( String shortId ) {
		try {
			return shortsCache.get(shortId);
		} catch (ExecutionException e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}

	// Extended API 
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));


		//delete shorts
		var query1 = Hibernate.getInstance().sql("SELECT * FROM Short WHERE ownerId = '" + userId + "'", Short.class);
		for(int i = 0; i < query1.size(); i++){
			Hibernate.getInstance().deleteOne(query1.get(i));
		}

		//delete follows
		var query2 = Hibernate.getInstance().sql("SELECT * FROM Follow WHERE followerId = '" + userId + "' OR followedId = '" + userId + "'", Follow.class);
		for(int i = 0; i < query2.size(); i++){
			Hibernate.getInstance().deleteOne(query2.get(i));
		}

		//delete likes
		var query3 = Hibernate.getInstance().sql("SELECT * FROM Likes WHERE userId = '" + userId + "'", Likes.class);
		for(int i = 0; i < query3.size(); i++){
			Log.info("##################### like: " + query3.get(i).getShortId() + " userId " + query3.get(i).getUserId());
			Log.info("%%%%%%%%%%%%%%%%%%%" + likes(query3.get(i).getShortId(), password).value());
			Hibernate.getInstance().deleteOne(query3.get(i));
		}



		/*if( ! Token.matches( token ) )
			return error(FORBIDDEN);
		
		return DB.transaction( (hibernate) -> {
			
			usersCache.invalidate( new Credentials(userId, password) );
			
			//delete shorts
			var query1 = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);		
			hibernate.createNativeQuery(query1, Short.class).list().forEach( s -> {
				shortsCache.invalidate( s.getShortId() );
				hibernate.remove(s);
			});
			
			//delete follows
			var query2 = format("SELECT * FROM Follow f WHERE f.followerId = '%s' OR f.followedId = '%s'", userId, userId);
			hibernate.createNativeQuery(query2, Following.class).list().forEach( hibernate::remove );
			
			//delete likes
			var query3 = format("SELECT * FROM Likes WHERE userId = '%s'", userId);
			hibernate.createNativeQuery(query3, Likes.class).list().forEach( l -> {
				shortsCache.invalidate( l.getShortId() );
				hibernate.remove(l);
			});
		});*/

		return ok();
	}


	
	private String getLeastLoadedBlobServerURI() {
		try {
			var servers = blobCountCache.get(BLOB_COUNT);
			
			var	leastLoadedServer = servers.entrySet()
					.stream()
					.sorted( (e1, e2) -> Long.compare(e1.getValue(), e2.getValue()))
					.findFirst();
			
			if( leastLoadedServer.isPresent() )  {
				var uri = leastLoadedServer.get().getKey();
				servers.compute( uri, (k, v) -> v + 1L);				
				return uri;
			}
		} catch( Exception x ) {
			x.printStackTrace();
		}
		return "?";
	}
	
	static record BlobServerCount(String baseURI, Long count) {};
	
	private long totalShortsInDatabase() {
		var hits = DB.sql("SELECT count('*') FROM Short", Long.class);
		return 1L + (hits.isEmpty() ? 0L : hits.get(0));
	}


	@Override
	public Result<Short> getShortByBlobId(String blobId) {

		Log.info("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%" +
				"%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% mega log");

		if(badParam(blobId))
			return error(BAD_REQUEST);


		var shortList = Hibernate.getInstance().sql("SELECT * FROM Short WHERE shortId = '" + blobId + "'", Short.class);

		Log.info("$$$$$$$$$$$$$$$$$ blobId: " + blobId + " shortList size: " + shortList.size());


		if(shortList.isEmpty())
			return error(NOT_FOUND);

		return ok();
	}


	
}

