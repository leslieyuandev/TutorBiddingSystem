package model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import mainview.Application;

/**
 * This class models a User
 */
public class User extends Observable implements Model {
	private String id;

	private String givenName;

	private String familyName;

	private String userName;

	private boolean isStudent, isTutor;

	private List<String> monitor;


	public User(String id, String givenName, String familyName, String username, boolean isStudent, boolean isTutor) {
		this.id = id;
		this.givenName = givenName;
		this.familyName = familyName;
		this.userName = username;
		this.isStudent = isStudent;
		this.isTutor = isTutor;
	}

	public User () {}

	public User(JsonNode node) {
		this.id = node.get("id").textValue();
    	this.givenName = node.get("givenName").textValue();
    	this.familyName = node.get("familyName").textValue();
    	
    	this.userName = (node.get("userName") != null) ?node.get("userName").textValue() : null;
    	this.isTutor = (node.get("isTutor") != null) ?node.get("isTutor").asBoolean() : null;
    	this.isStudent = (node.get("isStudent") != null) ?node.get("isStudent").asBoolean() : null;
		this.monitor = (node.get("additionalInfo").isEmpty() | node.get("additionalInfo").get("monitor") == null) ? new ArrayList<>() : getMonitor(node.get("additionalInfo").get("monitor").iterator());
	}

	private static List<String> getMonitor(Iterator<JsonNode>iter) {
		List<String> list = new ArrayList<String>();
		while (iter.hasNext()) {
		    list.add(iter.next().textValue());
		}
		return list;
	}

	public void addBidToMonitor(Bid b) {
		if (this.monitor(b))
			return;
		
		this.monitor.add(b.getId());
		patch();
		this.inform(EventType.USER_SUBSCRIBE_NEW_BID);
	}

	public boolean monitor(Bid b) {
		for (String s : this.monitor) 
			if (s.equals(b.getId()))
				return true;
		return false;
	}
	
	/**
	 * Patch user's monitor information
	 */
	private void patch() {
		String url = Application.rootUrl + "/user/" + this.id;

		String jsonString = "{" + "\"additionalInfo\":" + "{" +
		  		"\"monitor\":" + getMonitorJson() + "}" + "}";

		Model.patch(url, jsonString);
	}

	private String getMonitorJson() {
		String jsonString = "[";
		String comma = "";
		for (String s : this.monitor) {
			jsonString = jsonString + comma + "\"" +  s + "\"";
			comma = ",";
		}
		jsonString = jsonString + "]";
		System.out.print(jsonString);
		return jsonString;
	}

	public static User logIn(String username, String password) {
		String usersLoginUrl = Application.rootUrl + "/user/login";
		
		String jsonString = "{" +
			      "\"userName\":\"" + username + "\"," +
			      "\"password\":\"" + password + "\"" +
			      "}";
		
		HttpClient client = HttpClient.newHttpClient();
	    HttpRequest request = HttpRequest.newBuilder(URI.create(usersLoginUrl + "?jwt=true"))
	  	      .setHeader("Authorization", Application.myApiKey)
		      .header("Content-Type","application/json")
		      .POST(HttpRequest.BodyPublishers.ofString(jsonString))
		      .build();
	    
	    HttpResponse<String> response;
		try {
			response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				String[] chunks = response.body().split("\\.");
				Base64.Decoder decoder = Base64.getDecoder();
				String payload = new String(decoder.decode(chunks[1]));

				ObjectNode node = new ObjectMapper().readValue(payload, ObjectNode.class);
				String userId = node.get("sub").textValue();
				
				return User.getUserbyId(userId);
			}
			else {
				System.out.println(response.statusCode() + response.body());
		    	return null;
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public String getId() {
		return id;
	}

	public String getGivenName() {
		return givenName;
	}

	public String getFamilyName() {
		return familyName;
	}

	public String getUsername() {
		return userName;
	}
	
	public String getFullName() {
		return givenName + " " + familyName;
	}

	public boolean isStudent() {
		return isStudent;
	}

	public boolean isTutor() {
		return isTutor;
	}

	public int getCompetency(String subjectId) {
		ObjectNode jsonNodes = Model.get("/user/", this.id + "?fields=competencies&fields=competencies.subject");
		
		assert (jsonNodes.get("competencies").isArray());
		Iterator<JsonNode> competenciesIter = jsonNodes.get("competencies").iterator();
		while (competenciesIter.hasNext()) {

			JsonNode node = competenciesIter.next();
			int currLv = node.get("level").intValue();
			String currId = node.get("subject").get("id").textValue();
			
			if (currId.equals(subjectId)) {
				System.out.println(currLv);
				return currLv;
			}
		}	
		return -1;
	}
	
	/**
	 * Check tutor's competency using their Id
	 * @param tutorId
	 * @param subjectId
	 * @return
	 */
	public static int getCompetency(String tutorId, String subjectId) {
		ObjectNode jsonNodes = Model.get("/user/", tutorId + "?fields=competencies&fields=competencies.subject");
		
		assert (jsonNodes.get("competencies").isArray());
		Iterator<JsonNode> competenciesIter = jsonNodes.get("competencies").iterator();
		while (competenciesIter.hasNext()) {

			JsonNode node = competenciesIter.next();
			int currLv = node.get("level").intValue();
			String currId = node.get("subject").get("id").textValue();
			
			if (currId.equals(subjectId)) {
				System.out.println(currLv);
				return currLv;
			}
		}	
		return -1;
	}
	
	public static List<String> getAllTutorsId() {
		List<String> allTutorsId = new ArrayList<String>();
		for (ObjectNode node : Model.getAll("/user")) {
			if (node.get("isTutor").asBoolean() == true)
				allTutorsId.add(node.get("id").textValue());
		}
		return allTutorsId;
	}
	
	public List<Bid> getInitiatedBids() {
		List<Bid> bids = new ArrayList<Bid>();
		ObjectNode nodes = Model.get("/user/", this.id + "?fields=initiatedBids");
		assert (nodes.get("initiatedBids").isArray());
		Iterator<JsonNode> iter = nodes.get("initiatedBids").iterator();
		while (iter.hasNext()) {
			JsonNode node = iter.next();
			bids.add(new Bid(node, this));
		}

		return Bid.screenClosedBid(bids);
	}
	
	public static User getUserbyId(String userId) {
		ObjectNode node = Model.get("/user/", userId);
		return new User(node);
	}
	
	@Override
	public String toString() {
		return "User " +
				"Name='" + givenName + '\'' +
				", familyName='" + familyName + '\'' +
				", username='" + userName + '\'' +
				", isStudent=" + isStudent +
				", isTutor=" + isTutor +
				'}';
	}
}