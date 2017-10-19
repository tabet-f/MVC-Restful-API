package hello;


import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import redis.clients.jedis.Jedis;

 
@RestController
public class MainFile<RedisMonitorHandler> {
    
	//CURL POST UPLOAD REQUEST: curl -X POST localhost:8080/SV -F "file=@C:/Users/donate/Documents/SPRING-WORKSPACE/jsontwo.json"
   
	static boolean isURIFileRecieved = false;
	
	static boolean isvalidSchema = false; //Flag for Schema VS Json Validation
    static String SchemaFileName=""; //Schema File Name
    static String SchemaFilePath=""; //Schema Directory Path 
    static String SchemaFileContent="";//Hold  Json Schema 
	
	static String JsonFileName=""; //Json File Name
    static String JsonFilePath=""; //Json Directory Path
    static String JsonFileContent="";
    
    static String JsonMERGEFileName=""; //Json MERGE File Name
    static String JsonMERGEFilePath=""; //Json MERGE File Path
    static String JsonMERGEFileContent=""; //Json MERGE File Body or Content
    
    static String JsonPUTFileName=""; //Json File Name
    static String JsonPUTFilePath=""; //Json Directory Path
    static String JsonPUTFileContent="";//Json PUT File Body or Content
    
    static boolean duplicateEntry = false; //Flag to identify duplicate subkeys between posted doc and redis(if a subkey is already in redis)
    
    private static ArrayList<String> responseLists = new ArrayList<String>();//Hold the display results (for display only)
    
    //Hold sub keys (address_789)
	//static ArrayList<String> SubKeysCheck = new ArrayList<String>();
	static ArrayList<String> OldJsonPayload = new ArrayList<String>();
	static ArrayList<String> NewJsonPayload = new ArrayList<String>();
   
	@SuppressWarnings("rawtypes")
	//Hold the MainKey as a key for the hashmap and all its subkeys inside an arraylist that points to the mainkey
	static HashMap<String, ArrayList> RefMap = new HashMap<String, ArrayList>();
	
	//Jedis Object
    Jedis jedis = new Jedis();
    
    //Elastic Search Global Variables
    //Node
    Node node = NodeBuilder.nodeBuilder().node();
    //Index = Database
    final static String ESIndex = "finalproject";
    //Type = DB Table 
    final static String ESType = "user";
    static String JsonFormatString = "";
    HashMap<String, String> RequestData = new HashMap<>();
   
    static Map<String, Object> JsonAsMapforES = new HashMap<String, Object>();
    
	int callCounter = 0;
    
	
	static String headerETag = "";//ResponseHeader ETAG Value
	static String lastModified; //Last Modified Header Date
	
	
    @RequestMapping("/")
	public String home() {
		return "You're Token Has Been Validated!";
	}
    
   

    
 /* *SECTION 1.0: CRUD REQUEST HANDLING METHODS FOR CRUD API* */   
 /* ***********************STARTS HERE*********************** */
    
    
    //Schema POST Request for SCHEMA DOCUMENT
    @RequestMapping(value = "/schema", method = RequestMethod.POST)
    public String StoreSchema(@RequestParam("file") MultipartFile file) { 
    	
		try {
			
			//Deleting the Index from ES ---> Means Flush Elastic Search
			//DeleteESIndex();
			//Adding request type to responseList
			
			
			addResponse(true, "<HTTP-POST REQUEST>");
			
			//get the JSONSchema filename & path from URI
	    	getFilePathFromURI(file,2);  
	    	
	    	//Parse the Json Payload
			JSONParser parser = new JSONParser();
			Object obj = parser.parse(new FileReader(SchemaFilePath));
			
			if(obj!=null){
				 //Get Object.toString from the Payload
				JSONObject jsonObj = (JSONObject) obj;
				SchemaFileContent = jsonObj.toString();
				jedis.set("Schema", SchemaFileContent);//Store Schema in Redis
				//response = "---------SCHEMA File Successfully Received \n ---------SCHEMA File Successfully Stored Into Redis---------";
			    //responseLists.add("<SUCCESS>: SCHEMA File ("+SchemaFileName+") Stored Into REDIS.");
			    addResponse(true, "SCHEMA File ("+SchemaFileName+") Stored Into REDIS");
			}
			
			
		} catch (ParseException e) {
			addResponse(false, "JSON SCHEMA SYNTAX IS NOT VALID => "+e.toString());
		}
		   catch(IOException e){
		   addResponse(false, "IO Execption => "+e.toString());
		}
	
		
		return displayResponseStatus();//Print out Operations Status
		
    }
    
    
  
    
    
    //JSON DOC POST Request for JSON DOCUMENT
    @RequestMapping(value = "/user", method = RequestMethod.POST)
    public synchronized String create(@RequestParam("file") MultipartFile file) {        
        
    	 //Flush Previous Data Except SCHEMA (where key="Schema" in Redis)
    	 //flushRedisValueNOTSchema();
    	
    	//Showing Request Type
    	addResponse(true,"<HTTP-POST REQUEST>");
    	
    	//get the JSON file from URI
    	getFilePathFromURI(file,1);
    	
    	//Validate Json File Syntax (Simple Json Lib)
    	if(JsonSyntaxValidation()==true){
    	
    			//Check is Schema is Stored In Redis Before Do the MATCHING	
    			if(jedis.exists("Schema")){
    					
    						//If Schema found in redis call JsonAgainstSchema
    						if(JsonAgainstSchema(JsonFileContent)==true){//if Json MATCH Json Schema 
    								try {
    									JacksonParser();
    									ProcessIndexStack();
    									
    								 } catch (IOException e) {
    									
    									e.printStackTrace();
    								 }
    						
    						}//END if(JsonAgainstSchema()==true) IF-STMT
    	
    			}//END jedis.exits IF-STMT
    		
    	 //else if Schema Not Found in Redis (JsonSyntaxValidation IF-STMT)
         else{
         addResponse(false,"SCHEMA IS MISSED, UPLOAD JSON SCHEMA FIRST");
        }
    	
    	}//END JsonSyntaxValidation IF-STMT
    	
    	
    	return displayResponseStatus();
    	
            
    }
    
    
    
    //USER GET REQUEST
	@RequestMapping(value = "/user/{user_id}", method = RequestMethod.GET) 
    public @ResponseBody String getReport(
            @PathVariable("user_id") String userid, 
            HttpServletRequest request, 
            HttpServletResponse response) {
       
	//true means continue serve the resource normally
	if(HandleETagStatusMatch_NoneMatch(request, response)==true){
		   
		
    	//Adding request type to responseList
	   addResponse(true, "<HTTP-GET REQUEST>");
	   
	   
	   //Call ProcessGetRequest which will return data fetched with that key
       ProcessGetRequest(userid);
	}
      
       return displayResponseStatus();
    }
            
    
    
    //USER DELETE REQUEST
    @RequestMapping(value = "/user/{user_id}", method = RequestMethod.DELETE)
    public @ResponseBody synchronized String deleteReport(
            @PathVariable("user_id") String userid, 
            HttpServletRequest request, 
            HttpServletResponse response) {
        
    	//Populate RefMaps on Startup
    	populateMapsonStartUp();
    	
    	addResponse(true, "<HTTP-DELETE REQUEST>");
    	
    	//Call Delete Method and pass MainKey which is userid
    	deleteDataFromRedis(userid);
    	
    	
       return displayResponseStatus();

     }
    
    
    
    //USER PATCH (MERGE) REQUEST
    @RequestMapping(value = "/user/{user_id}", method = RequestMethod.PATCH)
    public  @ResponseBody String mergeReport(
            @PathVariable("user_id") String userid,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request, 
            HttpServletResponse response) {
    	
    	//true means continue serve the resource normally
    	if(HandleETagStatusMatch_NoneMatch(request, response)==true){
    		
    		
    	
    	    addResponse(true, "<HTTP-PATCH REQUEST>");
    	    //MANDATORY TO CALL THIS FUNCTION AT THE BEGINNING OF ANY REQUEST METHOD HANDLER
    		populateMapsonStartUp();//Populate RefMaps from Redis 
    	
    	
    	    //If user exists then continue
            if(CheckifUserExists(userid)==true){
            	
            //get the JSONMerge file from URI
    	    getFilePathFromURI(file,3);
            	
        	try {
        	
        	JsonMERGEFileContent =new String(Files.readAllBytes(Paths.get(JsonMERGEFilePath)));
        	JSONParser parser = new JSONParser();
        	Object obj = parser.parse(JsonMERGEFileContent);
        	JSONObject jsonObj = (JSONObject) obj;
			
			String keyStr="";//propertyname ex-> {"propertyname":"value"}
			String newpayload = jsonObj.toString();

		     //Hold sub keys (address_789)
			 ArrayList<String> SubKeys = new ArrayList<String>();
			
			 String payload=""; //Actual Payload 
			 String actualKey=""; //_type+_id
			 
			
			 for (Object key : jsonObj.keySet()) {
			        //Hold key
			        keyStr = (String)key;
			        Object keyvalue = jsonObj.get(keyStr); //Hold Value

			        //if JSONObject
			        if (keyvalue instanceof JSONObject){
			        	 //The actual field data is called keyvalue 
                        payload = keyvalue.toString();
                        //Getting the key(_type+_id= actualKEY) from the String
			        	actualKey = GetkeyfromString(payload);
                        SubKeys.add(actualKey);
			        }
			        
			       //if JSONArray
			        else if (keyvalue instanceof JSONArray){
			        	 //Json Value to toString
			        	 payload = keyvalue.toString();
			        	 //Getting the key(_type+_id= actualKEY) from the String
			        	 actualKey = GetkeyfromString(payload);
			        	 SubKeys.add(actualKey);
			        }
			        
			 }//end of for    
			//Getting Subkey from Subkeys Arraylist 
			String subky = SubKeys.toString().replaceAll("\\[", "").replaceAll("\\]","");
			
			//Call Process Merge After Getting New Json SUBKEY FROM MERGE FILE BODY
			processPatchRequest(userid, subky,  JsonMERGEFileContent, newpayload); 
			
		
			 
			} catch (FileNotFoundException e) {
				
				//e.printStackTrace();
			} catch (IOException e) {
				//addResponse(false,"MERGE Body Payload Syntax Is Not Valid"); 
			} catch (ParseException ex) {
				addResponse(false,"MERGE Body Payload Syntax Is Not Valid");
			}

			
        }
    	
    }  
    	//String r= ProcessGetRequest(userid);
		return displayResponseStatus();

     }
    
    
    
