package de.ingrid.external.sns;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.slb.taxi.webservice.xtm.stubs.FieldsType;
import com.slb.taxi.webservice.xtm.stubs.SearchType;
import com.slb.taxi.webservice.xtm.stubs.TopicMapFragment;
import com.slb.taxi.webservice.xtm.stubs.xtm.Topic;

import de.ingrid.external.GazetteerService;
import de.ingrid.external.om.Location;
import de.ingrid.iplug.sns.SNSClient;
import de.ingrid.iplug.sns.SNSController;

/**
 * SNS Access implementing abstract gazetteer, thesaurus API (external services).
 */
public class SNSService implements GazetteerService {

	private final static Logger log = Logger.getLogger(SNSService.class);	

    private SNSClient snsClient;
    private SNSController snsController;
    private SNSMapper snsMapper;

    // Init Method is called by the Spring Framework on initialization
    public void init() throws Exception {
    	ResourceBundle resourceBundle = ResourceBundle.getBundle("sns");

    	int SNS_TIMEOUT = new Integer(resourceBundle.getString("sns.timeout"));

    	snsClient = new SNSClient(
    			resourceBundle.getString("sns.username"),
    			resourceBundle.getString("sns.password"),
    			resourceBundle.getString("sns.language"),
        		new URL(resourceBundle.getString("sns.serviceURL")));
    	snsClient.setTimeout(SNS_TIMEOUT);
    	snsController = new SNSController(snsClient, resourceBundle.getString("sns.nativeKeyPrefix"));
    	snsMapper = SNSMapper.getInstance(resourceBundle);
    }


	/* (non-Javadoc)
	 * @see de.ingrid.external.GazetteerService#getRelatedLocationsFromLocation(java.lang.String, java.util.Locale)
	 */
	@Override
	public Location[] getRelatedLocationsFromLocation(String arg0, Locale arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see de.ingrid.external.GazetteerService#getLocationsFromText(java.lang.String, int, boolean, java.util.Locale)
	 */
	@Override
	public Location[] getLocationsFromText(String text, int analyzeMaxWords,
			boolean ignoreCase, Locale language) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see de.ingrid.external.GazetteerService#getLocationsFromQueryTerms(java.lang.String, de.ingrid.external.GazetteerService.QueryType, java.util.Locale)
	 */
	@Override
	public Location[] getLocationsFromQueryTerms(String queryTerms,
			QueryType typeOfQuery, Locale language) {
	    return snsFindTopics(queryTerms, typeOfQuery, language);
	}
	
	/** Call SNS findTopics. Map passed params to according SNS params. */
	private Location[] snsFindTopics(String queryTerms,
			QueryType typeOfQuery, Locale language) {
    	List<Location> resultList = new ArrayList<Location>();
    	SearchType searchType = SearchType.beginsWith;
    	String path = getSNSLocationPath(typeOfQuery);
    	String langFilter = getSNSLanguageFilter(language);

    	TopicMapFragment mapFragment = null;
    	try {
    		mapFragment = snsClient.findTopics(queryTerms, path, searchType,
    	            FieldsType.captors, 0, langFilter, false);
    	} catch (Exception e) {
	    	log.error(e);
	    }
	    
	    if (null != mapFragment) {
	    	Topic[] topics = mapFragment.getTopicMap().getTopic();
	        if ((null != topics)) {
	            for (Topic topic : topics) {
	            	Location t = snsMapper.mapTopicToLocation(topic);
	            	if (t != null) {
	            		resultList.add(t);
	            	}
				}
	        }
	    }
	    return resultList.toArray(new Location[resultList.size()]);
	}
	
	/** Determine location path for SNS dependent from passed query type.
	 * @param typeOfQuery query type from request, pass null if default location path !
	 * @return SNS location path
	 */
	private String getSNSLocationPath(QueryType typeOfQuery) {
		// default is all locations !
    	String path = "/location";
    	if (typeOfQuery == QueryType.ONLY_ADMINISTRATIVE_LOCATIONS) {
    		path = "/location/admin";    		
    	}
		
    	return path;
	}

	/** Determine language filter for SNS dependent from passed language !
	 * @param language language from request, pass null if default language !
	 * @return SNS language filter
	 */
	private String getSNSLanguageFilter(Locale language) {
		// default is german !
    	String langFilter = "de";
    	if (language != null) {
    		langFilter = language.getLanguage();
    	}
		
    	return langFilter;
	}

}
