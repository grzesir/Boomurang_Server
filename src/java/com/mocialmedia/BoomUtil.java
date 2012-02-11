package com.mocialmedia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.jivesoftware.database.DbConnectionManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoomUtil {
	
	private static final Logger Log = LoggerFactory
			.getLogger(BoomurangPlugin.class);
	
	public static String makeMsgBody(String messageBody, String postID,
			String messageID, String logTime, String locationName,
			String longg, String lat, String teleported, String kudosUp,
			String kudosDown, String userKudos, String replyCount,
			String lastReplyTime, String roomType) {

		//I ma changing to test github!
		// Adding extra
		//commenting 1
		
		JSONObject o = new JSONObject();
		try {
			o.put("messageBody", messageBody);
			o.put("postID", postID);
			o.put("messageID", messageID);
			o.put("logTime", logTime);
			o.put("locationName", locationName);
			o.put("long", longg);
			o.put("lat", lat);
			o.put("teleported", teleported);
			o.put("kudosUp", kudosUp);
			o.put("kudosDown", kudosDown);
			o.put("userKudos", userKudos);
			o.put("replyCount", replyCount);
			o.put("lastReplyTime", lastReplyTime);
			o.put("roomType", roomType);

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return o.toString();

	}

	public static String makePushMessage(String alert, int badge,
			String sound, String postID, int nType, String recipient, String sender) {
		JSONObject aps = new JSONObject();
		//JSONObject info = new JSONObject();
		JSONObject all = new JSONObject();
		Connection con = null;
		try {

			aps.put("alert", alert);
			aps.put("badge", badge);
			aps.put("sound", sound);
			aps.put("postID", postID);
			aps.put("nType", nType);
			all.put("aps", aps);
			//all.put("info", info.toString());
			
			con = DbConnectionManager.getConnection();
			PreparedStatement setStatement = con
					.prepareStatement("INSERT INTO moNotifications(recipient, sender, nType, messageID, logTime) VALUES (?,?,?,?,?)");
			setStatement.setString(1, recipient);
			setStatement.setString(2, sender);
			setStatement.setInt(3, nType);
			setStatement.setString(4, postID);
			setStatement.setLong(5, System.currentTimeMillis());
			setStatement.executeUpdate();
			setStatement.close();

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException sqle) {
			Log.info("Error:" + sqle.getMessage());
		} catch (Exception ex) {
			Log.info("Error:" + ex.getMessage());
		} finally {
			DbConnectionManager.closeConnection(con);
		}
		return all.toString();
	}

	public static String makePushMessageForKudos(String alert, String sound,
			String postID, int nType, String recipient, String sender) {
		JSONObject aps = new JSONObject();
		//JSONObject info = new JSONObject();
		JSONObject all = new JSONObject();
		Connection con = null;
		try {
			aps.put("alert", alert);
			aps.put("sound", sound);
			aps.put("postID", postID);
			aps.put("nType", nType);
			all.put("aps", aps);
			//all.put("info", info.toString());
			
			con = DbConnectionManager.getConnection();
			PreparedStatement setStatement = con
					.prepareStatement("INSERT INTO moNotifications(recipient, sender, nType, messageID, logTime) VALUES (?,?,?,?,?)");
			setStatement.setString(1, recipient);
			setStatement.setString(2, sender);
			setStatement.setInt(3, nType);
			setStatement.setString(4, postID);
			setStatement.setLong(5, System.currentTimeMillis());
			setStatement.executeUpdate();
			setStatement.close();

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException sqle) {
			Log.info("Error:" + sqle.getMessage());
		} catch (Exception ex) {
			Log.info("Error:" + ex.getMessage());
		} finally {
			DbConnectionManager.closeConnection(con);
		}
		return all.toString();
	}

	public static String makeMsgBody(String messageBody, String postID,
			String messageID, String logTime, String locationName,
			String longg, String lat, String teleported, String kudosUp,
			String kudosDown, String userKudos, String replyCount,
			String lastReplyTime, String roomType, String unreadCount) {

		JSONObject o = new JSONObject();
		try {
			o.put("messageBody", messageBody);
			o.put("postID", postID);
			o.put("messageID", messageID);
			o.put("logTime", logTime);
			o.put("locationName", locationName);
			o.put("long", longg);
			o.put("lat", lat);
			o.put("teleported", teleported);
			o.put("kudosUp", kudosUp);
			o.put("kudosDown", kudosDown);
			o.put("userKudos", userKudos);
			o.put("replyCount", replyCount);
			o.put("lastReplyTime", lastReplyTime);
			o.put("roomType", roomType);
			o.put("unreadCount", unreadCount);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return o.toString();

	}

	public static String extractNameofUser(String s) {

		String[] s2 = s.split("@");
		return s2[0];
	}

	public static String trancateMsg(String s, int n) {

		if (s.length() < n) {
			return s;
		} else {
			s = s.substring(0, n);
			return s + "...";

		}
	}
}