    //USER PUT REQUEST
    @RequestMapping(value = "/user/{user_id}", method = RequestMethod.PUT)
    public  @ResponseBody String putReport(
            @PathVariable("user_id") String userid,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request, 
            HttpServletResponse response) throws FileNotFoundException, IOException {
  	       
  	      addResponse(true, "<HTTP-PUT REQUEST>");
  	      //MANDATORY TO CALL THIS FUNCTION AT THE BEGINNING OF ANY REQUEST METHOD HANDLER
  		  populateMapsonStartUp();//Populate RefMaps from Redis 
  	        
  	        
  	       //If user exists then continue
           if(CheckifUserExists(userid)==true){
            
        	
        	//get the JSONMerge file from URI
	    	getFilePathFromURI(file,4);
	    	
	    	boolean isSyntaxValid = false;
	    	//STEP 1: Validate Json Put File Body SYNTAX
	    	ObjectMapper mapper = new ObjectMapper();
 	  	      try {
 	  	    	
 	  	    	JsonPUTFileContent =new String(Files.readAllBytes(Paths.get(JsonPUTFilePath)));
 	  			mapper.readTree(JsonPUTFileContent);
 	  			addResponse(true ,"JSON PAYLOAD SYNTAX IS VALID");
 	  			isSyntaxValid = true;//return true that json is parsed 
 	  			
 	  		} catch (IOException e) {
 	  			
 	  			 addResponse(false, "JSON PAYLOAD SYNTAX IS NOT VALID => " +e.getMessage().split("\n")[0]);
 	  			 isSyntaxValid = false;
 	  			
 	  		}
	    	
 	  	    //STEP 2: If JSONPUT Body is Syntax Error Free -->Check it Against Schema
	    	if(isSyntaxValid==true){
	    		
	    		     //Step 3: Validate the file Against Schema
	    			if(JsonAgainstSchema(JsonPUTFileContent)){
	    				
	    				//This Method will Push the new document into redis (same method as jacksonparse() used for Post Request)
	    				//This method also will handle deletion of old data 
	    				ProcessPUTRequest(userid);//Send old Main Key to check if it's equal the main key in the new doc -> so put will be allowed
	    			    
	    			}//If JsonAgainstSchema
	   	    		   
	    	  }//If Json Syntax is Valid
	    	
        }//EOF if user Exists/Found 		 
	    	 

  	  return displayResponseStatus();
  	  
    }
    
    
    
    //Flush Everything in Redis Request 
    @RequestMapping(value = "/flushAll", method = RequestMethod.POST)
    public String flushRedis() {
    	
    	boolean isRedisEmpty = false;
    	if(jedis.dbSize()==0){
			 addResponse(false,"No Data Exist(s) In Redis");
			 isRedisEmpty = true;
		 }
    	
    //If Redis Not Empty -->Then Flush All
    if(isRedisEmpty==false){
    		
    	
    	if(jedis.flushDB() != null){
    		addResponse(true, "REDIS DATA Flushed Successfully");
    	}
    	else{
    		addResponse(false, "Unable To Flush Redis");
    	}
    }
    
    
		return displayResponseStatus();    
    }
    
    
   //Flush Everything in Redis Request WITHOUT THE SCHEMA 
    @RequestMapping(value = "/flushAllKeepSchema", method = RequestMethod.POST)
    public String flushRedisBUTkeepSchema() {
    	
    	flushRedisValueNOTSchema();
    	addResponse(true,"All Data Flushed Except The Schema");
    	
		return displayResponseStatus();    
    }
  
    
 
    
 public Client getmyClient(){
 
	 if(node.isClosed()==true){
		 node = NodeBuilder.nodeBuilder().node();
	 }
	 
	      Client client = node.client();
  		  return client;
  		  
 }
    

 public void destroyResources(Client client){
	 client.close();
	 node.close();
	 
 }
 
 public synchronized void addRequest_toStack(String userid){
	RequestData.put(userid, JsonFileContent);//Add the id which numbers and the jsonfilecontent to index queue -> requestData called
	
	System.out.println("");
	System.out.println("***************DOCUMENT INDEXED STRUCTURE STARTS***************");
	
	System.out.println("USER: (user_"+userid+")");
	System.out.println("DATA: "+JsonFileContent);
	
	System.out.println("***************DOCUMENT INDEXED STRUCTURE ENDS***************");
	System.out.println("");
 }
 
 public synchronized void ProcessIndexStack(){
	 
	 //If request data hashmap is empty stop execution
	 if(RequestData.size()==0){
		 return;
	 }
	
	 Client client = getmyClient();
	
	 addResponse(true, "Indexing Starts");
	// putJsonDocument("http POST");
	 Iterator<Entry<String,String>> iterator = RequestData.entrySet().iterator();
		
	 while (iterator.hasNext()) {
			
			
			Map.Entry<String,String> entry = (Map.Entry<String,String>) iterator.next();
			String userid = entry.getKey(); 
			String jsonstring = entry.getValue();
			
			
	        	 IndexResponse getResponse = client.prepareIndex(ESIndex, ESType, userid.toString())
	        	 .setSource(putJsonDocument("POST",jedis.dbSize(),new Date())).execute().actionGet(500);
	        	
	        	 JsonAsMapforES.clear();
	        	 
	         if(getResponse!=null){
	        	 
	        	 addResponse(true, "Indexing Ends");
	         }
	         else{
	        	 addResponse(false, "Indexing Failed");
	        	 System.out.println("FAILED WENT TO CONTINUE");
	        	 continue;
	         }
	 
	 }
	 
	 destroyResources(client);
     //Remove info from Hashmap once it gets indexed
	 RequestData.clear();
         
       
	 }
 
 
 
 
public synchronized Map<String, Object> putJsonDocument(String requestType, long Tag, Date postDate){
JsonAsMapforES.put("LAST MODIFICATION HTTP-REQUEST", requestType);
JsonAsMapforES.put("UTAG", Tag);
JsonAsMapforES.put("Last Update", postDate);
return JsonAsMapforES;
}
 
 

//Method used to delete _INDEX not _ID
public boolean DeleteESIndex(){
    	
	boolean isDeleted = false;
	
    	try{
    	Client client = getmyClient();
    	client.admin().indices().delete(new DeleteIndexRequest(ESIndex)).actionGet(500);
    	destroyResources(client);
    	isDeleted = true;
    	}
    	catch(org.elasticsearch.indices.IndexMissingException e){
    	
    	isDeleted = false; // in case index doesn't exists at all to delete it return false
    	}
    	
		return isDeleted;
    	
    }
    
    //USER Get REQUEST
    @RequestMapping(value = "/search/{user_id}", method = RequestMethod.GET)
    public @ResponseBody synchronized String getUserFromElasticSearch(
            @PathVariable("user_id") String userid, 
            HttpServletRequest request, 
            HttpServletResponse response) {
       
    	
    	
    	//Adding request type to responseList
	   addResponse(true, "<HTTP-GET REQUEST>");
       //Get the digits only from user_id -> user_123 will become 123
	   //ES_UserId_Fromat = 123 NOT user_123
	   String ES_UserId_Format = userid.substring(userid.lastIndexOf("_") + 1);
	  
	   //First Check if user exists & get the result if exsits
	   checkIfuserExists(ES_UserId_Format);
		   
	  
       return displayResponseStatus();
    }
    
    
    
    
    //User when getuser from elastic search to see if user exists or not
    public synchronized boolean checkIfuserExists(String userid){
    	
    	boolean isUserExists = false;
    	
    	Client client = getmyClient();
    	try{
    		
    	 if ((ESIndex!= null) && (ESType != null) && (userid != null)) {
       
    		 if(ESHealthCheck()==true){
    			 // Check if a document exists
                 GetResponse getResponse = client.prepareGet(ESIndex, ESType, userid).setRefresh(true).execute().actionGet();
                 
                 
                 if(getResponse.isExists()==false){
                	 isUserExists = false;
                	 addResponse(true, "(user_"+userid+") Doesn't Exists in ES");
                	 return isUserExists;
                	 	
                 }
                 else{
                	 addResponse(true, "(user_"+userid+") Was Found in ES");
                	 addResponse(true, "Reading Data From ES for (user_"+userid+")");
                	 isUserExists = true;
                	 
                	 Map<String, Object> source = getResponse.getSource();
                	 System.out.println("-------------DATA FETCHED FROM ES STARTS-------------\n");
                	 System.out.println("-------------ES SYSTEM VARIABLES-------------");
                	 System.out.println("_index: " + getResponse.getIndex()+"\n");
                	 System.out.println("_type: " + getResponse.getType()+"\n");
                	 System.out.println("_id: " + getResponse.getId()+"\n");
                	 System.out.println("-------------DOCUMENT VARIABLES-------------");
                	 
                	 Iterator<Entry<String,Object>> iterator = source.entrySet().iterator();
             		
                	 while (iterator.hasNext()) {
                			
                			Map.Entry<String,Object> entry = (Map.Entry<String,Object>) iterator.next();
                			String fieldName = entry.getKey();
                			String fieldValue = entry.getValue().toString();
                			System.out.println(fieldName+": "+fieldValue+"\n");
                	 }
                	 
                	 
                	 System.out.println("-------------DATA FETCHED ENDS-------------");
                	 
                	 addResponse(true, "(user_"+userid+") Data Printed on Console");
                	 destroyResources(client);
                	 return isUserExists;
                 }
    		 }
    	 }
            
    	 else{
    		 // Check if index exists
             IndicesExistsResponse response = getmyClient().admin().indices().prepareExists(ESIndex).execute().actionGet();
             destroyResources(client);
             
             
             if(response.isExists()==false){
            	 isUserExists=false;
            	 addResponse(false, "(user_"+userid+") No _index Exists in ES");
            	 
             }
             else{
            	 isUserExists=true;
             }
    		 
    	 }
    	 
    	     	 
    	}catch (org.elasticsearch.indices.IndexMissingException e){
    		
    	addResponse(false, "Index Missing: Index Requested Doesn't Exists in ES");
      }
            
    	destroyResources(client);
    	 return isUserExists;   
    	 
    	
    }
  
    
 
