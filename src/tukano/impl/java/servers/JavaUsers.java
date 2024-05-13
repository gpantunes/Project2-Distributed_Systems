package tukano.impl.java.servers;

import static java.lang.String.format;
import static tukano.api.java.Result.ErrorCode.*;
import static tukano.api.java.Result.error;
import static tukano.api.java.Result.errorOrValue;
import static tukano.api.java.Result.ok;
import static tukano.impl.java.clients.Clients.ShortsClients;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import tukano.api.User;
import tukano.api.java.Result;
import tukano.api.java.Users;
import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.java.clients.ClientFactory;
import utils.Hibernate;

public class JavaUsers implements Users {
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	ClientFactory<ExtendedShorts> client = ShortsClients;

	@Override
	public Result<String> createUser(User user) {
		if (badUser(user))
			return error(BAD_REQUEST);

		var userId = user.getUserId();

		if (!Hibernate.getInstance().sql("SELECT * FROM User WHERE userId = '" + userId + "'", User.class).isEmpty())
			return error(CONFLICT);

		Hibernate.getInstance().persistOne(user);
		return ok(userId);
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		if (badParam(userId))
			return error(BAD_REQUEST);

		var userList = Hibernate.getInstance().sql("SELECT * FROM User WHERE userId = '"+ userId + "'", User.class);

		if (userList.isEmpty())
			return error(NOT_FOUND);

		User user = userList.get(0);

		if (badParam(pwd) || wrongPassword(user, pwd))
			return error(FORBIDDEN);
		else{
			return ok(user);
		}

	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User user) {
		if (badParam(userId) || user.getUserId() != null)
			return error(BAD_REQUEST);

		var userList = Hibernate.getInstance().sql("SELECT * FROM User WHERE userId = '" + userId + "'", User.class);

		if (userList.isEmpty())
			return error(NOT_FOUND);

		User oldUser = userList.get(0);

		Log.info("(" + oldUser.getUserId() + ", " + oldUser.getPwd() + ", "
				+ oldUser.getEmail() + ", " + oldUser.getDisplayName() + ")");

		if (badParam(pwd) || wrongPassword(oldUser, pwd))
			return error(FORBIDDEN);

		oldUser.setPwd(Objects.requireNonNullElse(user.getPwd(), oldUser.getPwd()));
		oldUser.setEmail(Objects.requireNonNullElse(user.getEmail(), oldUser.getEmail()));
		oldUser.setDisplayName(Objects.requireNonNullElse(user.getDisplayName(), oldUser.getDisplayName()));

		Hibernate.getInstance().updateOne(oldUser);

		return ok(oldUser);
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		if (badParam(userId))
			return error(BAD_REQUEST);

		var userList = Hibernate.getInstance().sql("SELECT * FROM User WHERE userId = '" + userId + "'", User.class);
		if(userList.isEmpty())
			return error(NOT_FOUND);
		User user = userList.get(0);
		if (badParam(pwd) || wrongPassword(user, pwd))
			return error(FORBIDDEN);

		client.get().deleteAllShorts(userId, pwd, userId);

		Hibernate.getInstance().deleteOne(user);

		return ok(user);
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		if (badParam(pattern))
			return error(BAD_REQUEST);

		pattern = pattern.toLowerCase();

		var hits = Hibernate.getInstance().sql("SELECT * FROM User WHERE LOWER(userId) LIKE '%" + pattern + "%'", User.class);

		return ok(hits);
	}

	@Override
	public Result<Void> existsUser(String userId){
		Log.info("$$$$$$$$$$$$$$$$$$$$$$$$$$ entrou no existsUser");

		var hits = Hibernate.getInstance().sql("SELECT * FROM User WHERE userId = '" + userId + "'", User.class);

		if(hits.isEmpty())
			return error(NOT_FOUND);

		return ok();
	}

	
	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}

	private boolean badUser(User user) {
		return user == null || badParam(user.getEmail()) || badParam(user.getDisplayName())
				|| badParam(user.getPwd());
	}

	private boolean badParam(String str) {
		return str == null;
	}

	private boolean wrongPassword(User user, String password) {
		return !user.getPwd().equals(password);
	}

}
