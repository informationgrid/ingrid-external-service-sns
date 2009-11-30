package de.ingrid.external.sns;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.axis.AxisFault;
import org.apache.log4j.Logger;

import com.slb.taxi.webservice.xtm.stubs.FieldsType;
import com.slb.taxi.webservice.xtm.stubs.SearchType;
import com.slb.taxi.webservice.xtm.stubs.TopicMapFragment;
import com.slb.taxi.webservice.xtm.stubs.xtm.Topic;

import de.ingrid.external.FullClassifyService;
import de.ingrid.external.GazetteerService;
import de.ingrid.external.om.FullClassifyResult;
import de.ingrid.external.om.Location;
import de.ingrid.iplug.sns.SNSClient;
import de.ingrid.iplug.sns.SNSController;

/**
 * SNS Access implementing abstract gazetteer, thesaurus API (external services).
 */
public class SNSServiceAccess implements GazetteerService, FullClassifyService {

	private final static Logger log = Logger.getLogger(SNSServiceAccess.class);	

	private final static String SNS_FILTER_LOCATIONS = "/location";
	private final static String SNS_PATH_ADMINISTRATIVE_LOCATIONS = "/location/admin";

    // Error string for the frontend
    private static String ERROR_SNS_TIMEOUT = "SNS_TIMEOUT";
    private static String ERROR_SNS_INVALID_URL = "SNS_INVALID_URL";

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


	@Override
	public Location[] getRelatedLocationsFromLocation(String locationId, Locale language) {
    	// no language in SNS for getPSI !!!
    	Topic[] topics = snsGetPSI(locationId);
    	List<Location> resultList = snsMapper.mapToLocations(topics);

	    return resultList.toArray(new Location[resultList.size()]);
	}

	@Override
	public Location[] getLocationsFromText(String text, int analyzeMaxWords,
			boolean ignoreCase, Locale language) {
    	Topic[] topics = snsAutoClassifyText(text, analyzeMaxWords, SNS_FILTER_LOCATIONS, ignoreCase, language);
    	List<Location> resultList = snsMapper.mapToLocations(topics);

	    return resultList.toArray(new Location[resultList.size()]);
	}

	@Override
	public Location[] getLocationsFromQueryTerms(String queryTerms,
			QueryType typeOfQuery, Locale language) {
    	String path = getSNSLocationPath(typeOfQuery);

    	Topic[] topics = snsFindTopics(queryTerms, path, language);
    	List<Location> resultList = snsMapper.mapToLocations(topics);

	    return resultList.toArray(new Location[resultList.size()]);
	}

	@Override
	public FullClassifyResult autoClassifyURL(URL url, int analyzeMaxWords,
			boolean ignoreCase, Locale language) {
		String filter = null;

		TopicMapFragment mapFragment =
			snsAutoClassifyURL(url, analyzeMaxWords, filter, ignoreCase, language);
		FullClassifyResult result = snsMapper.mapToFullClassifyResult(mapFragment);

		return result;
	}

	/** Call SNS findTopics. Map passed params to according SNS params. */
	private Topic[] snsFindTopics(String queryTerms,
			String path, Locale language) {
		Topic[] topics = null;
		SearchType searchType = SearchType.beginsWith;
    	String langFilter = getSNSLanguageFilter(language);

    	TopicMapFragment mapFragment = null;
    	try {
    		mapFragment = snsClient.findTopics(queryTerms, path, searchType,
    	            FieldsType.captors, 0, langFilter, false);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.findTopics", e);
	    }
	    
	    if (null != mapFragment) {
	    	topics = mapFragment.getTopicMap().getTopic();
	    }
	    return topics;
	}
	
	/** Call SNS getPSI. Map passed params to according SNS params. */
	private Topic[] snsGetPSI(String locationId) {
		Topic[] topics = null;

    	TopicMapFragment mapFragment = null;
    	try {
    		mapFragment = snsClient.getPSI(locationId, 0, SNS_FILTER_LOCATIONS);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.getPSI", e);
	    }
	    
	    if (null != mapFragment) {
	    	topics = mapFragment.getTopicMap().getTopic();
	    }
	    return topics;
	}
	
	/** Call SNS autoClassify. Map passed params to according SNS params. */
	private Topic[] snsAutoClassifyText(String text,
			int analyzeMaxWords, String filter, boolean ignoreCase, Locale language) {
		Topic[] topics = null;
    	String langFilter = getSNSLanguageFilter(language);

    	TopicMapFragment mapFragment = null;
    	try {
    		mapFragment = snsClient.autoClassify(text, analyzeMaxWords, filter, ignoreCase, langFilter);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.autoClassify", e);
	    }
	    
	    if (null != mapFragment) {
	    	topics = mapFragment.getTopicMap().getTopic();
	    }
	    return topics;
	}

	/** Call SNS autoClassify. Map passed params to according SNS params. */
    private TopicMapFragment snsAutoClassifyURL(URL url,
    		int analyzeMaxWords, String filter, boolean ignoreCase, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	TopicMapFragment mapFragment = null;
    	try {
    		mapFragment = snsClient.autoClassifyToUrl(url.toString(), analyzeMaxWords, filter, ignoreCase, langFilter);

    	} catch (AxisFault f) {
    		log.debug("Error while calling autoClassifyToUrl.", f);
    		if (f.getFaultString().contains("Timeout"))
    			throw new RuntimeException(ERROR_SNS_TIMEOUT);
    		else
    			throw new RuntimeException(ERROR_SNS_INVALID_URL);

    	} catch (Exception e) {
	    	log.error("Error calling snsClient.autoClassifyToUrl", e);
    	}
    	
    	return mapFragment;
    }

	/** Determine location path for SNS dependent from passed query type.
	 * @param typeOfQuery query type from request, pass null if default location path !
	 * @return SNS location path
	 */
	private String getSNSLocationPath(QueryType typeOfQuery) {
		// default is all locations !
    	String path = SNS_FILTER_LOCATIONS;
    	if (typeOfQuery == QueryType.ONLY_ADMINISTRATIVE_LOCATIONS) {
    		path = SNS_PATH_ADMINISTRATIVE_LOCATIONS;
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