  public synchronized boolean ESHealthCheck(){
	  Client client = getmyClient();
	  boolean status = false;
	  
	   ClusterHealthResponse healthResponse = client.admin().cluster().prepareHealth().setWaitForActiveShards(1)
              .execute().actionGet(1000);
	  
	  if (healthResponse.getStatus().name().equalsIgnoreCase("RED")) {
 		 try {
				 TimeUnit.SECONDS.sleep(2);
				 System.out.println("***************Node/Cluster Health Status: RED***************");
				 System.out.println("Paused...Retrying");
				 addResponse(true, "Paused.");
				 
				 //Retest after waiting 2 seconds
				 healthResponse = client.admin().cluster().prepareHealth().setWaitForActiveShards(1)
			     .execute().actionGet(1000);
				 
				 if (healthResponse.getStatus().name().equalsIgnoreCase("RED")){
					 
					 System.out.println("Paused...Retrying");
					 addResponse(true, "Paused.");
					 status = false;
				 }
				 else{
					 System.out.println("Connected Again");
					 addResponse(true, "Connected Again");
					 status = true;
				 }
				
					return status;
			
			} catch (InterruptedException e) {
				System.out.println("Service Interrupted");
				//e.printStackTrace();
			}
 		
 	 }
 	 else{
 		 System.out.println("***************Node/Cluster Health Status: YELLOW***************\n");
		 status = true;
		 return status;
 		
 	 }
 	
	  destroyResources(client);
	  return status;
	
  }
  
  
  
  
 //delete a _id from elastic search  
  public void deleteUserFromES(String userid){
	 Client client = getmyClient();
	  
  	//just the number after underscore (user_123) will become 123 only
	String ES_UserId_Format = userid;
	ES_UserId_Format = ES_UserId_Format.substring(ES_UserId_Format.lastIndexOf("_") + 1);
  	
  	 
      DeleteResponse response = client.prepareDelete(ESIndex,ESType,ES_UserId_Format).execute().actionGet();
      if(response!=null){
    	  addResponse(true, "(user_"+ES_UserId_Format+") Has Been Deleted From ES");
      }
      else{
    	  addResponse(false, "Failed to Delete (user_"+ES_UserId_Format+")");
      }
  
      destroyResources(client);
  }
  
 
  //PUT method in elastic search to update jsoncontent 
  public synchronized void updatePUTinES (String userid, String ReqType){
		     Client client = getmyClient();
		  
		  	//just the number after underscore (user_123) will become 123 only
			String ES_UserId_Format = userid;
			ES_UserId_Format = ES_UserId_Format.substring(ES_UserId_Format.lastIndexOf("_") + 1);
		  	
			//Deleting Previous Post
			 DeleteResponse response = client.prepareDelete(ESIndex,ESType,ES_UserId_Format).execute().actionGet();
		     //If delete happen index again
			 if(response!=null){
		    	  
				 RequestData.put(ES_UserId_Format, "notused");

				 Iterator<Entry<String,String>> iterator = RequestData.entrySet().iterator();
						
					 while (iterator.hasNext()) {
							
							
							Map.Entry<String,String> entry = (Map.Entry<String,String>) iterator.next();
							String useridx = entry.getKey(); 
							//String jsonstring = entry.getValue();
							
							
					        	 IndexResponse getResponse = client.prepareIndex(ESIndex, ESType, useridx.toString())
					        	 .setSource(putJsonDocument(ReqType,jedis.dbSize(),new Date())).execute().actionGet(500);
					        	
					        	 JsonAsMapforES.clear();
					        	 
					        if(getResponse!=null){
					        	addResponse(true,"Data Modified in ES");
					        }
					        else{
					        	addResponse(true,"Unable to Modify Data in ES");
					        }
					        
					 
					 }
					 
					 destroyResources(client);
				     //Remove info from Hashmap once it gets indexed
					 RequestData.clear();
				 
		      }
      }
  
 
  
  //SEARCH URL API FOR ELASTIC SEARCH /search/yoursearchkeyword
  @RequestMapping(value = "/search/{keyword}", method = RequestMethod.POST)
  public synchronized String searchkeyword(@PathVariable("keyword") String keyword) {
	  
	  addResponse(true,"Your Search Keyword Is: "+keyword);
	  searchES(keyword);
	  
	  
	  
	return displayResponseStatus();   
	  
	  
	  
	  
  }
  
  
  
  
  
