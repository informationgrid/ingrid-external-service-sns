package de.ingrid.external.sns;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
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
import de.ingrid.external.ThesaurusService;
import de.ingrid.external.om.FullClassifyResult;
import de.ingrid.external.om.Location;
import de.ingrid.external.om.RelatedTerm;
import de.ingrid.external.om.Term;
import de.ingrid.external.om.TreeTerm;
import de.ingrid.external.om.Term.TermType;
import de.ingrid.external.om.impl.LocationImpl;
import de.ingrid.external.om.impl.TermImpl;
import de.ingrid.external.sns.SNSMapper.HierarchyDirection;

/**
 * SNS Access implementing abstract gazetteer, thesaurus, fullClassify API (external services).
 */
public class SNSService implements GazetteerService, ThesaurusService, FullClassifyService {

	private final static Logger log = Logger.getLogger(SNSService.class);	

	private final static String SNS_FILTER_THESA = "/thesa";
	private final static String SNS_FILTER_LOCATIONS = "/location";
	private final static String SNS_PATH_ADMINISTRATIVE_LOCATIONS = "/location/admin";

    // Error string for the frontend
    private static String ERROR_SNS_TIMEOUT = "SNS_TIMEOUT";
    private static String ERROR_SNS_INVALID_URL = "SNS_INVALID_URL";

