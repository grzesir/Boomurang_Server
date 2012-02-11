/**
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mocialmedia;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;

import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.EmailService;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dom4j.Element;

import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.handler.IQHandler;

import org.xmpp.packet.PacketError;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;

import java.util.Date;
import java.lang.Long;

import javapns.notification.Payload;
import javapns.notification.PayloadPerDevice;
import javapns.notification.PushNotificationPayload;

import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MUCRole;

import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;

/**
 * Registration plugin.
 * 
 * @author Ryan Graham.
 */
public class BoomurangPlugin implements Plugin, PacketInterceptor {
	final private boolean IS_PRODUCTION = false; // specify if this is
													// production release or dev
													// release , necessary for
													// push not. distinction.
	private InterceptorManager interceptorManager;
	private String serverName;
	private JID serverAddress;
	private MessageRouter router;
	private IQRouter iqRouter;
	private PacketRouter packRouter;
	private XMPPServer server;

	private static final Logger Log = LoggerFactory
			.getLogger(BoomurangPlugin.class);

	public BoomurangPlugin() {
		interceptorManager = InterceptorManager.getInstance();
	}

	public void initializePlugin(PluginManager pm, File f) {
		Log.info("BoomurangPlugin loaded...");
		serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
		serverAddress = new JID(serverName);
		router = XMPPServer.getInstance().getMessageRouter();
		iqRouter = XMPPServer.getInstance().getIQRouter();
		packRouter = XMPPServer.getInstance().getPacketRouter();
		interceptorManager.addInterceptor(this);

		server = XMPPServer.getInstance();

		IQHandler handler = new BoomurangIQHandler();
		iqRouter.addHandler(handler);
	}

	public void destroyPlugin() {
		interceptorManager.removeInterceptor(this);
		serverAddress = null;
		router = null;
	}

	public class BoomurangIQHandler extends IQHandler {
		private IQHandlerInfo info;

		public BoomurangIQHandler() {
			super("Boomurang IQ Handler");
			info = new IQHandlerInfo("query", "urn:boomurang:iq");
		}

		@Override
		public IQHandlerInfo getInfo() {
			return info;
		}

		@Override
		public IQ handleIQ(IQ packet) throws UnauthorizedException {

			// system.out.println("iq handler started......");
			IQ result = IQ.createResultIQ(packet);
			IQ.Type type = packet.getType();

			Log.info("Got IQ!");
			if (type.equals(IQ.Type.get)) {
				Log.info("Is Get Type");

				Element query = packet.getChildElement();
				Element resultChild = result.setChildElement("query",
						"urn:boomurang:iq");
				resultChild.addAttribute("type", query.attribute("type")
						.getValue());

				if (query.attribute("type").getValue().equals("rooms:basic")) {
					getRoomsBasic(resultChild, query);
				} else if (query.attribute("type").getValue()
						.equals("rooms:square")) {
					getRoomsSquare(resultChild, query);
				} else if (query.attribute("type").getValue()
						.equals("rooms:closeByLocation")) {
					getRoomsCloseByLocation(resultChild, query);
				} else if (query.attribute("type").getValue()
						.equals("myMessages:replies")) {
					getMyMessagesReplies(resultChild, query, packet);
				} else if (query.attribute("type").getValue()
						.equals("myMessages:posts")) {
					getMyMessagesPosts(resultChild, query, packet);
				} else if (query.attribute("type").getValue()
						.equals("messages:withPostID")) {
					getMessagesGetThread(resultChild, query, packet);
				} else if (query.attribute("type").getValue()
						.equals("messages:fromRooms")) {
					getMessagesFromRooms(resultChild, query, packet);
				} else if (query.attribute("type").getValue()
						.equals("rooms:search")) {
					getRoomsSearch(resultChild, query, packet);
				} else if (query.attribute("type").getValue()
						.equals("user:info")) {
					getUserInfo(resultChild, query, packet);
				} else if (query.attribute("type").getValue()
						.equals("myMessages:unreadCount")) {
					getMyMessagesUnreadCount(resultChild, query, packet);
				}
				else if (query.attribute("type").getValue()
						.equals("room:getMainRoom")) {
					getRoomGetMainRoom(resultChild, query);
				}
				else if (query.attribute("type").getValue()
						.equals("notifications:settings")) {
					getNotificationSettings(resultChild, query, packet);
				}

			}

			else if (type.equals(IQ.Type.set)) {
				Log.info("Is Set Type");

				Element query = packet.getChildElement();
				Element resultChild = result.setChildElement("query",
						"urn:boomurang:iq");
				resultChild.addAttribute("type", query.attribute("type")
						.getValue());

				if (query.attribute("type").getValue().equals("rooms:add")) {
					setRoomsAdd(resultChild, query);
				} else if (query.attribute("type").getValue()
						.equals("kudos:set")) {
					setKudosSet(query, packet);
				} else if (query.attribute("type").getValue()
						.equals("user:regForRoomNotification")) {
					// System.out
					// .println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@---invoking setUserRoomNotification");
					setUserRegForRoomNotification(resultChild, query, packet);
				} else if (query.attribute("type").getValue()
						.equals("user:deviceToken")) {
					// System.out
					// .println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@---invoking setUserRoomNotification");
					setUserDeiviceToken(resultChild, query, packet);
				}
				else if (query.attribute("type").getValue()
						.equals("notifications:settings")) {
					// System.out
					// .println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@---invoking setUserRoomNotification");
					setNotificationSettings(resultChild, query, packet);
				}
			}

			else {
				Log.info("IQ Error");
				result.setChildElement(packet.getChildElement().createCopy());
				result.setError(PacketError.Condition.not_acceptable);
			}

			Log.info("Returning Result");
			return result;
		}

		private void getNotificationSettings(Element resultChild, Element query,IQ packet) {
			// TODO Auto-generated method stub
			Log.info("Is Query Type: "  + query.attribute("type").getValue());

			Connection con = null;
			ResultSet rs = null;
			PreparedStatement pstmt = null;
			boolean abortTransaction = false;

			try {

				
				
				String user = packet.getFrom().toBareJID();

				con = DbConnectionManager.getConnection();
				pstmt = con
						.prepareStatement("select * from moUser where user=?;");
				pstmt.setString(1,user);
				rs=pstmt.executeQuery();
				
				if (rs.next()){
					resultChild.addElement("nTyp1").addText(rs.getString("nType1"));
					resultChild.addElement("nTyp2").addText(rs.getString("nType2"));
					resultChild.addElement("nTyp3").addText(rs.getString("nType3"));
					resultChild.addElement("nTyp4").addText(rs.getString("nType4"));
				}
				
				pstmt.close();
				con.close();

				
			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
				DbConnectionManager.closeTransactionConnection(con, true);
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
				//resultChild.addElement("failed");
			} finally {
				DbConnectionManager.closeTransactionConnection(con,
						abortTransaction);

			}
			
		}

		private void setNotificationSettings(Element resultChild,
				Element query, IQ packet) {
			// TODO Auto-generated method stub
			// TODO Auto-generated method stub

						Log.info("Is Query Type: "  + query.attribute("type").getValue());

						Connection con = null;
						ResultSet rs = null;
						PreparedStatement pstmt = null;
						boolean abortTransaction = false;

						try {

							int nType1 = Integer.parseInt(query.element("nType1").getText());
							int nType2 = Integer.parseInt(query.element("nType2").getText());
							int nType3 = Integer.parseInt(query.element("nType3").getText());
							int nType4 = Integer.parseInt(query.element("nType4").getText());
							
							String user = packet.getFrom().toBareJID();

							con = DbConnectionManager.getConnection();
							pstmt = con
									.prepareStatement("update moUser set nType1=?,nType2=?,nType3=?,nType4=? where user=?;");
							pstmt.setInt(1, nType1);
							pstmt.setInt(2, nType2);
							pstmt.setInt(3, nType3);
							pstmt.setInt(4, nType4);
							
							pstmt.setString(5, user);
							pstmt.executeUpdate();

							
							pstmt.close();
							con.close();

							resultChild.addElement("success");
						} catch (SQLException sqle) {
							Log.info("Error:" + sqle.getMessage());

							resultChild.addElement("failed");
							DbConnectionManager.closeTransactionConnection(con, true);
						} catch (Exception ex) {
							Log.info("Error:" + ex.getMessage());

							resultChild.addElement("failed");
						} finally {
							DbConnectionManager.closeTransactionConnection(con,
									abortTransaction);

						}
		}

		private void getMyMessagesUnreadCount(Element resultChild,
				Element query, IQ packet) {
			// TODO Auto-generated method stub
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// return all the thread that user started
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			int unreadPostCount = 0;
			int unreadReplyCount = 0;
			try {
				// Log.info("Bare ID: " + packet.getFrom().toBareJID());
				// System.out.println("------------------");
				con = DbConnectionManager.getConnection();
				// pstmt =
				// con.prepareStatement("SELECT * FROM `moMessages` AS t1 INNER JOIN `moRoomDetails` AS t2 ON t1.roomID = t2.roomID WHERE t1.sender = ? AND LENGTH(t1.subject) > 0 ORDER BY logTime;");
				pstmt = con
						.prepareStatement("select count(*) as unreadPostCount from moMsgReadTrack where user=? and postID=\"\";");
				pstmt.setString(1, packet.getFrom().toBareJID());
				rs = pstmt.executeQuery();
				if (rs.next()) {
					unreadPostCount = rs.getInt("unreadPostCount");
				}

				pstmt = con
						.prepareStatement("select count(distinct relMessageID) as unreadReplyCount from moMsgReadTrack where user=? and postID<>\"\";");
				pstmt.setString(1, packet.getFrom().toBareJID());
				rs = pstmt.executeQuery();
				if (rs.next()) {
					unreadReplyCount = rs.getInt("unreadReplyCount");
				}

				resultChild.addElement("unreadPostCount").addText(
						String.valueOf(unreadPostCount));
				resultChild.addElement("unreadReplyCount").addText(
						String.valueOf(unreadReplyCount));

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}

		}

		private void getUserInfo(Element resultChild, Element query, IQ packet) {
			// TODO Auto-generated method stub
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// return messages which user replied to
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try {
				int kudosUp = 0;
				int kudosDown = 0;
				int postCount = 0;
				int replyCount = 0;
				con = DbConnectionManager.getConnection();
				String user=packet.getFrom().toBareJID();
				// get kudos UP
				pstmt = con
						.prepareStatement("select sum(value) as kudosUp from moKudos left join moMessages "
								+ "on moKudos.messageID=moMessages.messageID where sender=? and value>0 ;");
				pstmt.setString(1, user);
				rs = pstmt.executeQuery();
				if (rs.next()) {
					kudosUp = rs.getInt("kudosUp");

				}

				// get kudos Down
				pstmt = con
						.prepareStatement("select sum(value) as kudosDown from moKudos left join moMessages on "
								+ "moKudos.messageID=moMessages.messageID where sender=? and value<0 ;");
				pstmt.setString(1, user);
				rs = pstmt.executeQuery();
				if (rs.next()) {
					kudosDown = rs.getInt("kudosDown");

				}

				// get post Count
				pstmt = con
						.prepareStatement("select count(msg) as postCount from moMessages where sender=? and postID=\"\";");
				pstmt.setString(1, user);
				rs = pstmt.executeQuery();
				if (rs.next()) {
					postCount = rs.getInt("postCount");

				}

				// get reply Count
				pstmt = con
						.prepareStatement("select count(msg) as replyCount from moMessages where sender=? and postID<>\"\";");
				pstmt.setString(1, user);
				rs = pstmt.executeQuery();
				if (rs.next()) {
					replyCount = rs.getInt("replyCount");

				}
				
				// get last Post
//				pstmt = con
//						.prepareStatement("select * from moMessages where sender=? order by logTime desc limit 0,1;");
//				pstmt.setString(1, user);
//				rs = pstmt.executeQuery();
//				if (rs.next()) {
//					replyCount = rs.getInt("lastPost");
//
//				}

				resultChild.addElement("kudosUp").addText(
						String.valueOf(kudosUp));
				resultChild.addElement("kudosDown").addText(
						String.valueOf(kudosDown));
				resultChild.addElement("postCount").addText(
						String.valueOf(postCount));
				resultChild.addElement("replyCount").addText(
						String.valueOf(replyCount));

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}

		}