  public void searchES(String keyword) {
	  
	  int resultcounter = 0;
	  
	  Client client = getmyClient();
	  
	 
	  SearchRequestBuilder searchRequestBuilder = new SearchRequestBuilder(client);
	  searchRequestBuilder.setIndices(ESIndex);
	  searchRequestBuilder.setTypes(ESType);
	  QueryStringQueryBuilder queryStringQueryBuilder = new QueryStringQueryBuilder(keyword);
	  //queryStringQueryBuilder.field("PerInfo_123010001");
	  searchRequestBuilder.setQuery(queryStringQueryBuilder);
	  SearchResponse response = searchRequestBuilder.execute().actionGet();
	  
	  //No result
	  if(response.toString().equals("null") || response.toString().isEmpty()){
		  
		  addResponse(false,"No Result Found");
		 
	  }
	  //Result Found
	  else{
		  //System.out.println(response.toString());
		  addResponse(true,"Your Search Result is Printed");
	 
	  
	  SearchHit[] results = response.getHits().getHits();
      System.out.println("\nCurrent results= " + results.length);
      addResponse(true,"Number of Results Found: "+results.length);
      
      
      
      
      for (SearchHit hit : results) {
          resultcounter++;
          
    	  System.out.println("-----------------ES RESULT NUMBER# "+resultcounter+" STARTS-----------------");
    	  System.out.println("_index: " + hit.getIndex()+"\n");
     	  System.out.println("_type: " + hit.getType()+"\n");
     	  System.out.println("_id: " + hit.getId()+"\n");
     	  
     	  
          Map<String,Object> result = hit.getSource();   


         Iterator<Entry<String,Object>> iterator = result.entrySet().iterator();
   		
     	 while (iterator.hasNext()) {
     			
     			Map.Entry<String,Object> entry = (Map.Entry<String,Object>) iterator.next();
     			String fieldName = entry.getKey();
     			String fieldValue = entry.getValue().toString();
     			
     			
     			if(fieldValue.contains(keyword)){
     				System.out.println("-----------------YOUR ACTUAL RESULT FOR KEY: ("+keyword+") IS FOUND HERE:-----------------\n");
     				System.out.println(fieldName+": "+fieldValue+"\n");
     				System.out.println("----------------------------------------------------------------------------------------\n");
     			}
     			else{
     				System.out.println(fieldName+": "+fieldValue+"\n");
     			}
     	 }
     	 
          
     	System.out.println("\n-----------------ES RESULT NUMBER# "+resultcounter+" ENDS-----------------\n");
          
      }
      
}//end of else
	  
	  destroyResources(client);
	  
	}
  

  
 
  
  
	  
/**************************** ********************* *****************************/	  
/**************************** FINAL PROJECT METHODS *****************************/	    
/**************************** FINAL PROJECT METHODS *****************************/	  
/**************************** ********************* *****************************/		  

/*** REVIEWD ***/	
	    //Validate Json File Syntax (Simple Json Lib)
	         private boolean JsonSyntaxValidation(){
	   	  	 
	   	  	      ObjectMapper mapper = new ObjectMapper();
	   	  	      try {
	   	  	    	
	   	  	    	JsonFileContent = new String(Files.readAllBytes(Paths.get(JsonFilePath)));
	   	  			mapper.readTree(JsonFileContent);
	   	  			addResponse(true ,"JSON PAYLOAD SYNTAX IS VALID");
	   	  			return true;//return true that json is parsed 
	   	  			
	   	  		} catch (IOException e) {
	   	  			
	   	  			 addResponse(false, "JSON PAYLOAD SYNTAX IS NOT VALID => " +e.getMessage().split("\n")[0]);
	   	  			 return false;
	   	  		}
	   	  	      
}
	   	 
/*** REVIEWD ***/	
	  //Validate Json File Against Json SCHEMA
	   private boolean JsonAgainstSchema(String jsonString){
	 		
	 	   try {
	 	 
	 		//Reading Json Schema From Redis Not from File
	 		String schemafilefromRedis = jedis.get("Schema");
	        final JsonNode schemafromRedis=JsonLoader.fromString(schemafilefromRedis);
	         
	         
	 		if (ValidationUtils.isJsonValid(schemafromRedis.toString(), jsonString)){
	 		    	//System.out.println("Valid!");
	 		    	addResponse(true,"VALID: JSON PAYLOAD MATCH JSON SCHEMA");
	 		    	return true;
	 		    }else{
	 		    	//System.out.println("NOT valid!");
	 		    	addResponse(false,"INVALID: JSON PAYLOAD DOES NOT MATCH JSON SCHEMA");
	 		    	return false;
	 		    }
	 		
	 	} 
	 	   catch (ProcessingException e) {
	 		addResponse(false,"Parssing Exception: JSON PAYLOAD DOES NOT MATCH JSON SCHEMA => "+e.toString());
	 	}
	 	  catch (IOException e){
	 		addResponse(false,"IO Exception: JSON PAYLOAD DOES NOT MATCH JSON SCHEMA => "+e.toString());
	 	  }
	 	  return false;
}
	 
/******REVIEWD *****/	    
	   //Parse Json File & Store it into REDIS (USED IN POST REQUEST) when user post a json documents to the api
	   private void JacksonParser() throws IOException {  
	 	 
	 	  		   duplicateEntry=false; //set default value as NO duplicate (mainkey or subkeys) once the request is made
	 	           populateMapsonStartUp(); //Populate RefMaps from redis
	 	  		   
	 	  		       //Calling to Get the Doc Main Key (user_123)
	        	       String MainKey =  getBasekeyFromFile(JsonFilePath, JsonFileContent);

	        	       //Stop Exection if Main Key is not generated form the Json Doc
	        	       if(MainKey.length()<1){
	        	    	   return;
	        	       }
	        	       
	        	        //Check if Main Key Already Exists if no continue into try catch block
	                 if(RefMap.containsKey(MainKey)){
	 		        addResponse(false, "USER: ("+MainKey+") Already Exists =>POST COULDNOT BE PERFORMED");
	 		        duplicateEntry=true;
	 		        return;
	 		        }
	                 
	                 //else if main key is not duplicate
	                 else{ 
	               
	 		try {
	 			 //Hold sub keys (address_789)
	 			 ArrayList<String> SubKeys = new ArrayList<String>();
	 			
	 			 JSONParser parser = new JSONParser();
	 			 Object obj = parser.parse(JsonFileContent);
	 			 JSONObject jsonObject = (JSONObject) obj;

	 			 JsonFormatString = jsonObject.toJSONString();
	 			 
	 			 String payload=""; //Actual Payload 
	 			 String actualKey=""; //_type+_id
	 			 String formatedKey="";//"Vehicle_Info": 
	 			 
	 			 //first for loop to KEEP checking for duplicate subkeys 
	 			 for (Object key : jsonObject.keySet()) {
	 			        //Hold key
	 			        String keyStr = (String)key;
	 			        Object keyvalue = jsonObject.get(keyStr); //Hold Value
	                     
	                     
	                     //The actual field data is called keyvalue 
	                     payload = keyvalue.toString();
	                     
	                     //Getting the key(_type+_id= actualKEY) from the String
	 		        	actualKey = GetkeyfromString(payload);
	 		          	
	                     //Check if subkey is found in redis ->if yes stop execution to avoid overwriten
	 		        	if(jedis.exists(actualKey)){
	 		        		addResponse(false,"SubKey:("+actualKey+")Exists for ("+getKeyfromSubKey(actualKey)+")");
	 		        		duplicateEntry=true;
	 		        	}
	 			 }
	 			 
	 			 //then if no even one subkey is duplicate we continue to set their values in the maps
	 			 if(duplicateEntry==false){
	 			 
	 			 for (Object key : jsonObject.keySet()) {
	 				    //Hold key
	 			        String keyStr = (String)key;
	 			        Object keyvalue = jsonObject.get(keyStr); //Hold Value
	                  
	                     //The actual field data is called keyvalue 
	                     payload = keyvalue.toString();
	                  
	                     //Getting the key(_type+_id= actualKEY) from the String
	 		        	 actualKey = GetkeyfromString(payload);
	 		        	
	 		        		
	 		        		//if JSONObject
	 				        if (keyvalue instanceof JSONObject){
	 				        	
	 	                        formatedKey = "\""+keyStr+"\":"; //"Vehicle_Info": 
	 	                        
	 				        	//if subkey is not found in redis -> new json document ->save it
	 				        	
	 	                        String payloadWithKey = formatedKey+""+payload;
	 				        			//Store Data into Redis
	 						        	 setDatatoRedis(actualKey,payloadWithKey);
	 						        	 
	 						        	 //System.out.println("STORED DATA:"+GetDataFromRedis(actualKey));
	 			                         SubKeys.add(actualKey);
	 			                       
	 				        		 System.out.println("***OBJECT***");
	 					        	 //print key, value and field
	 		                    	 System.out.println("KEY: "+ actualKey);
	 					        	 System.out.println("VALUE: " + payloadWithKey);
	 					        	 System.out.println("PATH FROM ROOT: "+ MainKey+"/"+actualKey);
	 					        	 
	 					        	 JsonAsMapforES.put(actualKey, payloadWithKey);
	 					        	
	 				        }
	 				        
	 				       //if JSONArray
	 				       else if (keyvalue instanceof JSONArray){
	 				        	 
	 				        
	 				    	        formatedKey = "\""+keyStr+"\":"; //"Vehicle_Info": 
	 					        	//System.out.println(formatedKey);
	 					        	String payloadWithKey = formatedKey+""+payload;
	 					        	
	 				        	    //Store Data into Redis
	 						        setDatatoRedis(actualKey,payloadWithKey);
	 						        	 
	 						         //System.out.println("STORED DATA:"+GetDataFromRedis(actualKey));
	 			                     SubKeys.add(actualKey);
	 			                       
	 					        	 System.out.println("***ARRAY***");
	 					        	 //print key, value and field
	 		                    	 System.out.println("KEY: "+ actualKey);
	 					        	 System.out.println("VALUE: " + payloadWithKey);
	 					        	 System.out.println("PATH FROM ROOT: "+  MainKey+"/"+actualKey);
	 					        	 JsonAsMapforES.put(actualKey, payloadWithKey);
	 					        	
	 				        }
	 		        		
	 		        		
	 		        	}
	 		
	 			    System.out.println("****MAIN KEY: "+MainKey);
	 			 	System.out.println("****SUB KEYS: "+SubKeys.toString().replace(", ", ","));
	 			 	
	 			 	//Call this method to add the Mainkey and its subkeys arraylist to jedis where relationships between nodes are saved
	 			 	synchronizeMapswithRedis(MainKey, SubKeys.toString().replaceAll("\\[", "").replaceAll("\\]","").replace(", ", ","));
	 			 	
	 			 	addResponse(true, "JSON File ("+JsonFileName+") Stored Into Redis");
	 			 	
	 			 	//just the number after underscore (user_123) will become 123 only
	 			 	String ES_UserId_Format = MainKey;
	 			 	ES_UserId_Format = ES_UserId_Format.substring(ES_UserId_Format.lastIndexOf("_") + 1);
	 			 
	 			 	//Calling Indexer Method to index that user
	 			 	//ESIndexerCreator(ES_UserId_Format);
	 			 	//putJsonDocument("ss");
	 			 	addRequest_toStack(ES_UserId_Format);
	 				
	 			 	
	 			 }
	 			    
	 			 
	 			 
	 		} catch (ParseException e) {
	 			
	 			e.printStackTrace();
	 		}
	 		
	  }
	   
	   
	   }
	   
	   
	   
/******REVIEWD *****/
	 //Get Document Main Key or Base Key (user_123)
	   private String getBasekeyFromFile(String filePath, String JsonStringContent){
	 	  
	 	     //Hold doc main key (ex: person_123) as an array person at index 0 & 123 at index 1
	 		 ArrayList<String> BaseKey = new ArrayList<String>();
	 		 String MainKey="";//Hold the doc main key (user_123) as one string
	 		 String payload=""; //Actual Payload 
	 		 try {
	 	     
	 	     JSONParser parser = new JSONParser();
	 		 Object obj = parser.parse(JsonStringContent);
	 		 JSONObject jsonObject = (JSONObject) obj;
	 	 	 //read the basekey from the file
	          for (Object key : jsonObject.keySet()) {
	 		        //Hold key
	 		        String keyStr = (String)key;
	 		        //Hold the value
	 		        Object keyvalue = jsonObject.get(keyStr); //Hold Value
	 		        //if keyvalue is not an object nor an array then it's the Main key at the beginning of the doc
	 		        if(!(keyvalue instanceof JSONObject)&& !(keyvalue instanceof JSONArray)){
	 		        	BaseKey.add(keyvalue.toString());//add the keyvalue to basekey array
	 		        }
	           }
	        
	  		//Check if size is 2 that's mean we got _type value & _id value which is user_123
	          if(BaseKey.size()==2){
	        	  //Format the Basekey from baskey array at index 0 -> type & index 1 ->id --= EXAMPLE(Person_123)
	  	  		 MainKey = BaseKey.get(0)+"_"+BaseKey.get(1);   
	         	 addResponse(true,"Reading Doc Main Key: ("+MainKey+")");
	          }
	          else{
	         	 addResponse(false,"Reading Doc Main Key Failed");
	         	 MainKey=""; //set it to empty so it will fail the check inside jacksonParser Method and stop exectuion
	          }
	 	  } 
	 	  catch (ParseException e) {
	 		//e.printStackTrace();
	 		addResponse(false, "Reading Doc Main Key ERROR => "+e.toString());
	 	}
	     
	 	return MainKey;          
}	   
	   
/******REVIEWD *****/   
//SUBKEY Method that Extract _type Value & _id Value from payload String (keyvalue or SUBKEY)
	    private String GetkeyfromString(String type){ 
	    	
	    	String keypartA=""; //hold the _type value
	    	String keypartB=""; //hold the _id value
	    	String entireKey="";//hold keypartA+keyPartB;
	    	
	    	//Getting _type & _id value from the string called type which hold keyvalue
	    	String[] details = type.split(",");
	        for (int x=0;x<details.length;x++)
	        {
	            String[] d = details[x].split(":");
	            if(d.length == 2)
	            {
	                String keyx = d[0];
	                String val = d[1];
	                if(keyx.equals("\"_type\""))
	                {
	                 keypartA=val;
	                
	                }
	                if(keyx.equals("\"_id\"")){
	               	 
	               	 keypartB=val;
	              
	                }
	                
	            }
	            
	        }
	       
	        //.replaceAll("\"", "") to remove double quotes ""
	        //.replaceAll("[{}]", " ") to remove any curly braces {}
	        entireKey = keypartA.replaceAll("\"", "").replaceAll("[{}]", "")+"_"+keypartB.replaceAll("\"", "").replaceAll("[{}]", "");
	        
	        return entireKey;
	    }
	   
	    
	    
/******REVIEWD *****/      
//Find the MainKey from a Subkey (when you have a subkey and you want to which MAINKEY this particular subkey belongs)
	    private String getKeyfromSubKey (String subkey){
	     String mainkeyfound="";
	  	  
	  	  for (Entry<String, ArrayList> entry : RefMap.entrySet()) {
	            String key = entry.getKey();
	            ArrayList values = entry.getValue();
	            if(values.contains(subkey)){
	          	  mainkeyfound = key;
	          	 
	            }
	           
	        }
	  	  
	  	  return  mainkeyfound;
	            
	    }
	  	
	    

	    
/******REVIEWD *****/ 	    
//Used to process the MERGE/PATCH request
//parameters: userid = MainKey
//			  subkey = property subkey (VehInfo_12303)
//			  originalmerge = body of the merge document in plain format found in (JsonMergeFileContent)
//            MergedJson = body of the merge document in JSON Format (compact format) no spaces, and tabs
private void processPatchRequest(String userid, String subkey, String originalmerge, String MergedJson){
			 PopulateOldJsonPayloadArrayListBasedonMainKey(userid);
			
			 //subkey --> is the subkey of the merged Json body
			 //Merged Json --> is the body itself in merge document
			 //userid --> is Mainkey of that subkey
			 
			System.out.println("\nMERGE JSON BODY:\n----------------\n" +MergedJson+"\n");
			  
			 /*** STEP 1: Syntax Validation of The JsonMergeFileContent = MergedJson ***/
		  	   try {
		  	    	ObjectMapper mapper = new ObjectMapper();
		  			mapper.readTree(originalmerge);
		  			addResponse(true ,"MERGE Body Payload Syntax Is Valid");
		  		} catch (IOException e) {
		  			addResponse(false, "MERGE Body Payload Syntax Is Not Valid => " +e.getMessage().split("\n")[0]); 
		  			return;
		  		}
			 
		  	   
		  	 /*** STEP 2: Check if subkey recieved has json value in redis ***/  
	         //Make Sure That Subkey Format is Correct
		  	 if(GetDataFromRedis(subkey)==null){
	         addResponse(false, "Reading MERGE Payload Sub Key Failed (SUBKEY DOESN'T EXISTS): "+subkey);
	   	     return;//Stop exection
	         }
		  	 //else subkey is valid 
		  	 else{
		  	 addResponse(true, "Reading MERGE Payload Sub Key: "+subkey); 
		  	 }
		  	 
		  	 //Get the json part of that subkey from redis
			 String oldpayload =","+GetDataFromRedis(subkey).toString().replaceAll("[=]",":");
			
			 
			 
			 /*** STEP 3: Locate and Replace the new MergedJson inside the old json document ***/  
			 boolean isMergeDone = true;//by default it's true unless and error happened an set it to false
			 
			 String oldJsonPayloadSTRING = "";//Converting oldJsonPayLoad Arraylist to a String called oldJsonPayloadSTRING

			 //Pushinig OldJsonPayLoad arraylist to String oldJsonPayloadSTRING (for easier validation against Schema)
			 for (String s : OldJsonPayload)
			 {
				 //String withnoComma = s.substring(1);
				 oldJsonPayloadSTRING += s;
			 }

			 //Remove The Comma at the beginning of the entire jsonpayload String
			 oldJsonPayloadSTRING = oldJsonPayloadSTRING.substring(1);
			 //Add Curly brackets for syntax compliance: to the beginning and the end
			 oldJsonPayloadSTRING = "{"+oldJsonPayloadSTRING+"}";
			 
			 //print the old json payload (for display only)
			 System.out.println("OLD JSON PAYLOAD STRING:\n------------------------\n"+oldJsonPayloadSTRING+"\n");
			 System.out.println("");//newline
			 
			 //Remove Curly Brackets from the beginning and the end of the new payload to add it to old payload
		  	 MergedJson = MergedJson.substring(1);
			 MergedJson = MergedJson.substring(0, MergedJson.length()-1);
			
			 
			 //locate the index where the mergedjson property is found in the oldjson document so we can replace it
			 int indexChanged = OldJsonPayload.indexOf(oldpayload);
			 
			 //this case will never happen because oldpayload is correctly formatted to match one of oldJsonPayLoad Arraylist elements
			 if(indexChanged<0){//no index contain the oldpayload in OldJsonPayLoad arraylist
				 addResponse(false, "Subkey Provided Doesn't Belong to That User. Subkey Already Exists for Another User: Could Not Locate The Merge (Location = INDEX) in Original Json Doc");
				 isMergeDone = false;
				 return;
			 }
			 
			 OldJsonPayload.set(indexChanged,","+MergedJson.toString());//Insert the new part insetad of the old one
			 
			 //Adding the oldpayload after aletring the changed part to the newpayloa
			 for(int i=0; i<OldJsonPayload.size(); i++){
			 NewJsonPayload.add(OldJsonPayload.get(i));
		     }
			 
			 //This case will never happen again
			 //because these 2 arrays MUST Match due to the code written above that will ensure consistency
			 if(NewJsonPayload.size() != OldJsonPayload.size()){
				 addResponse(false, "Could Not Match New Merged Json With Original Json Document");
				 isMergeDone=false;
				 return;
			 }
			 
			 
			 //pushing the newJsonPayLoad arraylist to NewJsonPayLoadSTRING
			 String newJsonPayloadSTRING = "";

			 //Pushinig OldJsonPayLoad arraylist to String oldJsonPayloadSTRING (for easier validation against Schema)
			 for (String s : NewJsonPayload)
			 {
				 //String withnoComma = s.substring(1);
				 newJsonPayloadSTRING += s;
			 }

			 //Remove The Comma at the beginning of the entire jsonpayload String
			 newJsonPayloadSTRING = newJsonPayloadSTRING.substring(1);
			 //Add Curly brackets for syntax compliance: to the beginning and the end
			 newJsonPayloadSTRING = "{"+newJsonPayloadSTRING+"}";
			 
			 System.out.println("NEW JSON PAYLOAD STRING:\n------------------------\n"+newJsonPayloadSTRING+"\n");
			 
			 if(isMergeDone==false){
				 addResponse(false, "UNABLE to Couple Merged Body With Parent Json Document");
				 return;
			 }
			 else{
				 addResponse(true,"Coupling Merged Body With Parent Json Document");
			 }
			 
			 
			 
			 
			 /*** STEP 4: Validate the newpart merged with the old entire doc together as one document against the schema***/  
			 //get schema from redis
			 String schemafilefromRedis = jedis.get("Schema");
			 boolean ismergeSchemaValid = false;
			//VALIDATE NEWPARYLOADSTRING AGAINST SCHEMA
			try {
				if(ValidationUtils.isJsonValid(schemafilefromRedis, newJsonPayloadSTRING)){
					addResponse(true,"VALID: JSON MERGE PAYLOAD MATCH JSON SCHEMA");
					ismergeSchemaValid = true;
				 }
				
				else{
					addResponse(false,"INVALID: JSON MERGE PAYLOAD DOES NOT MATCH SCHEMA");
					return;
				}
			} catch (ProcessingException e) {
				addResponse(false,"Parssing Exception: JSON MERGE PAYLOAD DOES NOT MATCH SCHEMA => "+e.toString());
				return;
			} catch (IOException e) {
				addResponse(false,"IO Exception: JSON MERGE PAYLOAD DOES NOT MATCH SCHEMA => "+e.toString());
				return;
			}
			
			
			
			
			   /*** STEP 5: Store the merged part into redis***/  
				if(ismergeSchemaValid==true){//check if newpayload match with the schema
				
					//Check if delete the entry os subky in redis with its value fail for any reason
					if(jedis.del(subkey)==null){
						addResponse(false, "Unable to Modify Data Into Redis");
						return;
						}
					
					else{
					
						jedis.del(subkey);//Remove old data for that key from redis
						setDatatoRedis(subkey, MergedJson);
						addResponse(true, "Data Modified Into Redis");
						
						ParseJsonDocAgainForMergeAndPutToBeESFormatCompatible(userid,newJsonPayloadSTRING);
						updatePUTinES(userid,"MERGE");//Update -id in ES
				     }
				
				}
				
			
			 
		 }
		  
	    
/****** REVIEWD ******/
/** SAME METHOD AS JACKSONPARSER() but filename & path are different for Put File instead of POST File 
 * EVERYTING ELSE IS 100% THE SAME AS Method called JacksonParse() found above ***/	    
//Parse Json File & Store it into REDIS (USED IN POST REQUEST) when user post a json documents to the api
private void ProcessPUTRequest(String OldMainKeyToBeDeleted) throws IOException {  
	 
	  		   duplicateEntry=false; //set default value as NO duplicate (mainkey or subkeys) once the request is made
	           populateMapsonStartUp(); //Populate RefMaps from redis
	  		   
	  		   //Calling to Get the Doc Main Key (user_123)
     	       String MainKey =  getBasekeyFromFile(JsonPUTFilePath, JsonPUTFileContent);

     	       /**Check if NewMainKey Doesn't equal to OldMainKey Stop Execution
     	        * BYLAW A PUT REQUEST SHOULD HAVE THE SAME DOCUMENT MAIN KEY AS THE ONE
     	        * ITS REPLACING AND NOT ANOTHER MAINKEY AND SUBKEYS **/
     	        if(MainKey.equalsIgnoreCase(OldMainKeyToBeDeleted)){
     	        //Delete The OldMainKey & OldSubkeys Related to it
				if(RefMap.containsKey(OldMainKeyToBeDeleted)){
			     	 
			     	  //Delete The MainKey From Redis
			     	  jedis.del(OldMainKeyToBeDeleted);
			     	  
			     	  //Loop through the RefMap MainKey index inside the arraylist 
			     	  //ex user_123 (mainkey) --> [Pinfo_12301, AInfo_12304,...] as subkeys stored in the arraylist
			     	  //that is stored in the hashmap with the mainkey
			     	 for (Map.Entry<String, ArrayList> entry : RefMap.entrySet()) {
					    
					    if(entry.getKey().equalsIgnoreCase(OldMainKeyToBeDeleted)){
					    ArrayList<String> value = entry.getValue();
					    for(String aSubKey : value){
					        
					    	//Delete Subkey and it's value field from redis
					    	jedis.del(aSubKey);
					    	
					       }
					    }
					  }
			     	addResponse(true, "Deleting Exsiting Data For Main Key ("+OldMainKeyToBeDeleted+")");	
			     	populateMapsonStartUp(); //REPopulate RefMaps after delete from redis 
				}
     	       
				else{//If delete doesn't happen -> this case will never happen
					addResponse(false, "Failed to Delete Exsiting Data For Main Key ("+OldMainKeyToBeDeleted+")");	
					return;
				}
     	       }
     	        //So if new put doc has different main key that the old one 
     	        //STOP EXECTION NO DELETE WIL HAPPEN AND NO ADDITION OF THE NEW DOC WILL HAPPEN
     	        else{
     	         addResponse(false, "Not Allowed To Change Doc Main Key on Put Request");
     	         return;
     	        }
     	       
     	       
     	       
     	       
     	       //Stop Exection if Main Key is not generated form the Json Doc
     	       if(MainKey.length()<1){
     	    	   return;
     	       }
     	       
     	        //Check if Main Key Already Exists if no continue into try catch block
              if(RefMap.containsKey(MainKey)){
		        addResponse(false, "USER: ("+MainKey+") Already Exists =>POST COULDNOT BE PERFORMED");
		        duplicateEntry=true;
		        return;
		        }
              
              //else if main key is not duplicate
              else{ 
            
		try {
			 //Hold sub keys (address_789)
			 ArrayList<String> SubKeys = new ArrayList<String>();
			
			 JSONParser parser = new JSONParser();
			 Object obj = parser.parse(JsonPUTFileContent);
			 JSONObject jsonObject = (JSONObject) obj;
			 
			 String payload=""; //Actual Payload 
			 String actualKey=""; //_type+_id
			 String formatedKey="";//"Vehicle_Info": 
			 
			 //first for loop to KEEP checking for duplicate subkeys 
			 for (Object key : jsonObject.keySet()) {
			        //Hold key
			        String keyStr = (String)key;
			        Object keyvalue = jsonObject.get(keyStr); //Hold Value
                  
                  
                  //The actual field data is called keyvalue 
                  payload = keyvalue.toString();
                  
                  //Getting the key(_type+_id= actualKEY) from the String
		        	actualKey = GetkeyfromString(payload);
		          	
                  //Check if subkey is found in redis ->if yes stop execution to avoid overwriten
		        	if(jedis.exists(actualKey)){
		        		addResponse(false,"SubKey:("+actualKey+") Exists for ("+getKeyfromSubKey(actualKey)+")");
		        		duplicateEntry=true;
		        	}
			 }
			 
			 //then if no even one subkey is duplicate we continue to set their values in the maps
			 if(duplicateEntry==false){
			 
			 for (Object key : jsonObject.keySet()) {
				    //Hold key
			        String keyStr = (String)key;
			        Object keyvalue = jsonObject.get(keyStr); //Hold Value
               
                  //The actual field data is called keyvalue 
                  payload = keyvalue.toString();
               
                  //Getting the key(_type+_id= actualKEY) from the String
		        	 actualKey = GetkeyfromString(payload);
		        	
		        		
		        		//if JSONObject
				        if (keyvalue instanceof JSONObject){
				        	
	                        formatedKey = "\""+keyStr+"\":"; //"Vehicle_Info": 
	                        
				        	//if subkey is not found in redis -> new json document ->save it
				        	
	                        String payloadWithKey = formatedKey+""+payload;
				        			//Store Data into Redis
						        	 setDatatoRedis(actualKey,payloadWithKey);
						        	 
						        	 //System.out.println("STORED DATA:"+GetDataFromRedis(actualKey));
			                         SubKeys.add(actualKey);
			                       
				        		 System.out.println("***OBJECT***");
					        	 //print key, value and field
		                    	 System.out.println("KEY: "+ actualKey);
					        	 System.out.println("VALUE: " + payloadWithKey);
					        	 System.out.println("PATH FROM ROOT: "+ MainKey+"/"+actualKey);
					        	 JsonAsMapforES.put(actualKey, payloadWithKey);
				        }
				        
				       //if JSONArray
				       else if (keyvalue instanceof JSONArray){
				        	 
				        
				    	        formatedKey = "\""+keyStr+"\":"; //"Vehicle_Info": 
					        	//System.out.println(formatedKey);
					        	String payloadWithKey = formatedKey+""+payload;
					        	
				        	    //Store Data into Redis
						        setDatatoRedis(actualKey,payloadWithKey);
						        	 
						         //System.out.println("STORED DATA:"+GetDataFromRedis(actualKey));
			                     SubKeys.add(actualKey);
			                       
					        	 System.out.println("***ARRAY***");
					        	 //print key, value and field
		                    	 System.out.println("KEY: "+ actualKey);
					        	 System.out.println("VALUE: " + payloadWithKey);
					        	 System.out.println("PATH FROM ROOT: "+  MainKey+"/"+actualKey);
					        	 JsonAsMapforES.put(actualKey, payloadWithKey);
				        }
		        		
		        		
		        	}
		
			    System.out.println("****MAIN KEY: "+MainKey);
			 	System.out.println("****SUB KEYS: "+SubKeys.toString().replace(", ", ","));
			 	
			 	//Call this method to add the Mainkey and its subkeys arraylist to jedis where relationships between nodes are saved
			 	synchronizeMapswithRedis(MainKey, SubKeys.toString().replaceAll("\\[", "").replaceAll("\\]","").replace(", ", ","));
			 	
			 	addResponse(true, "JSON PUT File ("+JsonPUTFileName+") Stored Into Redis");
			 	
			 	ParseJsonDocAgainForMergeAndPutToBeESFormatCompatible(MainKey,JsonPUTFileContent);
				updatePUTinES(MainKey,"PUT");//Update -id in ES
			 	
			 	
			 	
			 }
			    
			 
			 
		} catch (ParseException e) {
			addResponse(false, "Parsing Error Line 1156");
			//e.printStackTrace();
		}
		
}


}



	    
	    
	    
/******REVIEWD *****/  	    
//Used to get the entire json document saved in redis based on MainKey only
//result will be saved in arraylist called oldjsonPayLoad
//in the format of Subkey -> Value in each index of the arraylist
private void PopulateOldJsonPayloadArrayListBasedonMainKey(String userid){

	        //Clear both arrays to avoid duplicate data when doing 2nd merge request
	    	OldJsonPayload.clear();
	    	NewJsonPayload.clear();
	    	
	    	
	      	 for (Map.Entry<String, ArrayList> entry : RefMap.entrySet()) {
	 		    
	 		    String Mainkey = entry.getKey();//MainKey Value (user_123)
	 		    if(Mainkey.equals(userid)){ //If Mainkey Found in hashmap equal passed userid 
	 		    ArrayList<String> value = entry.getValue(); //then loop inside the arraylist and find all subkeys related with that mainkey
	 		    for(String aSubKey : value){
	 		    	 
	 		    	//Add COMMA, before propety value to make syntax free error when validating against SCHEMA
	 		        String payload = ","+GetDataFromRedis(aSubKey).toString().replaceAll("[=]",":");
	 		        OldJsonPayload.add(payload); //Add Json Extracted into Arraylist
	 		        
	 		     }
	 		  }
	       }     
}
	 	    
	    


	    
	    
/******REVIEWD *****/    	    
//get Data from Redis Based on userid which MAINKEY Sent in GET REQUEST 
private void ProcessGetRequest(String userid){
	    	
	    	 
	    	populateMapsonStartUp();//Populate RefMaps from REDIS
	    	 
	    	 
	    	//if user_id found in the refMap for any MainKey (where RefMap is populated from redis)
	    	 if(RefMap.containsKey(userid)){
	    		 
	      	  addResponse(true,"REQUESTED USER: ("+userid+") FOUND");
	      	  
	      	
	      	  //Loop through the RefMap MainKey index inside the arraylist 
	      	  //ex user_123 (mainkey) --> [Pinfo_12301, AInfo_12304,...] as subkeys stored in the arraylist
	      	  //that is stored in the hashmap with the mainkey
	      	 for (Map.Entry<String, ArrayList> entry : RefMap.entrySet()) {
	 		    
	 		    String Mainkey = entry.getKey();//MainKey Value (user_123)
	 		    if(Mainkey.equals(userid)){ //If Mainkey Found in hashmap equal passed userid 
	 		    ArrayList<String> value = entry.getValue(); //then loop inside the arraylist and find all subkeys related with that mainkey
	 		    for(String aSubKey : value){
	 		    	 
	 		        //System.out.println("key : " + Mainkey + " value : " + aSubKey);
	 		    	System.out.println("");
	 		        String payload = GetDataFromRedis(aSubKey).toString().replaceAll("[=]",":");
	 		        System.out.println("SUBKEY: "+aSubKey);
	 		        System.out.println("VALUE: "+payload);
	 		        OldJsonPayload.add(payload); //Add Json Extracted into Arraylist
	 		        
	 		    }
	 		  addResponse(true,"READING DATA FROM REDIS");
	       	  addResponse(true,"DATA BELONGS TO ("+userid+") WAS PRINTED IN CONSOLE");
	 		}
	      	 }
	         }
	    	 //if user_id no found in the refMap for any MainKey
	         else{
	           addResponse(false,"REQUESTED USER: ("+userid+") DOES NOT EXISTS IN REDIS");
	          
	         }
	    	
	    	
	    }
	    
	    
	    
	    
	    
/******REVIEWD *****/    	   
//Method to Store MainKey & All its Subkeys arraylist into Redis
//AND populate RefMap Hashmap after that with MainKey and SubKeys arraylist that was stored into redis
public void synchronizeMapswithRedis(String mainKey, String subKey){

	 //Store the MainKey and all the subkeys (Subkey.toString)
 	 jedis.set(mainKey,subKey);
 	
 	 //Getting the string belonging to this key from redis 
 	 String subKeyString = jedis.get(mainKey);
 	 
 	 //declare an arraylist of strings to hold the value gotten from redis
 	 ArrayList<String> SubKeysArraylist = new ArrayList<String>();
 	 
 	 //transfer the string and insert it into the arraylist
 	 SubKeysArraylist = new ArrayList<String>(Arrays.asList(subKeyString.split(",")));
 	 
 	 //Displaying response
 	 addResponse(true, "Reading Doc Sub Keys: "+SubKeysArraylist.toString());
 	 
 	 //Add the the RefMap
 	 RefMap.put(mainKey, SubKeysArraylist);
 	 
}



/******REVIEWD *****/    
//Method use to populate RefMap with its value from redis when redis is ON but SPRING BOOT was OFF 
//and turned on later and redis already has keys and subkeys stored
public void populateMapsonStartUp(){
	
	//Clear RefMap from previous data before RE-Populate it
	RefMap.clear();
	
	Set<String> mainkeyspattern = jedis.keys("user_"+"*"); //get any key in redis that starts with user_*
 	for (String key : mainkeyspattern) {
 		
 		 //Getting the string belonging to this key from redis 
 	 	 String subKeyString = jedis.get(key);
 	 	 
 	 	//declare an arraylist of strings to hold the value gotten from redis
 	 	 ArrayList<String> SubKeysArraylist = new ArrayList<String>();
 	 	 
 	 	 //transfer the string and insert it into the arraylist
 	 	 SubKeysArraylist = new ArrayList<String>(Arrays.asList(subKeyString.split(",")));
 		 
 	 	 //Add Results to refMap
 	 	 RefMap.put(key, SubKeysArraylist);
 	 
 	} 
 	
 	 
}
	

/******REVIEWD *****/    
//Method to Display on CMD and Console the elements inside responseList
public String displayResponseStatus(){
	
	StringBuffer SingleResponse = new StringBuffer();
	for (int i=0; i<responseLists.size(); i++) {
	//Adding Operation Number which arrayindex + 1 after # sign in the response string saved in the arraylist
    String addOpNumb = responseLists.get(i).toString().replaceAll("#", "#"+(i+1)+" ");
    //Now append each element of the array on a newline (for display purposes only)
	SingleResponse.append(addOpNumb.toString()).append('\n');
	}
	
	//Clear the Arraylist responseList after saving its content to SingleResponse String Builders
	//To avoid duplicate entry in arraylist
	responseLists.clear();
	
	//Print out the result in Spring Boot
	System.out.println(SingleResponse.toString());
	
	//Return Result to method that called displayResponseStatus() to display it in CMD
	return SingleResponse.toString();

}


/******REVIEWD *****/    
 //Get System Time in HH:mm:ss Format
	public String getSystemTime(){
	Calendar cal = Calendar.getInstance();
	SimpleDateFormat sdf = new SimpleDateFormat("mm:ss.SSS");
	String currentTime = sdf.format(cal.getTime());
	return currentTime;
	}
	
	
	
/******REVIEWD *****/    	
//Add to Response to Arraylist ResponseList
	public void addResponse(boolean status,String response){
		
		//Status (true = sucess & false = failed)
		String success = "<SUCCESS>:";
		String failed = "<FAILED>:";
		String formatedResponse = "";
		
		//Means Operation Success (true)
		if(status == true){
			//If this is the first response add /n NEWLINE to the beginning just for formating display
			if(responseLists.size()==0){
				//Formated the response string with system type and TRUE status + response string
				formatedResponse = "\n"+getSystemTime()+"| OP#"+success+" "+response+".";
				//Adding the formated response string to arraylist
				responseLists.add(formatedResponse);
			}
			else{
			//Formated the response string with system type and TRUE status + response string
			formatedResponse = getSystemTime()+"| OP#"+success+" "+response+".";
			//Adding the formated response string to arraylist
			responseLists.add(formatedResponse);
			}
		}
		
		//Means Operation Failed (false)
		else{
			//If this is the first response add /n NEWLINE to the beginning just for formating display
			if(responseLists.size()==0){
				//Formated the response string with system type and FALSE status + response string
				formatedResponse = "\n"+getSystemTime()+"| OP#"+" "+failed+" "+response+".";
				//Adding the formated response string to arraylist
				responseLists.add(formatedResponse);	
			}
			else{
			//Formated the response string with system type and FALSE status + response string
			formatedResponse = getSystemTime()+"| OP#"+" "+failed+" "+response+".";
			//Adding the formated response string to arraylist
			responseLists.add(formatedResponse);
			}
		}
	}
	
/*** REVIEWD ***/   
//Store data into redis based on (key: value) format
    private void setDatatoRedis(String key, String value){
	    	
	    	//jedis.hsetnx(key, "\""+field+"\"", value);
	    	jedis.set(key, value);
	    	
	         
	    }

/*** REVIEWD ***/   
//Get value from redis based on the key
    private String GetDataFromRedis(String key){
	    	return jedis.get(key);
	 }	
    
    
    
    
    
    
 /*** REVIEWD ***/   
 private boolean CheckifUserExists(String userid){
  	  
     	 //if user_id found in the refMap for any MainKey
    	  if(RefMap.containsKey(userid)){
      	  addResponse(true,"REQUESTED USER: ("+userid+") FOUND");
      	  return true;
    	  }
    	  //if user doesn't exists
    	  else{
    		 addResponse(false,"REQUESTED USER: ("+userid+") DOES NOT EXISTS IN REDIS");
            return false;
    	  }
 }
    
    
    