    private SNSClient snsClient;
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
    	snsMapper = SNSMapper.getInstance(resourceBundle);
    }

    // ----------------------- GazetteerService -----------------------------------

	@Override
	public Location[] getRelatedLocationsFromLocation(String locationId, 
			boolean includeFrom, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	// no language in SNS for getPSI !!!
    	// NOTICE: includes location with passed id at FIRST position !
    	Topic[] topics = snsMapper.getTopics(snsGetPSI(locationId, SNS_FILTER_LOCATIONS));

    	boolean checkExpired = true;
    	// NOTICE: includes location with passed id !
    	List<Location> resultList = snsMapper.mapToLocations(topics, checkExpired, langFilter);

    	// filter passed location ?
    	if (!includeFrom) {
    		for (Iterator<Location> it = resultList.iterator(); it.hasNext(); ) {
    			if (locationId.equals(it.next().getId())) {
    				it.remove();
    				break;
    			}
    		}
    	}

	    return resultList.toArray(new Location[resultList.size()]);
	}

	@Override
	public Location getLocation(String locationId, Locale language) {
    	Location result = null;
    	String langFilter = getSNSLanguageFilter(language);

    	// no language in SNS for getPSI !!!
    	Topic[] topics = snsMapper.getTopics(snsGetPSI(locationId, SNS_FILTER_LOCATIONS));
    	if (topics != null) {
            for (Topic topic : topics) {
            	if (topic.getId().equals(locationId)) {
            		result = snsMapper.mapToLocation(topic, new LocationImpl(), langFilter);
            		break;
            	}
            }

    	}
	    return result;
	}

	@Override
	public Location[] getLocationsFromText(String text, int analyzeMaxWords,
			boolean ignoreCase, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	Topic[] topics = snsAutoClassifyText(text, analyzeMaxWords, SNS_FILTER_LOCATIONS, ignoreCase, langFilter);

    	boolean checkExpired = true;
    	List<Location> resultList = snsMapper.mapToLocations(topics, checkExpired, langFilter);

	    return resultList.toArray(new Location[resultList.size()]);
	}

	@Override
	public Location[] findLocationsFromQueryTerm(String queryTerm,
			QueryType typeOfQuery, de.ingrid.external.GazetteerService.MatchingType matching, Locale language) {
    	String path = getSNSLocationPath(typeOfQuery);
    	SearchType searchType = getSNSSearchType(matching);
    	String langFilter = getSNSLanguageFilter(language);
    	boolean addDescriptors = false;

    	Topic[] topics = snsFindTopics(queryTerm, path, searchType,	addDescriptors, langFilter);

    	boolean checkExpired = true;
    	List<Location> resultList = snsMapper.mapToLocations(topics, checkExpired, langFilter);

	    return resultList.toArray(new Location[resultList.size()]);
	}

    // ----------------------- ThesaurusService -----------------------------------

	@Override
	public Term[] findTermsFromQueryTerm(String queryTerm, de.ingrid.external.ThesaurusService.MatchingType matching,
			boolean addDescriptors, Locale language) {
    	SearchType searchType = getSNSSearchType(matching);
    	String langFilter = getSNSLanguageFilter(language);

    	Topic[] topics = snsFindTopics(queryTerm, SNS_FILTER_THESA, searchType,
    			addDescriptors, langFilter);
    	List<Term> resultList = snsMapper.mapToTerms(topics, null, langFilter);

	    return resultList.toArray(new Term[resultList.size()]);
	}

	@Override
	public TreeTerm[] getHierarchyNextLevel(String termId, Locale language) {
		long depth = 2;
		HierarchyDirection direction = HierarchyDirection.DOWN;
		boolean includeSiblings = false;
    	String langFilter = getSNSLanguageFilter(language);
		// if top terms wanted adapt parameters
		if (termId == null) {
			// depth 1 is enough, fetches children of top terms
			depth = 1;
		}

		TopicMapFragment mapFragment = snsGetHierarchy(termId,
				depth, direction, includeSiblings, langFilter);

		// we also need language for additional filtering ! SNS delivers wrong results !
    	boolean checkExpired = false; // expired not set for thesa topics in SNS !
    	List<TreeTerm> resultList =
    		snsMapper.mapToTreeTerms(termId, direction, mapFragment, checkExpired, langFilter);

	    return resultList.toArray(new TreeTerm[resultList.size()]);
	}

	@Override
	public TreeTerm getHierarchyPathToTop(String termId, Locale language) {
		long depth = 0; // fetches till top
		HierarchyDirection direction = HierarchyDirection.UP;
		boolean includeSiblings = false;
    	String langFilter = getSNSLanguageFilter(language);

		TopicMapFragment mapFragment = snsGetHierarchy(termId,
				depth, direction, includeSiblings, langFilter);

    	boolean checkExpired = false; // expired not set for thesa topics in SNS !
    	List<TreeTerm> resultList =
    		snsMapper.mapToTreeTerms(termId, direction, mapFragment, checkExpired, langFilter);

	    return resultList.get(0);
	}

	@Override
	public Term[] getSimilarTermsFromNames(String[] names, boolean ignoreCase, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	Topic[] topics = snsGetSimilarTerms(names, ignoreCase, langFilter);
    	List<Term> resultList = snsMapper.mapToTerms(topics, null, langFilter);

	    return resultList.toArray(new Term[resultList.size()]);
	}

	@Override
	public RelatedTerm[] getRelatedTermsFromTerm(String termId, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	// no language in SNS for getPSI !!!
		TopicMapFragment mapFragment = snsGetPSI(termId, SNS_FILTER_THESA);

    	boolean checkExpired = false; // expired not set for thesa topics in SNS !
    	List<RelatedTerm> resultList = snsMapper.mapToRelatedTerms(termId, mapFragment, checkExpired, langFilter);

	    return resultList.toArray(new RelatedTerm[resultList.size()]);
	}

	@Override
	public Term getTerm(String termId, Locale language) {
    	Term result = null;
    	String langFilter = getSNSLanguageFilter(language);

    	// no language in SNS for getPSI !!!
    	Topic[] topics = snsMapper.getTopics(snsGetPSI(termId, SNS_FILTER_THESA));
    	if (topics != null) {
            for (Topic topic : topics) {
            	if (topic.getId().equals(termId)) {
            		result = snsMapper.mapToTerm(topic, new TermImpl(), langFilter);
            		break;
            	}
            }

    	}
	    return result;
	}

	@Override
	public Term[] getTermsFromText(String text, int analyzeMaxWords,
			boolean ignoreCase, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	Topic[] topics = snsAutoClassifyText(text, analyzeMaxWords, SNS_FILTER_THESA, ignoreCase, langFilter);
    	List<Term> resultList = snsMapper.mapToTerms(topics, TermType.DESCRIPTOR, langFilter);

	    return resultList.toArray(new Term[resultList.size()]);
	}

    // ----------------------- FullClassifyService -----------------------------------

	@Override
	public FullClassifyResult autoClassifyURL(URL url, int analyzeMaxWords,
			boolean ignoreCase, Locale language) {
		String filter = null;
    	String langFilter = getSNSLanguageFilter(language);

		TopicMapFragment mapFragment =
			snsAutoClassifyURL(url, analyzeMaxWords, filter, ignoreCase, langFilter);
		FullClassifyResult result = snsMapper.mapToFullClassifyResult(mapFragment, langFilter);

		return result;
	}

    // ----------------------- PRIVATE -----------------------------------

	/** Call SNS findTopics. Map passed params to according SNS params. */
	private Topic[] snsFindTopics(String queryTerms,
			String path, SearchType searchType, boolean addDescriptors, String langFilter) {
		Topic[] topics = null;

    	TopicMapFragment mapFragment = null;
    	try {
    		mapFragment = snsClient.findTopics(queryTerms, path, searchType,
    	            FieldsType.captors, 0, langFilter, addDescriptors);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.findTopics", e);
	    }
	    
	    if (null != mapFragment) {
	    	topics = mapFragment.getTopicMap().getTopic();
	    }
	    return topics;
	}
	
	/** Call SNS getSimilarTerms. Map passed params to according SNS params. */
	private Topic[] snsGetSimilarTerms(String[] queryTerms, boolean ignoreCase, String langFilter) {
    	TopicMapFragment mapFragment = null;
    	try {
    		mapFragment = snsClient.getSimilarTerms(ignoreCase, queryTerms, langFilter);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.getSimilarTerms", e);
	    }

        final List<Topic> resultList = new ArrayList<Topic>();
        final List<String> duplicateList = new ArrayList<String>();
	    if (null != mapFragment) {
	    	Topic[] topics = mapFragment.getTopicMap().getTopic();
	        if (null != topics) {
	            for (Topic topic : topics) {
	                final String topicId = topic.getId();
	                if (!duplicateList.contains(topicId)) {
	                    if (!topicId.startsWith("_Interface")) {
	                        resultList.add(topic);
	                    }
	                    duplicateList.add(topicId);
	                }
	            }
	        }
	    }

	    return resultList.toArray(new Topic[resultList.size()]);
	}
	
	/** Call SNS getPSI. Map passed params to according SNS params. */
	private TopicMapFragment snsGetPSI(String topicId, String filter) {
    	TopicMapFragment mapFragment = null;
    	try {
    		mapFragment = snsClient.getPSI(topicId, 0, filter);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.getPSI", e);
	    }
	    
	    return mapFragment;
	}
	
	/** Call SNS autoClassify. Map passed params to according SNS params. */
	private Topic[] snsAutoClassifyText(String text,
			int analyzeMaxWords, String filter, boolean ignoreCase, String langFilter) {
		Topic[] topics = null;
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
    		int analyzeMaxWords, String filter, boolean ignoreCase, String langFilter) {
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

	/** Call SNS getHierachy. Map passed params to according SNS params. */
	private TopicMapFragment snsGetHierarchy(String root,
			long depth, HierarchyDirection hierarchyDir, boolean includeSiblings,
			String langFilter) {
		if (root == null) {
			root = "toplevel";
		}
		String direction = (hierarchyDir == HierarchyDirection.DOWN) ? "down" : "up";

    	TopicMapFragment mapFragment = null;
    	try {
            mapFragment = snsClient.getHierachy("narrowerTermAssoc", depth, direction,
                    includeSiblings, langFilter, root);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.getHierachy with root=" + root, e);
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

	/** Determine SearchType for SNS dependent from passed matching type. */
	private SearchType getSNSSearchType(de.ingrid.external.ThesaurusService.MatchingType matchingType) {
		// default is all locations !
    	SearchType searchType = SearchType.beginsWith;
    	if (matchingType == de.ingrid.external.ThesaurusService.MatchingType.CONTAINS) {
    		searchType = SearchType.contains;
    	} else if (matchingType == de.ingrid.external.ThesaurusService.MatchingType.EXACT) {
    		searchType = SearchType.exact;    		
    	}
		
    	return searchType;
	}

	/** Determine SearchType for SNS dependent from passed matching type. */
	private SearchType getSNSSearchType(de.ingrid.external.GazetteerService.MatchingType matchingType) {
		// default is all locations !
    	SearchType searchType = SearchType.beginsWith;
    	if (matchingType == de.ingrid.external.GazetteerService.MatchingType.CONTAINS) {
    		searchType = SearchType.contains;
    	} else if (matchingType == de.ingrid.external.GazetteerService.MatchingType.EXACT) {
    		searchType = SearchType.exact;    		
    	}
		
    	return searchType;
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