		public boolean addRoomToElement(Element mainElement, String latitude,
				String longitude, String roomID, String locationName,
				String roomName, String distance, String messageCount,
				String roomType, String category) {
			// add room info to the Element(String) to send it back
			// Log.info("Adding Room");
			Element room = mainElement.addElement("room");
			room.addElement("lat").addText(latitude);
			room.addElement("long").addText(longitude);
			room.addElement("roomID").addText(roomID);
			room.addElement("locationName").addText(locationName);
			room.addElement("roomName").addText(roomName);
			room.addElement("distance").addText(distance);
			room.addElement("messageCount").addText(messageCount);
			room.addElement("roomType").addText(roomType);
			room.addElement("category").addText(category);

			return true;
		}

		public boolean addMessageToElement(Element mainElement, String to,
				String from, String body, String subject) {
			// add message info to Element(String) to send it back.
			// Log.info("Adding Message");
			Element message = mainElement.addElement("message");

			message.addAttribute("type", "groupchat");
			message.addAttribute("to", to);
			message.addAttribute("from", from);
			if (subject != null)
				message.addElement("subject").setText(subject);

			Element bodyElement = message.addElement("body");
			bodyElement.setText(body);
			// addUserKudosToElement(bodyElement, to);

			return true;
		}

		// ------------------get an set messages functions---------------------
		private void getRoomsBasic(Element resultChild, Element query) {
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// gets limited number of rooms in specified lat,long and their
			// message count.
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try {
				con = DbConnectionManager.getConnection();
				pstmt = con
						.prepareStatement("SELECT *, SQRT(POW(?-`latitude`, 2)+POW(?-`longitude`, 2)) as 'distance' FROM movRoomDetails ORDER BY `distance` LIMIT ?, ?;");
				pstmt.setDouble(1,
						Double.parseDouble(query.element("lat").getText()));
				pstmt.setDouble(2,
						Double.parseDouble(query.element("long").getText()));
				pstmt.setInt(3,
						Integer.parseInt(query.element("listnumber").getText()));
				pstmt.setInt(4,
						Integer.parseInt(query.element("limit").getText()));
				rs = pstmt.executeQuery();

				while (rs.next()) {

					addRoomToElement(resultChild, rs.getString("latitude"),
							rs.getString("longitude"), rs.getString("roomID"),
							rs.getString("locationName"),
							rs.getString("roomName"), rs.getString("distance"),
							rs.getString("msgCount"), rs.getString("roomType"),
							rs.getString("category"));
				}

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

		private void getRoomsSquare(Element resultChild, Element query) {
			// TODO Auto-generated method stub
			Log.info("Is Query Type: rooms:square");
			// return all replies to a given thread
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try {
				int roomType = Integer.parseInt(query.element("roomType")
						.getText());
				double minlat = Double.parseDouble(query.element("minlat")
						.getText());
				double minlong = Double.parseDouble(query.element("minlong")
						.getText());
				double maxlat = Double.parseDouble(query.element("maxlat")
						.getText());
				double maxlong = Double.parseDouble(query.element("maxlong")
						.getText());
				int limit = Integer.parseInt(query.element("limit").getText());
				String param1 = query.element("param1").getText();

				double criterion = Double.parseDouble(query
						.element("criterion").getText());

				/*
				 * system out
				 */
				// System.out.println("==================");
				// System.out.println(minlat);
				// System.out.println(minlong);
				// System.out.println(maxlat);
				// System.out.println(maxlong);
				// System.out.println(limit);
				// System.out.println(roomType);
				// System.out.println(param1);

				con = DbConnectionManager.getConnection();

				if (roomType == 0 && criterion == 0) {
					// any type of room and without any criteria--> it returns
					// limited number of room in the square
					Log.info("rooms:square--> roomType==0 && criterion==0");
					pstmt = con
							.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,"
									+ "ro.locationName,ro.category,ro.street,ro.city,ro.msgCount, ro.roomName,"
									+ "ro.state,ro.country,ro.zip,ro.facebookID,ro.category from movRoomDetails as ro "
									+ "where latitude>? and latitude<? and longitude>? and "
									+ "longitude<? order by msgCount desc;");

					pstmt.setDouble(1, minlat);
					pstmt.setDouble(2, maxlat);
					pstmt.setDouble(3, minlong);
					pstmt.setDouble(4, maxlong);
					// rs = pstmt.executeQuery();
				} else if (roomType != 0 && criterion == 0) {
					Log.info("rooms:square--> roomType!=0 && criterion==0");
					pstmt = con
							.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,"
									+ "ro.locationName,ro.category,ro.street,ro.city,ro.msgCount, ro.roomName,"
									+ "ro.state,ro.country,ro.zip,ro.facebookID,ro.category from movRoomDetails as ro "
									+ "where latitude>? and latitude<? and longitude>? and "
									+ "longitude<? and roomType=? order by msgCount desc;");

					pstmt.setDouble(1, minlat);
					pstmt.setDouble(2, maxlat);
					pstmt.setDouble(3, minlong);
					pstmt.setDouble(4, maxlong);
					pstmt.setInt(5, roomType);
				} else if (roomType == 0 && criterion == 1) {
					// setting date
					Log.info("rooms:square--> roomType==0 && criterion==1");
					Date d = new Date();
					d.setMinutes(d.getMinutes() - Integer.parseInt(param1));

					pstmt = con
							.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,ro.locationName,"
									+ "ro.category,ro.street,ro.city,ro.state,ro.country,ro.zip,ro.facebookID,ro.msgCount ,"
									+ "count(msg) as recMsgCount,ro.roomName,ro.category"
									+ " from movRoomDetails as ro left join moMessages as me on ro.roomID=me.roomID  "
									+ "where latitude>? and latitude<? and longitude>? and longitude<? "
									+ "and me.logTime>? group by roomID having recMsgCount>=1 "
									+ "order by recMsgCount desc limit 0,?;");

					pstmt.setDouble(1, minlat);
					pstmt.setDouble(2, maxlat);
					pstmt.setDouble(3, minlong);
					pstmt.setDouble(4, maxlong);
					pstmt.setLong(5, d.getTime());
					pstmt.setInt(6, limit);
				} else if (roomType != 0 && criterion == 1) {
					Log.info("rooms:square--> roomType!=0 && criterion==1");
					Date d = new Date();
					d.setMinutes(d.getMinutes() - Integer.parseInt(param1));

					pstmt = con
							.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,ro.locationName,"
									+ "ro.category,ro.street,ro.city,ro.state,ro.country,ro.zip,ro.facebookID,ro.msgCount ,"
									+ "count(msg) as recMsgCount,ro.roomName,ro.category"
									+ " from movRoomDetails as ro left join moMessages as me on ro.roomID=me.roomID  "
									+ "where latitude>? and latitude<? and longitude>? and longitude<? "
									+ "and me.logTime>? and roomType=? group by roomID having recMsgCount>=1 "
									+ "order by recMsgCount desc limit 0,?;");

					pstmt.setDouble(1, minlat);
					pstmt.setDouble(2, maxlat);
					pstmt.setDouble(3, minlong);
					pstmt.setDouble(4, maxlong);
					pstmt.setLong(5, d.getTime());
					pstmt.setInt(6, roomType);
					pstmt.setInt(7, limit);
				} else if (roomType != 0 && criterion == 2) {
					Log.info("rooms:square--> roomType!=0 && criterion==1");
					Date d = new Date();
					d.setMinutes(d.getMinutes() - Integer.parseInt(param1));

					pstmt = con
							.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,ro.locationName,"
									+ "ro.category,ro.street,ro.city,ro.state,ro.country,ro.zip,ro.facebookID,ro.msgCount ,"
									+ "count(msg) as recMsgCount,ro.roomName,ro.category"
									+ " from movRoomDetails as ro left join moMessages as me on ro.roomID=me.roomID  "
									+ "where latitude>? and latitude<? and longitude>? and longitude<? "
									+ "and me.logTime>? and roomType<>? group by roomID having recMsgCount>=1 "
									+ "order by recMsgCount desc limit 0,?;");

					pstmt.setDouble(1, minlat);
					pstmt.setDouble(2, maxlat);
					pstmt.setDouble(3, minlong);
					pstmt.setDouble(4, maxlong);
					pstmt.setLong(5, d.getTime());
					pstmt.setInt(6, roomType);
					pstmt.setInt(7, limit);
				} else if (roomType == 0 && criterion == 3) {
					Log.info("rooms:square--> roomType==0 && criterion==2");
					pstmt = con
							.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,ro.locationName,ro.category,"
									+ "ro.street,ro.city,ro.state,ro.country,ro.zip,ro.facebookID,ro.msgCount ,ro.roomName,ro.category "
									+ "from movRoomDetails as ro where latitude>? and latitude<? and longitude>? "
									+ "and longitude<? and ro.msgCount>=? order by ro.msgCount desc limit 0,?;");