 /*** REVIEWD ***/      
//Used to populate Filepath & FileName of each file sent to this api
//ex JsonFilePath & JsonFileName get the file name and path and set them to these static strings
    private void getFilePathFromURI(MultipartFile file, int fileType)
    
     {		isURIFileRecieved = false; //Reset the Flag to False
     
    		//Json File ->fileType=1
    	    if(fileType==1){    
 	      
     		JsonFileName = file.getOriginalFilename();
     		//set JsonFilePath to The Project Path + File Name
     		JsonFilePath = "C:/Users/donate/Documents/JSON/"+JsonFileName;
     		
     	   //Check if filename OR filepath are received
 	       if(!JsonFileName.isEmpty() || !JsonFilePath.isEmpty()){
 	       isURIFileRecieved=true;
 		   addResponse(true,"JSON File ("+JsonFileName+") Received");
 		   }
 		   else{
 		   addResponse(false,"JSON File ("+JsonFileName+") WAS NOT Received");
 		   }
     		
     		
           }
    	    
    	    
           //Schema File ->fileType=2
           if(fileType==2){    
	        //System.out.println("\n\n----------JSON Schema URI Received Successfully----------");
	        SchemaFileName = file.getOriginalFilename();
  		    //set JsonFilePath to The Project Path + File Name
  		    //SchemaFilePath = Paths.get(".").toAbsolutePath().normalize().toFile()+"/JSON/"+SchemaFileName;
	        SchemaFilePath = "C:/Users/donate/Documents/JSON/"+SchemaFileName;
	       
	       //Check if filename OR filepath are received
	       if(!SchemaFileName.isEmpty() || !SchemaFilePath.isEmpty()){
	       isURIFileRecieved=true;
		   addResponse(true, "SCHEMA File ("+SchemaFileName+") Received");
		   }
		   else{
		   addResponse(false, "SCHEMA File ("+SchemaFileName+") Was NOT Received");
		   }
	       
	    
            }
           
           
           //Merge File Body
            if(fileType==3){    
	       
  		    JsonMERGEFileName = file.getOriginalFilename();
  		    //set JsonFilePath to The Project Path + File Name
  		    //SchemaFilePath = Paths.get(".").toAbsolutePath().normalize().toFile()+"/JSON/"+SchemaFileName;
  		    JsonMERGEFilePath = "C:/Users/donate/Documents/JSON/"+JsonMERGEFileName;
	       
	       //Check if filename OR filepath are received
	       if(!JsonMERGEFileName.isEmpty() || !JsonMERGEFilePath.isEmpty()){
	       isURIFileRecieved=true;
		   addResponse(true, "JSON Merge File ("+JsonMERGEFileName+") Received");
		   }
		   else{
		   addResponse(false, "JSON Merge File ("+SchemaFileName+") Was NOT Received");
		   }
            
	       
             }  
            //PUT File Body
            if(fileType==4){   
            	
            JsonPUTFileName = file.getOriginalFilename();
       		//set JsonFilePath to The Project Path + File Name
       		//SchemaFilePath = Paths.get(".").toAbsolutePath().normalize().toFile()+"/JSON/"+SchemaFileName;
       		JsonPUTFilePath = "C:/Users/donate/Documents/JSON/"+JsonPUTFileName;
     	       
     	       //Check if filename OR filepath are received
     	    if(!JsonPUTFileName.isEmpty() || !JsonPUTFilePath.isEmpty()){
     	    isURIFileRecieved=true;
     		addResponse(true, "JSON PUT File ("+JsonPUTFileName+") Received");
     		}
     		else{
     		addResponse(false, "JSON Merge File ("+JsonPUTFileName+") Was NOT Received");
     		}
          } 		
     
            
         
     }
    
 /*** REVIEWD ***/     
 //ONLY This method is only use with HTTP-DELETE Request ONLY!   
 //Delete a hash from Redis needs the key and field 
    private void deleteDataFromRedis(String userid){
    	
    	
    	//if user_id found in the refMap for any MainKey
   	  
    	if(RefMap.containsKey(userid)){
     	  addResponse(true, "REQUESTED USER: ("+userid+") FOUND");
     	  
     	  //Delete The MainKey From Redis
     	  if(jedis.del(userid)!=null){
     	    addResponse(true, "Main Key ("+userid+") DELETED");
     	    
     	  }
     	  
     	  else{
     		  addResponse(true, "Unable to Delete Main Key ("+userid+")");
     		  return;
     	  }
     	  
     	  //Loop through the RefMap MainKey index inside the arraylist 
     	  //ex user_123 (mainkey) --> [Pinfo_12301, AInfo_12304,...] as subkeys stored in the arraylist
     	  //that is stored in the hashmap with the mainkey
     	 for (Map.Entry<String, ArrayList> entry : RefMap.entrySet()) {
		    
		    if(entry.getKey().equalsIgnoreCase(userid)){
		    ArrayList<String> value = entry.getValue();
		    for(String aSubKey : value){
		        
		    	//Delete Subkey and it's value field from redis
		    	if(jedis.del(aSubKey)!=null){
		    	 addResponse(true, "SUBKEY:("+aSubKey+") DATA DELETED FROM REDIS");
		    	}
		    	else{
		    	 addResponse(true, "Unable To Delete SUBKEY:("+aSubKey+")");
		    	 return;
		    	}
		        
		    }
		   }
		}
     	addResponse(true, "USER ("+userid+") Deleted Successfully From REDIS");
     	deleteUserFromES(userid);//Delete from Elastic Search
     	 populateMapsonStartUp();
     	 
        }
    	else{
    	addResponse(false,"REQUESTED USER: ("+userid+") DOES NOT EXISTS IN REDIS");
    	}
    	
    }
    
    
  /*** REVIEWD ***/    
  //Flush All REDIS KEYS EXCEPT KEY ("Schema")
  	 private void flushRedisValueNOTSchema(){
  		
  		 if(jedis.dbSize()==0){
  			 addResponse(false,"No Data Exist(s) In Redis");
  			 return;
  		 }
  		
  		 //Hold all Redis keys inside
  		 ArrayList<String> keylist = new ArrayList<>();
  		
  		 //Get all Keys from Redis
  		keylist.addAll(jedis.keys("*"));
  			 
  			 for (String string : keylist) {
  				  
  				  //if Schema Skip
  	               if(string.matches("Schema")){
  	                   
  	               }
  	               //Delete All Other Keys
  	               else{
  	            	jedis.del(string);
  	               }
  			 }
  		
  	 }
      
  	 
  	 public synchronized void ParseJsonDocAgainForMergeAndPutToBeESFormatCompatible(String MainKey, String allpayload){
  		 try{
  		    //Hold sub keys (address_789)
			 ArrayList<String> SubKeys = new ArrayList<String>();
			
			 JSONParser parser = new JSONParser();
			 Object obj;
			
				obj = parser.parse(allpayload);
			
			 JSONObject jsonObject = (JSONObject) obj;
			 
  		 for (Object key : jsonObject.keySet()) {
			    //Hold key
		        String keyStr = (String)key;
		        Object keyvalue = jsonObject.get(keyStr); //Hold Value
           
		         String payload=""; //Actual Payload 
	 			 String actualKey=""; //_type+_id
	 			 String formatedKey="";//"Vehicle_Info": 
		        
              //The actual field data is called keyvalue 
              payload = keyvalue.toString();
           
              //Getting the key(_type+_id= actualKEY) from the String
	        	 actualKey = GetkeyfromString(payload);
	        	
	        		
	        		//if JSONObject
			        if (keyvalue instanceof JSONObject){
			        	
                      formatedKey = "\""+keyStr+"\":"; //"Vehicle_Info": 
                      
			        	//if subkey is not found in redis -> new json document ->save it
			        	
                      String payloadWithKey = formatedKey+""+payload;
			        			//Store Data into Redis
					        	 setDatatoRedis(actualKey,payloadWithKey);
					        	 
					        	 //System.out.println("STORED DATA:"+GetDataFromRedis(actualKey));
		                         SubKeys.add(actualKey);
		                       
//			        		 System.out.println("***OBJECT***");
				        	 //print key, value and field
//	                    	 System.out.println("KEY: "+ actualKey);
//				        	 System.out.println("VALUE: " + payloadWithKey);
//				        	 System.out.println("PATH FROM ROOT: "+ MainKey+"/"+actualKey);
				        	 
				        	 JsonAsMapforES.put(actualKey, payloadWithKey);
				        	
			        }
			        
			       //if JSONArray
			       else if (keyvalue instanceof JSONArray){
			        	 
			        
			    	        formatedKey = "\""+keyStr+"\":"; //"Vehicle_Info": 
				        	//System.out.println(formatedKey);
				        	String payloadWithKey = formatedKey+""+payload;
				        	
			        	    //Store Data into Redis
					        setDatatoRedis(actualKey,payloadWithKey);
					        	 
					         //System.out.println("STORED DATA:"+GetDataFromRedis(actualKey));
		                     SubKeys.add(actualKey);
		                       
//				        	 System.out.println("***ARRAY***");
//				        	 //print key, value and field
//	                    	 System.out.println("KEY: "+ actualKey);
//				        	 System.out.println("VALUE: " + payloadWithKey);
//				        	 System.out.println("PATH FROM ROOT: "+  MainKey+"/"+actualKey);
				        	 JsonAsMapforES.put(actualKey, payloadWithKey);
				        	
			        }
	        		
	        		
	        	}
  		 
  		 }
    catch (ParseException e) {
		
		//e.printStackTrace();
	}
  		 
  		 
  		 
  		 
  	 }
  	 
  	 
  	 //Generate Etag
  	 public String EtagGenerator(){
  	   String uuid = UUID.randomUUID().toString();
	   String uuid_nodash = uuid.replaceAll("[\\s\\-()]", "");//remove dashes
	   String etag = "\""+uuid_nodash+"\"";
	   return etag;
  	 }
  	 
  	 
  	 //Generate Date in Format equal = Sat, 10 Dec 2016 05:43:33 GMT
  	 public String GenerateLastModifiedDate(){
  		 //get date for last Modified 
  		Date currentTime = new Date();
  		SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy hh:mm:ss z");
  		// Give it to me in GMT time.
  		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
  		String finalDate = sdf.format(currentTime);
        return finalDate;
  	 }
  	 
  	 
  	 