					pstmt.setDouble(1, minlat);
					pstmt.setDouble(2, maxlat);
					pstmt.setDouble(3, minlong);
					pstmt.setDouble(4, maxlong);
					pstmt.setInt(5, Integer.parseInt(param1));
					pstmt.setInt(6, limit);
				} else if (roomType != 0 && criterion == 3) {
					Log.info("rooms:square--> roomType!=0 && criterion==2");
					pstmt = con
							.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,ro.locationName,ro.category,"
									+ "ro.street,ro.city,ro.state,ro.country,ro.zip,ro.facebookID,ro.msgCount,ro.roomName,ro.category  "
									+ "from movRoomDetails as ro where latitude>? and latitude<? and longitude>? "
									+ "and longitude<? and ro.msgCount>=? and roomType=? order by ro.msgCount desc limit 0,?;");

					pstmt.setDouble(1, minlat);
					pstmt.setDouble(2, maxlat);
					pstmt.setDouble(3, minlong);
					pstmt.setDouble(4, maxlong);
					pstmt.setInt(5, Integer.parseInt(param1));
					pstmt.setInt(6, roomType);
					pstmt.setInt(7, limit);
				}

				rs = pstmt.executeQuery();

				Log.info("rooms:square--> adding room info");
				ArrayList<Integer> roomIDs = new ArrayList<Integer>();

				while (rs.next()) {
					// Log.info("rooms 1");
					roomIDs.add(rs.getInt("roomID"));
					addRoomToElement(resultChild, rs.getString("latitude"),
							rs.getString("longitude"), rs.getString("roomID"),
							rs.getString("locationName"),
							rs.getString("roomName"), "-1",
							rs.getString("msgCount"), rs.getString("roomType"),
							rs.getString("category"));
				}

				// if the number of rooms is less than limit so try to add more
				// if possible to reach limit sticking to type sent
				int rest = limit - roomIDs.size();
				if (rest > 0) {
					Log.info("rooms:square--> adding more rooms!");
					if (roomType == 0 && criterion == 1) {
						// setting date
						// Log.info("rooms:square--> roomType==0 && criterion==1");
						Date d = new Date();
						d.setMinutes(d.getMinutes() - Integer.parseInt(param1));

						pstmt = con
								.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,ro.locationName,"
										+ "ro.category,ro.street,ro.city,ro.state,ro.country,ro.zip,ro.facebookID,ro.msgCount ,"
										+ "count(msg) as recMsgCount,ro.roomName,ro.category"
										+ " from movRoomDetails as ro left join moMessages as me on ro.roomID=me.roomID  "
										+ "where latitude>? and latitude<? and longitude>? and longitude<? "
										+ " group by roomID "
										+ "order by recMsgCount desc limit 0,?;");

						pstmt.setDouble(1, minlat);
						pstmt.setDouble(2, maxlat);
						pstmt.setDouble(3, minlong);
						pstmt.setDouble(4, maxlong);
						pstmt.setInt(5, limit * 2);
					} else if (roomType != 0 && criterion == 1) {
						// Log.info("rooms:square--> roomType!=0 && criterion==1");
						Date d = new Date();
						d.setMinutes(d.getMinutes() - Integer.parseInt(param1));

						pstmt = con
								.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,ro.locationName,"
										+ "ro.category,ro.street,ro.city,ro.state,ro.country,ro.zip,ro.facebookID,ro.msgCount ,"
										+ "count(msg) as recMsgCount,ro.roomName,ro.category"
										+ " from movRoomDetails as ro left join moMessages as me on ro.roomID=me.roomID  "
										+ "where latitude>? and latitude<? and longitude>? and longitude<? "
										+ " and roomType=? group by roomID  "
										+ "order by recMsgCount desc limit 0,?;");

						pstmt.setDouble(1, minlat);
						pstmt.setDouble(2, maxlat);
						pstmt.setDouble(3, minlong);
						pstmt.setDouble(4, maxlong);

						pstmt.setInt(5, roomType);
						pstmt.setInt(6, limit * 2);
					} else if (roomType != 0 && criterion == 2) {
						// Log.info("rooms:square--> roomType!=0 && criterion==1");
						Date d = new Date();
						d.setMinutes(d.getMinutes() - Integer.parseInt(param1));

						pstmt = con
								.prepareStatement("SELECT ro.roomID,ro.latitude,ro.longitude,ro.roomType,ro.locationName,"
										+ "ro.category,ro.street,ro.city,ro.state,ro.country,ro.zip,ro.facebookID,ro.msgCount ,"
										+ "count(msg) as recMsgCount,ro.roomName,ro.category"
										+ " from movRoomDetails as ro left join moMessages as me on ro.roomID=me.roomID  "
										+ "where latitude>? and latitude<? and longitude>? and longitude<? "
										+ " and roomType<>? group by roomID "
										+ "order by recMsgCount desc limit 0,?;");

						pstmt.setDouble(1, minlat);
						pstmt.setDouble(2, maxlat);
						pstmt.setDouble(3, minlong);
						pstmt.setDouble(4, maxlong);
						pstmt.setInt(5, roomType);
						pstmt.setInt(6, limit * 2);

					}
					rs = pstmt.executeQuery();
					while (rs.next()) {
						// Log.info("rooms 1");
						// if roomID is not already added add it
						if (!roomIDs.contains(rs.getInt("roomID")) && rest > 0) {
							roomIDs.add(rs.getInt("roomID"));
							addRoomToElement(resultChild,
									rs.getString("latitude"),
									rs.getString("longitude"),
									rs.getString("roomID"),
									rs.getString("locationName"),
									rs.getString("roomName"), "0",
									rs.getString("msgCount"),
									rs.getString("roomType"),
									rs.getString("category"));
							rest--;
						}
					}
				}
			}

			catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

		private void getRoomsSquareOld(Element resultChild, Element query) {
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// get room info like getRoomsBasic()! this return all 400 room
			// limited to square of lat,long

			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try {
				con = DbConnectionManager.getConnection();
				pstmt = con
						.prepareStatement("SELECT *, SQRT(POW(?-`latitude`, 2)+POW(?-`longitude`, 2)) as 'distance' FROM moRoomDetails WHERE `latitude` > ? AND `latitude` < ? AND `longitude` > ? AND `longitude` < ? ORDER BY `distance` LIMIT 0, 400;");
				pstmt.setDouble(1,
						Double.parseDouble(query.element("lat").getText()));
				pstmt.setDouble(2,
						Double.parseDouble(query.element("long").getText()));
				pstmt.setDouble(3,
						Double.parseDouble(query.element("minlat").getText()));
				pstmt.setDouble(4,
						Double.parseDouble(query.element("maxlat").getText()));
				pstmt.setDouble(5,
						Double.parseDouble(query.element("minlong").getText()));
				pstmt.setDouble(6,
						Double.parseDouble(query.element("maxlong").getText()));
				rs = pstmt.executeQuery();

				while (rs.next()) {
					PreparedStatement statement = con
							.prepareStatement("SELECT COUNT(body) FROM moMessages WHERE roomID = ?");
					statement.setInt(1, Integer.parseInt(rs.getString(4)));
					ResultSet messageCountResultSet = statement.executeQuery();
					messageCountResultSet.next();

					addRoomToElement(resultChild, rs.getString("latitude"),
							rs.getString("longitude"), rs.getString("roomID"),
							rs.getString("locationName"),
							rs.getString("roomName"), rs.getString("distance"),
							messageCountResultSet.getString(1),
							rs.getString("roomType"), rs.getString("category"));
				}

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

		private void getRoomsCloseByLocation(Element resultChild, Element query) {
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// return close rooms with messages inside

			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			int radius = 1000;
			if (query.element("radius") != null) {
				radius = Integer.parseInt(query.element("radius").getText());
			}
			// int ROOM_LIMIT = 250; //
			// Integer.parseInt(query.element("limit").getText());
			
			long after=0;
			if ((query.element("since") != null)){
				Date d=new Date();
				int min=Integer.parseInt(query.element("since").getText());
				d.setMinutes(d.getMinutes()-min);
				after=d.getTime();
			}
			try {
				con = DbConnectionManager.getConnection();
//				pstmt = con
//						.prepareStatement("SELECT *, SQRT(POW(?-`latitude`, 2)+POW(?-`longitude`, 2)) as "
//								+ "'distance' FROM movRoomDetails where 'distance'< ? and msgCount>0 "
//								+ "ORDER BY distance,msgCount desc LIMIT ?,?;");

				pstmt = con
						.prepareStatement("select t1.id,t1.latitude,t1.longitude,t1.roomID,t1.roomType," +
								"t1.locationName,t1.roomName,t1.category,t1.street,t1.city,t1.state," +
								"t1.country,t1.zip,t1.facebookID,count(t2.roomID) as msgCount," +
								"SQRT(POW(?-`latitude`, 2)+POW(?-`longitude`, 2)) as distance " +
								"from (moRoomDetails as t1 left join moMessages as t2 " +
								"on((t2.roomID = t1.roomID))) " +
								"where logTime>? group by t2.roomID " +
								"having distance< ? and  msgCount>0 " +
								"ORDER BY distance,msgCount desc LIMIT ?,?;");
				
				
				pstmt.setDouble(1,
						Double.parseDouble(query.element("lat").getText()));
				pstmt.setDouble(2,
						Double.parseDouble(query.element("long").getText()));
				pstmt.setLong(3,after);
				pstmt.setInt(4, radius);
				pstmt.setInt(5,
						Integer.parseInt(query.element("listnumber").getText()));
				pstmt.setInt(6,
						Integer.parseInt(query.element("limit").getText()));
				rs = pstmt.executeQuery();

				// Log.info("Starting SQL Build in getRoomsCloseByLocation");
				int i = 0;
				while (rs.next()) {
					// System.out.println("---------" + i++);
					// Log.info("Add roomID: " + rs.getString("roomID"));
					addRoomToElement(resultChild, rs.getString("latitude"),
							rs.getString("longitude"), rs.getString("roomID"),
							rs.getString("locationName"),
							rs.getString("roomName"), rs.getString("distance"),
							rs.getString("msgCount"), rs.getString("roomType"),
							rs.getString("category"));
				}

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

		private void getRoomGetMainRoom(Element resultChild, Element query) {
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// return close rooms with messages inside

			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			int radius = 1000;
			try {
				con = DbConnectionManager.getConnection();
				if (query.element("lat") != null
						&& query.element("long") != null) {
					Double lat = Double.parseDouble(query.element("lat")
							.getText());
					Double longg = Double.parseDouble(query.element("long")
							.getText());

					pstmt = con
							.prepareStatement("SELECT *, SQRT(POW(?-`latitude`, 2)+POW(?-`longitude`, 2)) as "
									+ "'distance' FROM movRoomDetails where 'distance'< ? "
									+ "ORDER BY distance asc LIMIT 0,1;");
					pstmt.setDouble(1,lat);
					pstmt.setDouble(2,longg);
					pstmt.setDouble(3,radius);
				} else if (query.element("roomName") != null) {
					String roomName = query.element("roomName").getText();
					pstmt = con
							.prepareStatement("select *, 0 as distance from movRoomDetails where " +
									"roomName=? LIMIT 0,1;");
					pstmt.setString(1,
							roomName);

				} else if (query.element("roomID") != null) {
					int roomID = Integer.parseInt(query.element("roomID")
							.getText());
					pstmt = con
							.prepareStatement("select *, 0 as distance from movRoomDetails where " +
									"roomID=? LIMIT 0,1;");
					pstmt.setInt(1,
							roomID);
				}
				rs = pstmt.executeQuery();

				// Log.info("Starting SQL Build in getRoomsCloseByLocation");
				int i = 0;
				while (rs.next()) {
					// System.out.println("---------" + i++);
					// Log.info("Add roomID: " + rs.getString("roomID"));
					addRoomToElement(resultChild, rs.getString("latitude"),
							rs.getString("longitude"), rs.getString("roomID"),
							rs.getString("locationName"),
							rs.getString("roomName"), rs.getString("distance"),
							rs.getString("msgCount"), rs.getString("roomType"),
							rs.getString("category"));
				}

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

		private void getMyMessagesReplies(Element resultChild, Element query,
				IQ packet) {
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// return messages which user replied to
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try {
				con = DbConnectionManager.getConnection();

				// pstmt = con
				// .prepareStatement("SELECT msg,postID,messageID,logTime,nickname,locationName,roomName,"
				// +
				// "latitude,longitude,teleported,roomType FROM `moMessages` AS t1 INNER JOIN `moRoomDetails` "
				// +
				// "AS t2 ON t1.roomID = t2.roomID WHERE t1.sender =? and postID!=\"\" order by logTime asc;");

				pstmt = con
						.prepareStatement("select distinct t2.messageID,t2.msg,t2.postID,t2.logTime,t2.nickname, t2.replyCount,"
								+ "t2.lastReplyTime, locationName,roomName,"
								+ "latitude,longitude,t2.teleported,roomType,count(t4.messageID) as unreadCount from "
								+ "(((moMessages as t1 inner join moMessages as t2 on t1.postID=t2.messageID) "
								+ "inner join moRoomDetails AS t3 ON t2.roomID = t3.roomID) "
								+ "left join moMsgReadTrack as t4 on t4.messageID=t1.messageID) WHERE t2.sender<>? "
								+ "and t1.sender =? "
								+ "group by t1.messageID order by t2.lastReplyTime desc;");

				pstmt.setString(1, packet.getFrom().toBareJID());
				pstmt.setString(2, packet.getFrom().toBareJID());
				// pstmt.setString(2, packet.getFrom().toBareJID());
				rs = pstmt.executeQuery();

				String body = "";
				int i = 0;
				Vector<String> kudosInfo;
				while (rs.next()) {
					System.out.println("----------getMyMessagesReplies----"
							+ i++);

					kudosInfo = getKudosInfo(rs.getString("messageID"), packet
							.getFrom().toBareJID());

					body = BoomUtil.makeMsgBody(rs.getString("msg"),
							rs.getString("postID"), rs.getString("messageID"),
							rs.getString("logTime"),
							rs.getString("locationName"),
							rs.getString("longitude"),
							rs.getString("latitude"),
							rs.getString("teleported"),
							kudosInfo.get(0)/* up */, kudosInfo.get(1)/* Down */,
							kudosInfo.get(2)/* user */,
							rs.getString("replyCount"),
							rs.getString("lastReplyTime"),
							rs.getString("roomType"),
							rs.getString("unreadCount"));

					addMessageToElement(
							resultChild,
							packet.getFrom().toBareJID(),
							rs.getString("roomName") + "/"
									+ rs.getString("nickname"), body,
							rs.getString("msg"));
				}
			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

		private void getMyMessagesPosts(Element resultChild, Element query,
				IQ packet) {
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// return all the thread that user started
			Connection con = null;
			PreparedStatement pstmt = null;
			PreparedStatement pstmt2 = null;
			ResultSet rs = null;
			ResultSet rs2 = null;
			try {
				// Log.info("Bare ID: " + packet.getFrom().toBareJID());
				// System.out.println("------------------");
				con = DbConnectionManager.getConnection();
				// pstmt =
				// con.prepareStatement("SELECT * FROM `moMessages` AS t1 INNER JOIN `moRoomDetails` AS t2 ON t1.roomID = t2.roomID WHERE t1.sender = ? AND LENGTH(t1.subject) > 0 ORDER BY logTime;");

				// pstmt = con
				// .prepareStatement("SELECT msg,postID,messageID,logTime,locationName,roomName,"
				// +
				// "nickname,latitude,longitude,teleported,replyCount,lastReplyTime,roomType FROM `moMessages` "
				// +
				// "AS t1 INNER JOIN `moRoomDetails` AS t2 ON t1.roomID = t2.roomID WHERE t1.sender =? and "
				// + "postID=\"\" order by lastReplyTime desc;");

				pstmt = con
						.prepareStatement("SELECT t1.msg,t1.postID,t1.messageID,t1.logTime,t2.locationName,t2.roomName,"
								+ "t1.nickname,t2.latitude,t2.longitude,t1.teleported,t1.replyCount,t1.lastReplyTime,t2.roomType"
								+ ",count(t3.messageID) as unreadCount FROM ((`moMessages` AS t1 INNER JOIN `moRoomDetails` AS t2 "
								+ "ON t1.roomID = t2.roomID) left join moMsgReadTrack as t3 on t1.messageID=t3.messageID) "
								+ "WHERE t1.sender =? and t1.postID=\"\" group by t1.messageID order by t1.lastReplyTime desc;");

				pstmt.setString(1, packet.getFrom().toBareJID());
				rs = pstmt.executeQuery();

				String body = "";
				Vector<String> kudosInfo;
				while (rs.next()) {

					// getting number of unred messages from this post
					// pstmt2 = con
					// .prepareStatement("select count(*) as unreadCount from moMsgReadTrack where messageID= ?;");
					//
					// pstmt2.setString(1, rs.getString("messageID"));
					// rs2 = pstmt2.executeQuery();
					//
					// String unreadCount="0";
					// if (rs2.next()){
					// unreadCount=rs2.getString("unreadCount ");
					// }
					//

					kudosInfo = getKudosInfo(rs.getString("messageID"), packet
							.getFrom().toBareJID());

					body = BoomUtil.makeMsgBody(rs.getString("msg"),
							rs.getString("postID"), rs.getString("messageID"),
							rs.getString("logTime"),
							rs.getString("locationName"),
							rs.getString("longitude"),
							rs.getString("latitude"),
							rs.getString("teleported"),
							kudosInfo.get(0)/* up */, kudosInfo.get(1)/* Down */,
							kudosInfo.get(2)/* user */,
							rs.getString("replyCount"),
							rs.getString("lastReplyTime"),
							rs.getString("roomType"),
							rs.getString("unreadCount"));

					addMessageToElement(
							resultChild,
							packet.getFrom().toBareJID(),
							rs.getString("roomName") + "/"
									+ rs.getString("nickname"), body,
							rs.getString("msg"));
					System.out.println("-------" + body);
				}
				pstmt2.close();
			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

		private void getMessagesGetThread(Element resultChild, Element query,
				IQ packet) {
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// return all replies to a given thread
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			String postID = query.element("postID").getText();
			String user = packet.getFrom().toBareJID();
			try {
				con = DbConnectionManager.getConnection();
				// pstmt =
				// con.prepareStatement("SELECT * FROM `moMessages` AS t1 INNER JOIN `moRoomDetails` AS t2 ON t1.roomID = t2.roomID WHERE body LIKE ?;");
				pstmt = con
						.prepareStatement("SELECT msg,postID,sender,nickname,messageID,logTime,"
								+ "latitude,longitude,locationName,roomName,teleported,roomType FROM moRoomDetails,moMessages "
								+ "where moMessages.roomID=moRoomDetails.roomID and "
								+ "(moMessages.postID=? or moMessages.messageID=?) order by logTime asc;");
				pstmt.setString(1, postID);
				pstmt.setString(2, postID);
				// Log.info("%\"postID\":\"" + postID + "%");
				rs = pstmt.executeQuery();

				// query.addElement("postID").setText(postID);
				int msgCount = 0;
				String body = "";
				Vector<String> kudosInfo;
				while (rs.next()) {
					// Log.info("getting Kudos Info");
					kudosInfo = getKudosInfo(rs.getString("messageID"), packet
							.getFrom().toBareJID());

					body = BoomUtil.makeMsgBody(rs.getString("msg"),
							rs.getString("postID"), rs.getString("messageID"),
							rs.getString("logTime"),
							rs.getString("locationName"),
							rs.getString("longitude"),
							rs.getString("latitude"),
							rs.getString("teleported"),
							kudosInfo.get(0)/* up */, kudosInfo.get(1)/* Down */,
							kudosInfo.get(2)/* user */, "0",
							rs.getString("logTime"), rs.getString("roomType"));

					addMessageToElement(
							resultChild,
							packet.getFrom().toBareJID(),
							rs.getString("roomName") + "/"
									+ rs.getString("nickname"), body,
							rs.getString("msg"));
					msgCount++;
				}
				Element msgCountElement = resultChild.addElement("msgCount");
				msgCountElement.setText(String.valueOf(msgCount));

				/*
				 * we need to delete the user notification track in
				 * moMsgReadTrack we need two quarry one for deleting the post
				 * related data and one for deleting reply related data. we
				 * combine them and write one quarry to do both
				 * 
				 * 1- delete moMsgReadTrack as t1 where t1.messageID=q.postID
				 * and t1.user=q.user 2- delete moMsgReadTrack as t1 where
				 * t1.postID=q.postID and t1.user=q.user
				 * 
				 * combined quarry: delete moMsgReadTrack as t1 where
				 * t1.user=q.user and (t1.messageID=q.postID or
				 * t1.postID=q.postID)
				 */

				pstmt = con.prepareStatement("delete from moMsgReadTrack  "
						+ "where user=? and (messageID=? or postID=?); ");
				pstmt.setString(1, user);
				pstmt.setString(2, postID);
				pstmt.setString(3, postID);

				pstmt.executeUpdate();

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());

			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

		// update to new table format
		private void getMessagesFromRoomsOld(Element resultChild,
				Element query, IQ packet) {

			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// get all post within array of rooms.
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			String user = packet.getFrom().toBareJID();
			Log.info("Checking rooms");
			for (Element room : (List<Element>) query.elements("room")) {
				Log.info("In room loop");
				String roomID = room.element("roomID").getText();
				String roomName = room.element("roomName").getText();
				Log.info("Room: " + roomID);
				try {
					con = DbConnectionManager.getConnection();
					// pstmt =
					// con.prepareStatement("SELECT * FROM `moMessages` WHERE roomID = ?");
					pstmt = con
							.prepareStatement("SELECT msg,postID,sender,messageID,logTime,latitude,longitude,"
									+ "locationName,teleported,roomType FROM moRoomDetails,moMessages "
									+ "where moMessages.roomID=moRoomDetails.roomID and moMessages.roomID=? order by logTime asc;");
					pstmt.setLong(1, Long.parseLong(roomID));
					rs = pstmt.executeQuery();
					int i = 0;
					String body = "";
					Vector<String> kudosInfo;
					while (rs.next()) {
						System.out.println("before -----------" + roomID);
						kudosInfo = getKudosInfo(rs.getString("messageID"),
								packet.getFrom().toBareJID());

						body = BoomUtil.makeMsgBody(rs.getString("msg"),
								rs.getString("postID"),
								rs.getString("messageID"),
								rs.getString("logTime"),
								rs.getString("locationName"),
								rs.getString("longitude"),
								rs.getString("latitude"),
								rs.getString("teleported"),
								kudosInfo.get(0)/* up */,
								kudosInfo.get(1)/* Down */,
								kudosInfo.get(2)/* user */, "0",
								rs.getString("logTime"),
								rs.getString("roomType"));

						System.out.println("after msg=" + body);

						addMessageToElement(resultChild, packet.getFrom()
								.toBareJID(),
								roomName + "/" + rs.getString("sender"), body,
								rs.getString("msg"));

						// addUserKudosToElement(resultChild.element("body"),
						// packet.getFrom().toBareJID());
						System.out
								.println("---------get message from element----"
										+ i++);
					}
				} catch (SQLException sqle) {
					Log.info("Error:" + sqle.getMessage());
				} catch (Exception ex) {
					Log.info("Error:" + ex.getMessage());
				} finally {
					DbConnectionManager.closeConnection(pstmt, con);
				}
			}
		}

		// update to new table format
		private void getMessagesFromRooms(Element resultChild, Element query,
				IQ packet) {

			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// get all post within array of rooms.
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			// String user=packet.getFrom().toBareJID();
			// long roomID=Long.parseLong(query.element("roomID").getText());
			// Log.info("Checking rooms");

			java.util.List<MsgSort> msgs = new ArrayList<MsgSort>();

			for (Element room : (List<Element>) query.elements("room")) {
				Log.info("In room loop");
				Long roomID = Long.parseLong(room.element("roomID").getText());
				// roomName = room.element("roomName").getText();
				// Log.info("Room: " + roomID);
				try {
					con = DbConnectionManager.getConnection();
					// pstmt =
					// con.prepareStatement("SELECT * FROM `moMessages` WHERE roomID = ?");
					pstmt = con
							.prepareStatement("SELECT msg,postID,sender,messageID,logTime,latitude,longitude,"
									+ "locationName,roomName, replyCount,lastReplyTime,teleported,roomType FROM moRoomDetails,moMessages "
									+ "where moMessages.roomID=moRoomDetails.roomID and moMessages.roomID=? and "
									+ "moMessages.postID=\"\" order by lastReplyTime desc;");
					pstmt.setLong(1, roomID);
					rs = pstmt.executeQuery();
					int i = 0;
					String body = "";
					Vector<String> kudosInfo;
					while (rs.next()) {
						System.out.println("before -----------" + roomID);
						kudosInfo = getKudosInfo(rs.getString("messageID"),
								packet.getFrom().toBareJID());

						body = BoomUtil.makeMsgBody(rs.getString("msg"),
								rs.getString("postID"),
								rs.getString("messageID"),
								rs.getString("logTime"),
								rs.getString("locationName"),
								rs.getString("longitude"),
								rs.getString("latitude"),
								rs.getString("teleported"),
								kudosInfo.get(0)/* up */,
								kudosInfo.get(1)/* Down */,
								kudosInfo.get(2)/* user */,
								rs.getString("replyCount"),
								rs.getString("lastReplyTime"),
								rs.getString("roomType"));

						System.out.println("after msg=" + body);

						// adding messages to ArrayList to sort it later
						msgs.add(new MsgSort(rs.getLong("lastReplyTime"), body,
								rs.getString("roomName"), rs
										.getString("sender"), rs
										.getString("msg")));

						// addUserKudosToElement(resultChild.element("body"),
						// packet.getFrom().toBareJID());
						System.out
								.println("---------get message from element----"
										+ i++);
					}
				} catch (SQLException sqle) {
					Log.info("Error:" + sqle.getMessage());
				} catch (Exception ex) {
					Log.info("Error:" + ex.getMessage());
				} finally {
					DbConnectionManager.closeConnection(pstmt, con);
				}
			}

			Collections.sort(msgs);
			int i = 1;
			for (MsgSort msgSort : msgs) {
				System.out.println("####### indie for" + i++);
				addMessageToElement(resultChild, packet.getFrom().toBareJID(),
						msgSort.roomName + "/" + msgSort.sender, msgSort.body,
						msgSort.msg);
				// System.out.println(msgSort.logTime+"-->"+msgSort.body);
			}

		}

		private void getRoomsSearch(Element resultChild, Element query,
				IQ packet) {
			// TODO Auto-generated method stub
			Log.info("Is Query Type: rooms:search");
			// return all replies to a given thread
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try {
				int roomType = Integer.parseInt(query.element("roomType")
						.getText());
				double lat = Double.parseDouble(query.element("lat").getText());
				double longg = Double.parseDouble(query.element("long")
						.getText());
				int limit = Integer.parseInt(query.element("limit").getText());
				int listnumber = Integer.parseInt(query.element("listnumber")
						.getText());
				double radius = Double.parseDouble(query.element("radius")
						.getText());

				String search = query.element("search").getText();
				search = "%" + search + "%";
				con = DbConnectionManager.getConnection();

				if (roomType != 0 && radius >= 0) {
					Log.info("roomType!=0 && radius>=0");
					pstmt = con
							.prepareStatement("select *,SQRT(POW(?-`latitude`, 2)+POW(?-`longitude`, 2)) as distance "
									+ "FROM movRoomDetails where roomType=? and locationName like ? having distance<?  order by distance limit ?,?;");
					pstmt.setDouble(1, lat);
					pstmt.setDouble(2, longg);
					pstmt.setInt(3, roomType);
					pstmt.setString(4, search);
					pstmt.setDouble(5, radius);
					pstmt.setInt(6, listnumber);
					pstmt.setInt(7, limit);

				} else if ((roomType != 0 && radius < 0)) {
					Log.info("roomType!=0 && radius<0");
					pstmt = con
							.prepareStatement("select *,SQRT(POW(?-`latitude`, 2)+POW(?-`longitude`, 2)) as distance "
									+ "FROM movRoomDetails where roomType=? and locationName like ?  order by distance limit ?,?;");
					pstmt.setDouble(1, lat);
					pstmt.setDouble(2, longg);
					pstmt.setInt(3, roomType);
					pstmt.setString(4, search);
					pstmt.setInt(5, listnumber);
					pstmt.setInt(6, limit);
				} else if (roomType == 0 && radius >= 0) {
					Log.info("roomType==0 && radius>=0");
					pstmt = con
							.prepareStatement("select *,SQRT(POW(?-`latitude`, 2)+POW(?-`longitude`, 2)) as distance "
									+ "FROM movRoomDetails where locationName like ? having distance<?  order by distance limit ?,?;");
					pstmt.setDouble(1, lat);
					pstmt.setDouble(2, longg);

					pstmt.setString(3, search);
					pstmt.setDouble(4, radius);
					pstmt.setInt(5, listnumber);
					pstmt.setInt(6, limit);
				} else {
					Log.info("roomType==0 && radius<0");
					pstmt = con
							.prepareStatement("select *,SQRT(POW(?-`latitude`, 2)+POW(?-`longitude`, 2)) as distance "
									+ "FROM movRoomDetails where locationName like ?  order by distance limit ?,?;");
					pstmt.setDouble(1, lat);
					pstmt.setDouble(2, longg);
					pstmt.setString(3, search);
					pstmt.setInt(4, listnumber);
					pstmt.setInt(5, limit);

				}

				rs = pstmt.executeQuery();

				while (rs.next()) {
					Log.info("Add roomID: " + rs.getString("roomID"));
					addRoomToElement(resultChild, rs.getString("latitude"),
							rs.getString("longitude"), rs.getString("roomID"),
							rs.getString("locationName"),
							rs.getString("roomName"), rs.getString("distance"),
							rs.getString("msgCount"), rs.getString("roomType"),
							rs.getString("category"));
				}

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

		// ----------------Set Methods-------------------
		// -----------------------------------------------
		// register user for the notification
		private void setUserRegForRoomNotification(Element resultChild,
				Element query, IQ packet) {
			// TODO Auto-generated method stub
			Log.info("Is Query Type: " + "user:regForRoomNotification");
			System.out.println("Is Query Type: "
					+ "user:regForRoomNotification");
			Connection con = null;
			ResultSet rs = null;
			PreparedStatement pstmt = null;
			boolean abortTransaction = false;

			try {

				int roomID = Integer
						.parseInt(query.element("roomID").getText());
				int duration = Integer.parseInt(query.element("duration")
						.getText());

				String user = packet.getFrom().toBareJID();
				Date t = new Date();
				long start = t.getTime();
				t.setMinutes(t.getMinutes() + duration);
				long end = t.getTime();

				System.out.println("user=" + user);
				System.out.println("roomID=" + roomID);

				con = DbConnectionManager.getConnection();
				pstmt = con
						.prepareStatement("delete from moRoomsNotifyList where user=? and roomID=?; ");
				pstmt.setString(1, user);
				pstmt.setInt(2, roomID);

				pstmt.executeUpdate();

				pstmt = con
						.prepareStatement("Insert into moRoomsNotifyList(user,roomID,startTime,endTime) values(?,?,?,?); ");
				pstmt.setString(1, user);
				pstmt.setInt(2, roomID);

				pstmt.setLong(3, start);
				pstmt.setLong(4, end);

				// Log.info("adding room notification for user");
				pstmt.executeUpdate();
				pstmt.close();
				con.close();

				resultChild.addElement("success");
			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
				resultChild.addElement("failed");
				DbConnectionManager.closeTransactionConnection(con, true);
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());

				resultChild.addElement("failed");
			} finally {
				DbConnectionManager.closeTransactionConnection(con,
						abortTransaction);

			}
			System.out.println("method ended!");

		}

		private void setUserDeiviceToken(Element resultChild, Element query,
				IQ packet) {
			// TODO Auto-generated method stub

			Log.info("Is Query Type: " + "user:deviceToken");

			Connection con = null;
			ResultSet rs = null;
			PreparedStatement pstmt = null;
			boolean abortTransaction = false;

			try {

				String deviceToken = query.element("deviceToken").getText();
				String user = packet.getFrom().toBareJID();

				con = DbConnectionManager.getConnection();
				pstmt = con
						.prepareStatement("delete from moUser where user=? or deviceToken=?; ");
				pstmt.setString(1, user);
				pstmt.setString(2, deviceToken);

				pstmt.executeUpdate();

				pstmt = con
						.prepareStatement("Insert into moUser(user,deviceToken) values(?,?); ");
				pstmt.setString(1, user);
				pstmt.setString(2, deviceToken);

				pstmt.executeUpdate();
				pstmt.close();
				con.close();

				resultChild.addElement("success");
			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());

				resultChild.addElement("failed");
				DbConnectionManager.closeTransactionConnection(con, true);
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());

				resultChild.addElement("failed");
			} finally {
				DbConnectionManager.closeTransactionConnection(con,
						abortTransaction);

			}
			System.out.println("user:deviceToken ended!");
		}

		private void setRoomsAdd(Element resultChild, Element query) {
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// add room to the system
			// system.out.println("ddd-set rooms add started.......");
			Connection con = null;
			ResultSet rs = null;
			PreparedStatement pstmt = null;

			MUCRoom room = createChatroom();

			boolean abortTransaction = false;
			boolean roomExists = false;

			try {
				con = DbConnectionManager.getConnection();
				pstmt = con
						.prepareStatement("SELECT * FROM `moRoomDetails` WHERE locationName = ? AND latitude = ? AND longitude = ?;");
				pstmt.setString(1, query.element("locationName").getText());
				pstmt.setDouble(2,
						Double.parseDouble(query.element("lat").getText()));
				pstmt.setDouble(3,
						Double.parseDouble(query.element("long").getText()));
				rs = pstmt.executeQuery();

				if (!rs.first()) // if there is not already room with specified
									// info (lat,long,location name)
				{

					// prepare recived info for insert
					String category = "";
					if (query.element("category") != null)
						category = query.element("category").getText();
					String street = new String("");
					if (query.element("street") != null)
						street = query.element("street").getText();
					String city = new String("");
					if (query.element("city") != null)
						city = query.element("city").getText();
					String state = new String("");
					if (query.element("state") != null)
						state = query.element("state").getText();
					String country = new String("");
					if (query.element("country") != null)
						country = query.element("country").getText();
					String zip = new String("");
					if (query.element("zip") != null)
						zip = query.element("zip").getText();
					String facebookID = new String("");
					if (query.element("facebookID") != null)
						facebookID = query.element("facebookID").getText();
					int roomType = 0;
					if (query.element("roomType") != null)
						roomType = Integer.parseInt(query.element("roomType")
								.getText());

					// insert the room
					// con = DbConnectionManager.getTransactionConnection();
					pstmt = con
							.prepareStatement("INSERT INTO moRoomDetails(latitude, longitude, roomID, roomType, locationName, roomName, category, street, city, state, country, zip, facebookID) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?);");

					pstmt.setDouble(1,
							Double.parseDouble(query.element("lat").getText()));
					pstmt.setDouble(2,
							Double.parseDouble(query.element("long").getText()));
					pstmt.setLong(3, room.getID());
					pstmt.setInt(4, roomType);
					pstmt.setString(5, query.element("locationName").getText());
					pstmt.setString(6, room.getName() + "@"
							+ room.getMUCService().getServiceDomain());
					pstmt.setString(7, category);
					pstmt.setString(8, street);
					pstmt.setString(9, city);
					pstmt.setString(10, state);
					pstmt.setString(11, country);
					pstmt.setString(12, zip);
					pstmt.setString(13, facebookID);

					pstmt.executeUpdate();

					// returning client created room information
					addRoomToElement(resultChild, query.element("lat")
							.getText(), query.element("long").getText(),
							String.valueOf(room.getID()),
							query.element("locationName").getText(),
							room.getName(), "0", "0", String.valueOf(roomType),
							category);

					// pstmt.close();
					// con.close();
				} else {
					Log.info("Error: Location already Exists");
					// roomExists = true;

				}
				pstmt.close();
				con.close();

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
				abortTransaction = true;
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			}
			// } finally {
			// DbConnectionManager.closeTransactionConnection(con,
			// abortTransaction);
			// // abortTransaction = true;
			// }

			// if (abortTransaction)
			// resultChild.addElement("fail").addText("Database error.");
			// else if (roomExists)
			// resultChild.addElement("fail").addText(
			// "Location already exists.");
			// else
			// resultChild.addElement("success");
		}

		private void setKudosSet(Element query, IQ packet) {
			Log.info("Is Query Type: " + query.attribute("type").getValue());
			// set a kudos on a message
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try {
				con = DbConnectionManager.getConnection();
				// pstmt =
				// con.prepareStatement("SELECT * FROM moKudos WHERE postID = ? AND messageID = ? AND username = ?");

				int kudosType = Integer.parseInt(query.element("kudosType")
						.getText());
				String kudosSender = packet.getFrom().toBareJID();
				String messageID = query.element("messageID").getText();

				pstmt = con
						.prepareStatement("SELECT * FROM moKudos WHERE messageID = ? AND username = ?");
				// pstmt.setString(1, query.element("postID").getText());
				pstmt.setString(1, query.element("messageID").getText());
				pstmt.setString(2, packet.getFrom().toBareJID());
				rs = pstmt.executeQuery();

				// Log.info("Starting Add..");
				if (!rs.next()) // if there is no kudos to the message from a
								// user
				{
					PreparedStatement setStatement = con
							.prepareStatement("INSERT INTO moKudos( messageID, username, value) VALUES (?,?,?)");
					// setStatement.setString(1,
					// query.element("postID").getText());
					setStatement.setString(1, messageID);
					setStatement.setString(2, kudosSender);
					setStatement.setInt(3, kudosType);
					setStatement.executeUpdate();
					setStatement.close();
				} else {
					// already there is a kudo from this user to that message
					PreparedStatement setStatement = con
							.prepareStatement("update moKudos set value=? where messageID=? and username=?;");
					// setStatement.setString(1,
					// query.element("postID").getText());
					setStatement.setInt(1, kudosType);
					setStatement.setString(2, messageID);
					setStatement.setString(3, kudosSender);
					setStatement.executeUpdate();
					setStatement.close();
				}

				// notify user who get kudos if it is positive
				// first find the user of the message
				if (kudosType > 0) {
					pstmt = con
							.prepareStatement("select deviceToken,sender,postID,messageID from moMessages as t1 "
									+ "left join moUser t2 on t1.sender=t2.user Where "
									+ "messageID=? and t2.valid=1 and nType1=1;");
					pstmt.setString(1, messageID);
					rs = pstmt.executeQuery();

					ArrayList<PayloadPerDevice> plpds = new ArrayList<PayloadPerDevice>();
					if (rs.next()) { // it return record only if the user has
										// device
										// Token registered!
						Log.info("Notifing User Kudos: "
								+ rs.getString("sender"));
						String parentPostID;
						if (rs.getString("postID")!=""){
							parentPostID=rs.getString("postID");
						}
						else{
							parentPostID=rs.getString("messageID");
						}
						// Payload payload = PushNotificationPayload.combined(
						// kudosSender + " gave you positive kudos! ",
						// kudosType, "default");
						// payload.addCustomDictionary("NType", 1);
						// payload.addCustomDictionary("messageID", messageID);

						final String pushMsg = BoomUtil
								.makePushMessageForKudos(
										BoomUtil.extractNameofUser(kudosSender)
												+ " gave you Kudos!",
										"default", parentPostID, 1);
						Payload payload = new Payload() {
							public String toString() {
								return pushMsg;
							};
						};

						PayloadPerDevice plpd = new PayloadPerDevice(payload,
								rs.getString("deviceToken"));

						plpds.add(plpd);

					}
					pstmt.close();
					if (!plpds.isEmpty()) {
						// send package to this user.
						PushThread pt = new PushThread(plpds, IS_PRODUCTION, 1);

						pt.start();
					}

				}

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());
			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());
			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
			}
		}

	}

	/*
	 * 
	 * public void interceptPacket(Packet packet, Session session, boolean
	 * incoming, boolean processed) throws PacketRejectedException {
	 * 
	 * System.out.println("#######Intercepted Message-----------------------------"
	 * ); System.out.println("%%%:"+packet.toXML());
	 * System.out.println("ID---->:"+packet.getID());
	 * System.out.println("###incoming="+incoming);
	 * System.out.println("###processed="+processed);
	 * System.out.println("###packet instanceof Message="+(packet instanceof
	 * Message)); System.out.println("###From="+packet.getFrom());
	 * System.out.println("###To="+packet.getTo());
	 * System.out.println("###toString()="+packet.toString()); }
	 */

	// public void interceptPacket(Packet packet, Session session,
	// boolean incoming, boolean processed) throws PacketRejectedException {
	//
	//
	//
	// //if ((packet instanceof Message) && !processed) {
	// if ((packet instanceof Message) && !processed && incoming) {
	// Log.info("Intercepted Incoming Message");
	// throw new PacketRejectedException();
	// }
	//
	// System.out.println("to: " +packet.toXML());
	//
	// }

	
	
	public void interceptPacket(Packet packet, Session session,
			boolean incoming, boolean processed) throws PacketRejectedException {

		//int ii = ((new java.util.Random()).nextInt()) * 10;
		//System.out.println("###"+packet.toXML());
		final String FB_USER="fblogin-boomurang-2012";
		if (packet instanceof IQ && !processed ){
		
//			IQ iq=(IQ)packet;
//			Element query = iq.getChildElement();
//			if (iq.getType().equals(IQ.Type.error) && query.getNamespaceURI().equals("jabber:iq:auth")){
//				Element error=iq.getElement().element("error");
//				if (query.elementText("username").equals(FB_USER)){
//					String acToken=query.elementText("password");
//					com.restfb.types.User user;
//					String userEmail="";
//					try{
//						FacebookClient facebookClient = new DefaultFacebookClient(acToken);
//						user=facebookClient.fetchObject("me", com.restfb.types.User.class);
//						userEmail=user.getEmail();
//					}
//					catch(Exception ex){
//						error.addElement("msg").addText("Invalid Access Token!");
//					}
//					//query ofUser for this email address
//					Connection con = null;
//					PreparedStatement pstmt = null;
//					ResultSet rs = null;
//					try {
//						Log.info("Checking Room: " + message.getTo().toBareJID()
//								+ " Other way: "
//								+ packet.getElement().attributeValue("to") + " From: "
//								+ message.getFrom().toBareJID());
//						con = DbConnectionManager.getConnection();
//						pstmt = con
//								.prepareStatement("SELECT * FROM moRoomDetails WHERE roomName = ?;");
//						pstmt.setString(1, message.getTo().toBareJID());
//						rs = pstmt.executeQuery();
//						// System.out.println(ii+"-->"+"------------------message adding starts---------------");
//						// System.out.println(ii+"-->"+"roomName(message.getTo().toBareJID())="+message.getTo().toBareJID());
//						// System.out.println(ii+"-->"+"message.getTo()="+message.getTo());
//						// Log.info("Starting Add..");
//						// System.out.println(ii+"-->"+"Starting Add..");
//						pstmt.executeQuery();
//						if (rs.next()) {
//							pstmt.setLong(10, msec);
//							// System.out.println(ii+"-->"+"befor execute");
//
//							
//						} catch (Exception e) {
//							// TODO Auto-generated catch block
//							Log.info("Error: Inserting Message ->" + e.getMessage());
//						}
//						}
//					
//					
//					
//				}
//				
//				
//				System.out.println("*******Hoooora");
//				String user="";
//				String pass="";
//				System.out.println(query.elementText("username"));
//				System.out.println(query.elementText("password"));
//				
//				
//			}
			
			//if (user = FB_USER and IQ.Type.error and jabber:iq:auth)
			//connect FB
			// problem in connection error: invalid access Token
			// get email address 
			// problem error :cannot access email address
			//query db with this email
			// if exist send back user
			//else error: user not registered yet.
			
			
			
			
			
			//if (iq.getgetType()==IQ.Type.
			
//			System.out.println("query.getNamespace()="+query.getNamespace());
//			System.out.println("query.getNamespacePrefix()="+query.getNamespacePrefix());
//			System.out.println("query.getNamespaceURI()="+query.getNamespaceURI());
//			
//			System.out.println("session.getAddress()="+session.getAddress());
//			System.out.println("session.getStatus()="+session.getStatus());
//			System.out.println("session.getStreamID()="+session.getStreamID());
			//System.out.println(session.getHostAddress());
			
			
				
			
		}
			
			
			
		// if ((packet instanceof Message) && !processed) {
		if ((packet instanceof Message) && !processed && incoming) {
			Log.info("Intercepted Incoming Message");

			Message message = (Message) packet;
			// System.out.println(ii+"-->"+"#######Intercomming######");
			// System.out.println(ii+"-->"+"Incomming:"+message.toXML());

			// System.out.println(ii+"-->"+"-----------message.getBody()="+message.toXML());
			Connection con = null;
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try {
				Log.info("Checking Room: " + message.getTo().toBareJID()
						+ " Other way: "
						+ packet.getElement().attributeValue("to") + " From: "
						+ message.getFrom().toBareJID());
				con = DbConnectionManager.getConnection();
				pstmt = con
						.prepareStatement("SELECT * FROM moRoomDetails WHERE roomName = ?;");
				pstmt.setString(1, message.getTo().toBareJID());
				rs = pstmt.executeQuery();
				// System.out.println(ii+"-->"+"------------------message adding starts---------------");
				// System.out.println(ii+"-->"+"roomName(message.getTo().toBareJID())="+message.getTo().toBareJID());
				// System.out.println(ii+"-->"+"message.getTo()="+message.getTo());
				// Log.info("Starting Add..");
				// System.out.println(ii+"-->"+"Starting Add..");

				if (rs.next()) {
					// System.out.println(ii+"-->"+"Starting Add..");
					Date currentDate = new Date();
					long msec = currentDate.getTime();

					// System.out.println(ii+"-->"+"Creating JSON");
					// Log.info("Creating JSON");

					JSONObject jsonObject = new JSONObject(message.getBody());
					jsonObject.put("logTime", msec);

					// System.out.println(ii+"-->"+"after JSON");
					// set postID="" if it doesn't exist
					if (jsonObject.isNull("postID")) {
						jsonObject.put("postID", "");
					}

					message.setBody(jsonObject.toString());

					// System.out.println(ii+"-->"+"jsonObject.toString()="+jsonObject.toString());
					// System.out.println(ii+"-->"+"message.getTo().toString()="+message.getTo().toString());

					//
					//
					// Log.info("Getting Components");
					// // System.out.println(ii+"-->"+"Getting Components");
					// String[] components =
					// message.getTo().toString().split("@");
					// String serviceDomain = components[1];
					// String roomName = components[0];
					//
					// //
					// System.out.println(ii+"-->"+"serviceDomain="+serviceDomain);
					// // System.out.println(ii+"-->"+"roomName2="+roomName);
					//
					// Log.info("Service Domain: " + serviceDomain
					// + " - Room Name: " + roomName);
					// MultiUserChatService mucService = null;
					//
					// // what is this part for?
					// // seems to get the service responsible service for the
					// // domain
					// // it is to find the service responsible to this chat
					// group
					// int i = 0;
					// for (MultiUserChatService curService : server
					// .getMultiUserChatManager()
					// .getMultiUserChatServices()) {
					// Log.info("Loop Domain Check: "
					// + curService.getServiceDomain());
					// if (curService.getServiceDomain().equals(serviceDomain))
					// {
					// mucService = curService;
					// //
					// System.out.println(ii+"-->"+"########################    "+"mucService.getNumberChatRooms()="+mucService.getNumberChatRooms());
					// break;
					// }
					// //
					// System.out.println(ii+"-->"+"######################## ="+i++);
					// }
					//
					// // get the chat room from the service responsible for
					// MUCRoom mucRoom = mucService.getChatRoom(roomName);
					//
					// boolean senderInRoom = false;
					//
					// // loop over all the users present in the chat room
					// for (MUCRole user : mucRoom.getOccupants()) {
					// if (message.getFrom().toBareJID()
					// .equals(user.getUserAddress().toBareJID()))
					// senderInRoom = true;
					//
					// Log.info("Get Occupants, From: "
					// + message.getFrom().toBareJID()
					// + " User Address: "
					// + user.getUserAddress().toBareJID());
					//
					// // sendMessage(
					// // user.getUserAddress().toBareJID(),
					// // mucRoom.getName() + "@"
					// // + mucService.getServiceDomain() + "/"
					// // + message.getFrom().toBareJID(),
					// // message.getBody(), message.getSubject());
					// }
					//
					// // maybe user currently is not in the chat room
					// if (!senderInRoom) {
					// Log.info("Sender Not in Room");
					// // System.out.println(ii+"-->"+"Sender Not in Room");
					//
					// // sendMessage(
					// // message.getFrom().toBareJID(),
					// // mucRoom.getName() + "@"
					// // + mucService.getServiceDomain() + "/"
					// // + message.getFrom().toBareJID(),
					// // message.getBody(), message.getSubject());
					// }

					// add message to the db
					// System.out.println(ii+"-->"+"---------Adding New Message------");
					// it doesn't show error in the case it has error in sql
					// statement ! it should be corrected
					// Log.info("Adding New Message");

					// --------------------------------
					// System.out.println(ii+"-->"+"Adding New Message");
					// PreparedStatement pstmt=null;

					String user = message.getFrom().toBareJID();
					int roomID = rs.getInt("roomID");
					try {

						pstmt = con
								.prepareStatement("INSERT INTO moMessages(roomID, sender, nickname, "
										+ "logTime,msg, body, postID, messageID, teleported,lastReplyTime) VALUES (?,?,?,?,?,?,?,?,?,?)");
						pstmt.setInt(1, roomID);
						pstmt.setString(2, user);
						pstmt.setString(3, user);
						pstmt.setLong(4, msec);
						// System.out.println(ii+"-->"+"jsonObject.getString(threadSubject)="+jsonObject.getString("threadSubject"));

						pstmt.setString(5, jsonObject.getString("messageBody"));
						pstmt.setString(6, message.getBody());

						pstmt.setString(7, jsonObject.getString("postID"));
						pstmt.setString(8, jsonObject.getString("messageID"));
						pstmt.setInt(9, Integer.parseInt(jsonObject
								.getString("teleported")));
						pstmt.setLong(10, msec);
						// System.out.println(ii+"-->"+"befor execute");

						pstmt.executeUpdate();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						Log.info("Error: Inserting Message ->" + e.getMessage());
					}
					// --------------------------------

					if (jsonObject.getString("postID") != "") { // this is a
																// reply
						// updating thread last reply field if the message is
						// reply
						// -------------------
						try {
							pstmt = con
									.prepareStatement("update moMessages set lastReplyTime=?, replyCount=replyCount+1 where messageID=?");
							pstmt.setString(1, String.valueOf(msec));
							pstmt.setString(2, jsonObject.getString("postID"));
							pstmt.executeUpdate();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							Log.info("Error: updating lastReplyTime ->"
									+ e.getMessage());
						}
						// -------------------

						// updating MsgReadTrack table for post
						// -------------------
						// first it should get user of the post
						/*
						 * rules: 1- if somebody reply its post it is not
						 * tracked 2- if somebody replied its reply it is not
						 * tracked
						 */
						try {

							pstmt = con
									.prepareStatement("select sender from moMessages where messageID=? and sender<>?");
							pstmt.setString(1, jsonObject.getString("postID"));
							pstmt.setString(2, user);
							rs = pstmt.executeQuery();

							if (rs.next()) {
								pstmt = con
										.prepareStatement("insert into moMsgReadTrack (messageID,relMessageID,user,relUser,relLogTime) values(?,?,?,?,?)");
								pstmt.setString(1,
										jsonObject.getString("postID"));
								pstmt.setString(2,
										jsonObject.getString("messageID"));
								pstmt.setString(3, rs.getString("sender")); // user
																			// who
																			// should
																			// read
																			// this
								pstmt.setString(4, user);
								pstmt.setLong(5, msec);
								pstmt.executeUpdate();
							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							Log.info("Error: Updating Message Track Table for Posts ->"
									+ e.getMessage());
						}

						// -------------------

						// updating MsgReadTrack table for replies
						// -------------------
						try {
							pstmt = con
									.prepareStatement("select t1.messageID,t1.sender,t1.postID from moMessages as t1"
											+ " inner join moMessages as t2 on t1.postID=t2.messageID where"
											+ " t1.postID=? and t1.messageID<>? and t1.sender<>? "
											+ "and t2.sender<>? and t2.sender<>t1.sender;");
							pstmt.setString(1, jsonObject.getString("postID"));
							pstmt.setString(2,
									jsonObject.getString("messageID"));
							pstmt.setString(3, user);
							pstmt.setString(4, user);
							rs = pstmt.executeQuery();

							while (rs.next()) { // it return record only if the
												// user
												// has device
												// Token registered!
								pstmt = con
										.prepareStatement("insert into moMsgReadTrack (messageID,relMessageID,user,postID,relUser,relLogTime) values(?,?,?,?,?,?)");
								pstmt.setString(1, rs.getString("messageID")); // sibling
																				// messageIDs
								pstmt.setString(2,
										jsonObject.getString("messageID")); // current
																			// messageID
								pstmt.setString(3, rs.getString("sender")); // user
																			// who
																			// should
																			// read
																			// this
								pstmt.setString(4, rs.getString("postID"));
								pstmt.setString(5, user);
								pstmt.setLong(6, msec);
								pstmt.executeUpdate();

							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							Log.info("Error: Updating Message Track Table for Replies ->"
									+ e.getMessage());
						}
						// ----------------------

					}

					// setStatement.close();
					// System.out.println(ii+"-->"+"quary executes!!");

					// Adding notification Messages

					notifyRoom(jsonObject.getString("postID"), roomID, user,
							jsonObject.getString("messageBody"),
							jsonObject.getString("messageID")); // this is
																// invoked in
																// any case
					if (jsonObject.getString("postID") != "") {
						// these are invoked when the message is thread
						notifyPost(jsonObject.getString("postID"), user,
								jsonObject.getString("messageBody"),
								jsonObject.getString("messageID"));
						notifyReply(jsonObject.getString("postID"), user,
								jsonObject.getString("messageBody"),
								jsonObject.getString("messageID"));
					}

					// throw new PacketRejectedException();
				} else {
					Log.info("Error: Room Not Found:"
							+ message.getTo().toBareJID());
					// System.out.println(ii + "-->" + "Room Not Found");
				}

			} catch (SQLException sqle) {
				Log.info("Error:" + sqle.getMessage());

			} catch (JSONException jsone) {
				Log.info("Error:" + jsone.getMessage());

			} catch (Exception ex) {
				Log.info("Error:" + ex.getMessage());

			} finally {
				DbConnectionManager.closeConnection(pstmt, con);
				Log.info("End of Message Interception!");
			}
			// Log.info("Try Exited");
			// System.out.println(ii+"-->"+"Try Exited");
		}

		// this seems to intercept automatic responses to users.
		Message message = (Message) packet;

		// if ((packet instanceof Message) && !incoming
		// && message.getBody() != null
		// && message.getBody().startsWith("{")
		// && message.getBody().endsWith("}")) {
		// Log.info("Intercepted Outgoing Message, Body: " + message.getBody());
		//
		// addKudosToElement(message.getElement().element("body"), message
		// .getTo().toBareJID());
		// System.out.println("to: " +packet.toXML());
		// }

		if ((packet instanceof Message) && !incoming && processed
				&& message.getBody() != null
				&& message.getBody().startsWith("{")
				&& message.getBody().endsWith("}")) {
			Log.info("Intercepted Outgoing Message, Body: " + message.getBody());

			addKudosToElement(message.getElement().element("body"), message
					.getTo().toBareJID());

			// addRoomTypeToElement // if it is not included in the sent
			// message!
			// System.out.println("to: " +packet.toXML());
		}
	}

	private void notifyReply(String relPostID, String relUser, String relMsg,
			String relMsgID) {
		// query to see who registered for this room notification.
		/*
		 * find the users who they also had reply to this post for each create
		 * notification package send package to him/her
		 */
		Log.info("Notify Reply Func. starts");
		// return all replies to a given thread
		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		Date t = new Date();
		try {

			con = DbConnectionManager.getConnection();

			pstmt = con
					.prepareStatement("select count(distinct t1.relMessageID) as badge, t1.messageID,"
							+ "t1.user,t1.postID,t2.deviceToken from  moMsgReadTrack as t1 "
							+ "left join moUser as t2 on t1.user=t2.user "
							+ "where t2.valid=1 and nType3=1 and postID=? and t1.user<>? group by t1.user;");
			// t1.user<>? -->because we don't want to send notification when
			// relUser replies to possible previously replies messages

			pstmt.setString(1, relPostID);
			pstmt.setString(2, relUser);

			rs = pstmt.executeQuery();

			/*
			 * if we want to send notification for a reply while we already send
			 * for it's post in the case that their sender is same user: you
			 * should query db and get sender of relPostID (who is sender of
			 * Post) so during below loop just check if user=sender of relPostID
			 * don't add it to notification! if clause will be (postID=relPostID
			 * and user=sender of relPostID message) but the first condition
			 * already hold for all messages, so we need to just check second
			 * one.
			 */
			ArrayList<PayloadPerDevice> plpds = new ArrayList<PayloadPerDevice>();
			while (rs.next()) { // it return record only if the user has device
								// Token registered!
				// Log.info("Notifing User Reply: " + rs.getString("user"));
				// Payload payload = PushNotificationPayload.combined(relUser
				// + " Replied your Reply! " + relMsg, rs.getInt("badge"),
				// "default");
				// payload.addCustomDictionary("NType", 3);
				// payload.addCustomDictionary("messageID", relMsg);

				final String pushMsg = BoomUtil.makePushMessage(
						BoomUtil.extractNameofUser(relUser)
								+ " has replied to your post!",
						rs.getInt("badge"), "default",
						relPostID, 3);
				Payload payload = new Payload() {
					public String toString() {
						return pushMsg;
					};
				};
				PayloadPerDevice plpd = new PayloadPerDevice(payload,
						rs.getString("deviceToken"));
				plpds.add(plpd);

			}
			pstmt.close();
			con.close();

			int threadCount = plpds.size() / 4 + 1;
			if (!plpds.isEmpty()) {
				// send package to this user.
				PushThread pt = new PushThread(plpds, IS_PRODUCTION,
						threadCount);
				pt.start();
			}

		} catch (SQLException sqle) {
			Log.info("Error:" + sqle.getMessage());
		} catch (Exception ex) {
			Log.info("Error:" + ex.getMessage());
		} finally {
			DbConnectionManager.closeConnection(pstmt, con);
		}

	}

	private void notifyPost(String relPostID, String relUser, String relMsg,
			String relMsgID) {
		// query to see who registered for this room notification.
		/*
		 * find the user of the thread create notification package send package
		 * to him/her
		 */
		Log.info("Notify Post Func. starts");
		// return all replies to a given thread
		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		// Date t = new Date();
		try {

			con = DbConnectionManager.getConnection();

			pstmt = con
					.prepareStatement("select count(t1.messageID) as badge, t1.messageID, "
							+ "t1.user,t1.postID,t2.deviceToken from  moMsgReadTrack as t1 "
							+ "left join moUser as t2 on t1.user=t2.user "
							+ "where t2.valid=1  and nType2=1 and messageID=? and t1.user<>?;");
			pstmt.setString(1, relPostID);
			pstmt.setString(2, relUser);

			rs = pstmt.executeQuery();

			int badge = 0;
			// ArrayList<String> deviceTokens = new ArrayList<String>();
			ArrayList<PayloadPerDevice> plpds = new ArrayList<PayloadPerDevice>();
			if (rs.next()) { // it return record only if the user has device
								// Token registered!

				final String pushMsg = BoomUtil.makePushMessage(
						BoomUtil.extractNameofUser(relUser)
								+ " has replied to your post!",
						rs.getInt("badge"), "default",
						relPostID, 2);
				Payload payload = new Payload() {
					public String toString() {
						return pushMsg;
					};
				};

				PayloadPerDevice plpd = new PayloadPerDevice(payload,
						rs.getString("deviceToken"));
				plpds.add(plpd);
			}

			pstmt.close();
			con.close();

			if (!plpds.isEmpty()) {
				// send package to this user.
				PushThread pt = new PushThread(plpds, IS_PRODUCTION, 1);
				pt.start();
			}

		} catch (SQLException sqle) {
			Log.info("Error:" + sqle.getMessage());
		} catch (Exception ex) {
			Log.info("Error:" + ex.getMessage());
		} finally {
			DbConnectionManager.closeConnection(pstmt, con);
		}

	}

	private void notifyRoom(String relPostID, int roomID, String relUser,
			String relMsg, String relMsgID) {
		// TODO Auto-generated method stub

		// query to see who registered for this room notification.
		/*
		 * for each user create notification package send notification package.
		 */
		Log.info("Notify Room Func. starts");
		// return all replies to a given thread
		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		Date t = new Date();
		String parentPostID; // this is set to messageID if the message is post
								// and to postID of reply if message is reply
		try {

			con = DbConnectionManager.getConnection();

			if (relPostID != "") { // already might notified user
				parentPostID = relPostID;
				pstmt = con
						.prepareStatement("select t1.user,t1.roomID,t1.deviceToken,t2.user from "
								+ "movRoomsNotifyList as t1 left join (select distinct t1.user from  "
								+ "moMsgReadTrack as t1 where (messageID=? or postID=?) and t1.user<>?) "
								+ "as t2 on t1.user=t2.user where t1.roomID=? and t1.endTime > ? and t1.user<>? "
								+ "and t1.nType4=1 and t2.user is null;");
				pstmt.setString(1, relPostID);
				pstmt.setString(2, relPostID);
				pstmt.setString(3, relUser);
				pstmt.setInt(4, roomID);
				pstmt.setLong(5, t.getTime());
				pstmt.setString(6, relUser);

				System.out
						.println("select t1.user,t1.roomID,t1.deviceToken,t2.user from "
								+ "movRoomsNotifyList as t1 left join (select distinct t1.user from  "
								+ "moMsgReadTrack as t1 where (messageID='"
								+ relPostID
								+ "' or postID='"
								+ relPostID
								+ "') and t1.user<>'"
								+ relUser
								+ "') "
								+ "as t2 on t1.user=t2.user where t1.roomID="
								+ roomID
								+ " and t1.endTime > "
								+ t.getTime()
								+ " and " + "t2.user is null;");
				// System.out.println("@@@@@@@@@"+t.getTime());
			} else {
				parentPostID = relMsgID;
				pstmt = con
						.prepareStatement("select t1.user,t1.roomID,t1.deviceToken from "
								+ "movRoomsNotifyList as t1 where t1.roomID=? and t1.endTime > ? "
								+ "and t1.user<>?");
				pstmt.setInt(1, roomID);
				pstmt.setLong(2, t.getTime());
				pstmt.setString(3, relUser);
				System.out
						.println("select t1.user,t1.roomID,t1.deviceToken from "
								+ "movRoomsNotifyList as t1 where t1.roomID="
								+ roomID
								+ " and "
								+ "t1.endTime > and t1.user<>?"
								+ t.getTime()
								+ ";");
			}
			rs = pstmt.executeQuery();

			// ArrayList<String> deviceTokens = new ArrayList<String>();
			ArrayList<PayloadPerDevice> plpds = new ArrayList<PayloadPerDevice>();

			while (rs.next()) {
				// Log.info("Notifing User: " + rs.getString("user"));
				// prepare package for this user
				// Payload payload = PushNotificationPayload.combined(
				// "New Message in Room!", 1, "default");
				// payload.addCustomDictionary("NType", 4);
				// payload.addCustomDictionary("messageID", relMsg);

				final String pushMsg = BoomUtil.makePushMessage(
						BoomUtil.extractNameofUser(relUser) + " said \""
								+ BoomUtil.trancateMsg(relMsg, 80) + "\"", 1,
						"default", parentPostID, 4);
				Payload payload = new Payload() {
					public String toString() {
						return pushMsg;
					};
				};

				PayloadPerDevice plpd = new PayloadPerDevice(payload,
						rs.getString("deviceToken"));
				plpds.add(plpd);
				// send package to this user.
			}
			pstmt.close();
			con.close();

			// for each device token send information
			// Payload payload = PushNotificationPayload.combined(
			// "New Message in Room!", 1, "default");
			PushThread pt = new PushThread(plpds, IS_PRODUCTION, 2);
			pt.start();

		} catch (SQLException sqle) {
			Log.info("Error:" + sqle.getMessage());
		} catch (Exception ex) {
			Log.info("Error:" + ex.getMessage());
		} finally {
			DbConnectionManager.closeConnection(pstmt, con);
		}

	}

	public Vector<String> getKudosInfo(String messageID, String user) {

		Vector<String> v = new Vector<String>();
		Connection con = null;
		PreparedStatement pstmt = null;

		try {
			ResultSet rs = null;
			con = DbConnectionManager.getConnection();
			pstmt = con
					.prepareStatement("SELECT sum(value) as ksum FROM moKudos WHERE messageID =? and value=? group by messageID;");
			pstmt.setString(1, messageID);
			pstmt.setInt(2, 1);

			rs = pstmt.executeQuery();
			int x = 0;
			if (rs.next())
				x = rs.getInt("ksum");
			v.add(Integer.toString(x));

			pstmt.setInt(2, -1);
			rs = pstmt.executeQuery();
			x = 0;
			if (rs.next())
				x = rs.getInt("ksum");
			v.add(Integer.toString(x));

			// setting user kudos
			pstmt = con
					.prepareStatement("SELECT value FROM moKudos WHERE messageID = ? AND username = ?");
			// pstmt.setString(1, object.getString("postID"));
			pstmt.setString(1, messageID);
			pstmt.setString(2, user);
			rs = pstmt.executeQuery();

			if (rs.next())
				v.add(Integer.toString(rs.getInt("value")));
			else
				v.add("0");

		} catch (Exception e) {
			Log.info("Error: " + e.getMessage());
		} finally {
			DbConnectionManager.closeConnection(pstmt, con);
		}
		return v;
	}

	private Message createServerMessage(String to, String subject, String body) {
		Message message = new Message();
		message.setTo(to);
		message.setFrom(serverAddress);
		message.setType(Message.Type.chat);
		// message.addChildElement( "type", "mo").addText("addRoom");
		// message.addChildElement( "result", "mo").addText("success");
		if (subject != null) {
			message.setSubject(subject);
		}
		message.setBody(body);
		return message;
	}

	public MUCRoom createChatroom() {
		MUCRoom room = null;

		try {
			if (server.getMultiUserChatManager()
					.getMultiUserChatServicesCount() == 0) {
				server.getMultiUserChatManager().createMultiUserChatService(
						"conference", null, false);
			}
			MultiUserChatService service = null;
			for (MultiUserChatService curService : server
					.getMultiUserChatManager().getMultiUserChatServices()) {
				if (curService.getNumberChatRooms() < 10000) {
					service = curService;
					break;
				}
			}
			if (service == null) {
				service = server
						.getMultiUserChatManager()
						.createMultiUserChatService(
								"conference"
										+ server.getMultiUserChatManager()
												.getMultiUserChatServicesCount(),
								null, false);
			}

			room = service.getChatRoom(String.valueOf(UUID.randomUUID()),
					server.createJID("autoChatroomCreator", null));
			room.unlock(room.getRole());
			room.setCanOccupantsChangeSubject(true);
			room.setMaxUsers(0);
			room.setPersistent(true);
			room.setPublicRoom(true);
			room.setChangeNickname(false);
			room.setLogEnabled(false);
			room.setMembersOnly(false);
			room.setCanOccupantsInvite(true);
			room.setCanAnyoneDiscoverJID(true);
			room.saveToDB();
		} catch (Exception e) {
			// Log.debug("*** Exception in createChatroom()");
			Log.info("Error: in creating Room createChatroom fun.");
			// Log.error(e);
		}

		return room;
	}

	// added one line
	// added line 2

	public boolean addKudosToElement(Element mainElement, String username) {
		Log.info("Adding Kudos");

		Connection con = null;
		PreparedStatement pstmt = null;

		try {
			// JSONObject object = (JSONObject) new
			// JSONTokener(packetElement.element("subject").getText()).nextValue();
			JSONObject object = new JSONObject(mainElement.getText());

			ResultSet rs = null;
			con = DbConnectionManager.getConnection();
			// pstmt =
			// con.prepareStatement("SELECT * FROM moKudos WHERE postID = ? AND messageID = ? AND value = ?");
			pstmt = con
					.prepareStatement("SELECT sum(value) as ksum FROM moKudos WHERE messageID =? and value=? group by messageID;");
			// System.out.println("object.getString(postID)="
			// + object.getString("postID"));
			pstmt.setString(1, object.getString("messageID"));
			// pstmt.setString(3, packet.getFrom().toBareJID());

			pstmt.setInt(2, 1);
			rs = pstmt.executeQuery();
			int x = 0;
			if (rs.next())
				x = rs.getInt("ksum");
			object.put("kudosUp", Integer.toString(x));

			pstmt.setInt(2, -1);
			rs = pstmt.executeQuery();
			x = 0;
			if (rs.next())
				x = rs.getInt("ksum");
			object.put("kudosDown", Integer.toString(x));

			// setting user kudos
			pstmt = con
					.prepareStatement("SELECT value FROM moKudos WHERE messageID = ? AND username = ?");
			// pstmt.setString(1, object.getString("postID"));
			pstmt.setString(1, object.getString("messageID"));
			pstmt.setString(2, username);
			rs = pstmt.executeQuery();

			if (rs.next())
				object.put("userKudos", rs.getInt("value"));
			else
				object.put("userKudos", 0);

			mainElement.setText(object.toString());
		} catch (Exception e) {
			Log.info("Error: " + e.getMessage());
		} finally {
			DbConnectionManager.closeConnection(pstmt, con);
		}

		return true;
	}

	public boolean sendMessage(String to, String from, String body,
			String subject) {
		Log.info("Sending Message");
		Message message = new Message();

		message.setTo(to);
		message.setFrom(from);
		message.setBody(body);
		message.setSubject(subject);
		message.setType(Message.Type.groupchat);

		packRouter.route(message);

		return true;
	}
}