  	 //Handling HTTP Response Based on If-Match Or If-None-Match Etag Value 
  	 //Handling HTTP also if no Etag is presented at all -->Generate a new one
  	 public boolean HandleETagStatusMatch_NoneMatch(HttpServletRequest request, 
             HttpServletResponse response){
  		 
  		 
  	   
       //headerETag = response.getHeader("ETag");
  	   String Request_If_Match = request.getHeader("If-Match");
  	   String Request_If_None_Match = request.getHeader("If-None-Match");
  	   
  	  //If nothing sent in header 
  	   if(Request_If_Match==null && Request_If_None_Match==null){
  		 
  		   headerETag = EtagGenerator();
		   //Add ETag to Response Header
	       response.addHeader("ETag", headerETag);
	       lastModified = GenerateLastModifiedDate();//Update Last Modified Date
	      
  	   }
  	   
  	   //one of them is sent-->let's see which one
  	   else{
  	   
  	   //If-Non-Match is NOT Empty 
  	   if(Request_If_None_Match!=null && Request_If_None_Match.length()>0){
  		   
  		   //If it's equal to etag
  		   if(Request_If_None_Match.equalsIgnoreCase(headerETag)){
  			 response.addHeader("ETag", headerETag); //add same etag hold in static variables to header
  			 response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);//set status to 304 not modified
  			 response.addHeader("Last-Modified", lastModified);//Add Last Modified Date to Header
  			 return false;//stop execution
  		   }
  		   
  		   //if they are not equal
  		   else{
  			 //Add new Etag
  			 headerETag = EtagGenerator();
  			 response.addHeader("ETag", headerETag);
  			 lastModified = GenerateLastModifiedDate();//Update Last Modified Date
  			 //Serve Resource Normally...(no return false)
  		   }
  		   
  	   }
  	   
  	   
  	 //If-Match is Not Empty
  	   if(Request_If_Match!=null && Request_If_Match.length()>0){
  		   
  		   //If they are equal 
  		   if(Request_If_Match.equalsIgnoreCase(headerETag)){
  			 headerETag = EtagGenerator();//New Etag
  			 response.addHeader("ETag", headerETag);//Set the New Etag
  			 response.setStatus(HttpServletResponse.SC_OK);//set status to 200 OK
  			 lastModified = GenerateLastModifiedDate();//Update Last Modified Date
  			 //Serve Resource Normally
  		   }
  		   
  		  //if they are not equal
  		else{
  			 //Add new Etag
  			 headerETag = EtagGenerator();
  			 response.addHeader("ETag", headerETag);//Set the new Etag
  			 response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);//set status to 412 Preconditional Failed
  			 response.addHeader("Last-Modified", lastModified);//Add Last Modified Date to Header
  			 return false;//stop execution
  		   }
  		   
  	   }
  	   
  	 }
	return true;//== HTTP 200 OK
  	   
  	   
 } 	   
  	  
  	 
   
}//EOF
