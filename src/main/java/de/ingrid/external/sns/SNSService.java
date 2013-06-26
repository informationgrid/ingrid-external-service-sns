package de.ingrid.external.sns;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.axis.AxisFault;
import org.apache.log4j.Logger;

import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import de.ingrid.external.ChronicleService;
import de.ingrid.external.FullClassifyService;
import de.ingrid.external.GazetteerService;
import de.ingrid.external.ThesaurusService;
import de.ingrid.external.om.Event;
import de.ingrid.external.om.FullClassifyResult;
import de.ingrid.external.om.Location;
import de.ingrid.external.om.RelatedTerm;
import de.ingrid.external.om.Term;
import de.ingrid.external.om.TreeTerm;
import de.ingrid.external.om.impl.LocationImpl;
import de.ingrid.external.om.impl.TermImpl;
import de.ingrid.external.sns.SNSMapper.HierarchyDirection;

/**
 * SNS Access implementing abstract gazetteer, thesaurus, fullClassify API (external services).
 */
public class SNSService implements GazetteerService, ThesaurusService, FullClassifyService, ChronicleService {

	private final static Logger log = Logger.getLogger(SNSService.class);	

	private final static String SNS_FILTER_THESA = "/thesa";
	private final static String SNS_FILTER_LOCATIONS = "/location";
	private final static String SNS_FILTER_EVENTS = "/event";
	private final static String SNS_PATH_ADMINISTRATIVE_LOCATIONS = "/location/admin";

    // Error string for the frontend
    private static String ERROR_SNS_TIMEOUT = "SNS_TIMEOUT";
    private static String ERROR_SNS_INVALID_URL = "SNS_INVALID_URL";

    private SNSClient snsClient;
    private SNSMapper snsMapper;

    // Init Method is called by the Spring Framework on initialization
    public void init() throws Exception {
    	ResourceBundle resourceBundle = ResourceBundle.getBundle("sns");

    	if (log.isInfoEnabled()) {
    		log.info("initializing SNSService, creating SNSClient ! username=" + resourceBundle.getString("sns.username")
    				+ " " + resourceBundle.getString("sns.serviceURL.thesaurus"));
    	}

    	snsClient = new SNSClient(
    			resourceBundle.getString("sns.username"),
    			resourceBundle.getString("sns.password"),
    			resourceBundle.getString("sns.language"),
        		new URL(resourceBundle.getString("sns.serviceURL.thesaurus")),
        		new URL(resourceBundle.getString("sns.serviceURL.gazetteer")),
        		new URL(resourceBundle.getString("sns.serviceURL.chronicle")));
    	snsClient.setTimeout(new Integer(resourceBundle.getString("sns.timeout")));
    	snsMapper = SNSMapper.getInstance(resourceBundle);
    }

    // ----------------------- GazetteerService -----------------------------------

	@Override
	public Location[] getRelatedLocationsFromLocation(String locationId, 
			boolean includeFrom, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getRelatedLocationsFromLocation(): " + locationId + " includeFrom=" + includeFrom + " " + langFilter);
    	}

    	// no language in SNS for getPSI !!!
    	// NOTICE: includes location with passed id at FIRST position !
    	//Topic[] topics = snsMapper.getTopics(snsGetPSI(locationId, SNS_FILTER_LOCATIONS));
    	Resource topic = snsClient.getTerm(locationId, langFilter, FilterType.ONLY_LOCATIONS);
    	if (topic != null) {

	    	boolean checkExpired = true;
	    	// TODO: iterate over all related locations fetched exclusively to get all information about
	    	// type and expiration date
	    	//List<Location> resultList = snsMapper.mapToLocationsFromLocation(location, checkExpired, langFilter);
	    	List<Location> resultList = new ArrayList<Location>();

	    	StmtIterator it = RDFUtils.getRelatedConcepts(topic);
	    	while (it.hasNext()) {
				Resource nodeRes = it.next().getResource();
				Resource locationRes = snsClient.getTerm(RDFUtils.getId(nodeRes), langFilter, FilterType.ONLY_LOCATIONS);
				resultList.add(snsMapper.mapToLocation(locationRes, new LocationImpl(), langFilter));
			}
	    	
	    	// NOTICE: includes location with passed id to the beginning!
	    	if (includeFrom)
	    		resultList.add(0, snsMapper.mapToLocation(topic, new LocationImpl(), langFilter));
	
	    	if (log.isDebugEnabled()) {
	    		log.debug("return locations.size: " + resultList.size());
	    	}
	
		    return resultList.toArray(new Location[resultList.size()]);
    	}
    	return new Location[0];
	}

	@Override
	public Location getLocation(String locationId, Locale language) {
    	Location result = null;
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getLocation(): " + locationId + " " + langFilter);
    	}

    	// no language in SNS for getPSI !!!
    	Resource location = snsClient.getTerm(locationId, langFilter, FilterType.ONLY_LOCATIONS);
    	if (location != null) {
	    	result = snsMapper.mapToLocation(location, new LocationImpl(), langFilter);
	    	
	    	// TODO: get typeName by requesting typeId and receive the name
	    	/*location = snsClient.getTerm(result.getTypeId(), langFilter, FilterType.ONLY_LOCATIONS);
	    	if (location != null)
	    		result.setTypeName(RDFUtils.getName(location, langFilter));
	    	*/
    	}
    	
    	if (log.isDebugEnabled()) {
    		log.debug("return: " + result);
    	}

	    return result;
	}

	@Override
	public Location[] getLocationsFromText(String text, int analyzeMaxWords,
			boolean ignoreCase, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getLocationsFromText(): " + text + " " + langFilter);
    	}

    	Resource res = snsAutoClassifyText(text,
    			analyzeMaxWords, FilterType.ONLY_LOCATIONS, ignoreCase, langFilter);

    	boolean checkExpired = true;
    	// TODO: List<Location> resultList = snsMapper.mapToLocations(topics, checkExpired, langFilter);

    	/*if (log.isDebugEnabled()) {
    		log.debug("return locations.size: " + resultList.size());
    	}

	    return resultList.toArray(new Location[resultList.size()]);*/
    	return null;
	}

	@Override
	public Location[] findLocationsFromQueryTerm(String queryTerm,
			QueryType typeOfQuery, de.ingrid.external.GazetteerService.MatchingType matching, Locale language) {
    	FilterType type = FilterType.ONLY_LOCATIONS;//getSNSLocationPath(typeOfQuery);
    	List<Location> resultList = new ArrayList<Location>();
    	String searchType = getSNSSearchType(matching);
    	String langFilter = getSNSLanguageFilter(language);
    	boolean addDescriptors = false;

    	if (log.isDebugEnabled()) {
    		log.debug("findLocationsFromQueryTerm(): " + queryTerm + " " + FilterType.ONLY_LOCATIONS + " " + searchType + " " + langFilter);
    	}

    	Resource topics = snsFindTopics(queryTerm, type, searchType, addDescriptors, langFilter);

    	if (topics == null) return new Location[0];
    	
    	boolean checkExpired = true;
    	
    	NodeIterator it = RDFUtils.getResults(topics);
    	while (it.hasNext()) {
			RDFNode node = it.next();
			Resource locationRes = snsClient.getTerm(RDFUtils.getId(node.asResource()), langFilter, type);
			resultList.add(snsMapper.mapToLocation(locationRes, new LocationImpl(), langFilter));
		}
    	//List<Location> resultList = snsMapper.mapToLocationsFromResults(topics, checkExpired, langFilter);
    	// TODO: remove expired locations

    	if (log.isDebugEnabled()) {
    		log.debug("return locations.size: " + resultList.size());
    	}

	    return resultList.toArray(new Location[resultList.size()]);
	}

    // ----------------------- ThesaurusService -----------------------------------

	@Override
	public Term[] findTermsFromQueryTerm(String queryTerm, de.ingrid.external.ThesaurusService.MatchingType matching,
			boolean addDescriptors, Locale language) {
    	String searchType = getSNSSearchType(matching);
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("findTermsFromQueryTerm(): " + queryTerm + " " + searchType + " " + langFilter);
    	}

    	Resource res = snsFindTopics(queryTerm, FilterType.ONLY_TERMS, searchType,
    			addDescriptors, langFilter);
    	List<Term> resultList = snsMapper.mapToTerms(res, null, langFilter);

    	if (log.isDebugEnabled()) {
    		log.debug("return locations.size: " + resultList.size());
    	}

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

    	if (log.isDebugEnabled()) {
    		log.debug("getHierarchyNextLevel(): " + termId + " " + langFilter);
    	}

		Resource mapFragment = snsGetHierarchy(termId,
				depth, direction, includeSiblings, langFilter);

		// we also need language for additional filtering ! SNS delivers wrong results !
		List<TreeTerm> resultList = null;
		if (termId == null)
			resultList = snsMapper.mapRootToTreeTerms(termId, direction, mapFragment, langFilter);
		else
			resultList = snsMapper.mapToTreeTerms(termId, direction, mapFragment, langFilter);

    	if (log.isDebugEnabled()) {
    		log.debug("return terms.size: " + resultList.size());
    	}

	    return resultList.toArray(new TreeTerm[resultList.size()]);
	}

	@Override
	public TreeTerm getHierarchyPathToTop(String termId, Locale language) {
		long depth = 0; // fetches till top
		HierarchyDirection direction = HierarchyDirection.UP;
		boolean includeSiblings = false;
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getHierarchyPathToTop(): " + termId + " " + langFilter);
    	}

		Resource hierarchy = snsGetHierarchy(termId, depth, direction, includeSiblings, langFilter);

		if (hierarchy == null) return null;
		
    	List<TreeTerm> resultList = snsMapper.mapToTreeTerms(termId, direction, hierarchy, langFilter);
    	
    	// TODO: get unresolved parents due to too short hierarchy?
    	//termId = getTermWithUnknownParents(resultList);

    	if (log.isDebugEnabled()) {
    		log.debug("return startTerm: " + resultList.get(0));
    	}

	    return resultList.get(0);
	}

	@Override
	public Term[] getSimilarTermsFromNames(String[] names, boolean ignoreCase, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getSimilarTermsFromNames(): " + names + " " + langFilter);
    	}

    	//Resource topics = snsGetSimilarTerms(names, ignoreCase, langFilter);
    	Resource resSimilarTerms = snsClient.getSimilarTerms(ignoreCase, names, langFilter);
    	List<Term> resultList = snsMapper.mapSimilarToTerms(resSimilarTerms, langFilter);

    	if (log.isDebugEnabled()) {
    		log.debug("return terms.size: " + resultList.size());
    	}

	    return resultList.toArray(new Term[resultList.size()]);
	}

	@Override
	public RelatedTerm[] getRelatedTermsFromTerm(String termId, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getRelatedTermsFromTerm(): " + termId + " " + langFilter);
    	}

    	// no language in SNS for getPSI !!!
		//TopicMapFragment mapFragment = snsGetPSI(termId, SNS_FILTER_THESA);
    	Resource term = snsClient.getTerm(termId, langFilter, FilterType.ONLY_TERMS);

    	if (term != null) {
    		List<RelatedTerm> resultList = snsMapper.mapToRelatedTerms(termId, term, langFilter);
    		

	    	if (log.isDebugEnabled()) {
	    		log.debug("return terms.size: " + resultList.size());
	    	}
	
		    return resultList.toArray(new RelatedTerm[resultList.size()]);
    	}
    	
    	return new RelatedTerm[0];
	}

	@Override
	public Term getTerm(String termId, Locale language) {
    	//Term result = null;
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getTerm(): " + termId + " " + langFilter);
    	}

    	// no language in SNS for getPSI !!!
    	Term topic = null;
    	Resource term = snsClient.getTerm(termId, langFilter, FilterType.ONLY_TERMS);
    	
    	if (term != null) {
	    	topic = snsMapper.mapToTerm(term, new TermImpl(), langFilter);
	    	/*if (topics != null) {
	            for (Topic topic : topics) {
	            	if (topic.getId().equals(termId)) {
	            		result = snsMapper.mapToTerm(topic, new TermImpl(), langFilter);
	            		break;
	            	}
	            }
	
	    	}*/
	
	    	if (log.isDebugEnabled()) {
	    		log.debug("return term: " + topic);
	    	}
    	}

	    return topic;
	}

	@Override
	public Term[] getTermsFromText(String text, int analyzeMaxWords,
			boolean ignoreCase, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getTermsFromText(): " + text + " maxWords=" + analyzeMaxWords + " ignoreCase=" + ignoreCase + " " + langFilter);
    	}

    	Resource res = snsAutoClassifyText(text,
    			analyzeMaxWords, FilterType.ONLY_TERMS, ignoreCase, langFilter);

    	List<Term> resultList = null; //snsMapper.mapToTerms(topics, TermType.DESCRIPTOR, langFilter);

    	if (log.isDebugEnabled()) {
    		log.debug("return terms.size: " + resultList.size());
    	}

	    return resultList.toArray(new Term[resultList.size()]);
	}

    // ----------------------- FullClassifyService -----------------------------------

	@Override
	public FullClassifyResult autoClassifyURL(URL url, int analyzeMaxWords,
			boolean ignoreCase, FilterType filter, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("autoClassifyURL(): " + url + " maxWords=" + analyzeMaxWords + " ignoreCase=" + ignoreCase + " " + langFilter);
    	}

		Resource[] resources = snsAutoClassifyURL(url, filter, langFilter);
		FullClassifyResult result = snsMapper.mapToFullClassifyResult(resources[0], resources[1], resources[2], langFilter);

    	if (log.isDebugEnabled()) {
    		int numTerms = result.getTerms() != null ? result.getTerms().size() : 0;
    		int numLocations = result.getLocations() != null ? result.getLocations().size() : 0;
    		int numEvents = result.getEvents() != null ? result.getEvents().size() : 0;
    		log.debug("FullClassifyResult: numTerms=" + numTerms + " numLocations=" + numLocations + " numEvents=" + numEvents);
    	}

		return result;
	}

	@Override
	public FullClassifyResult autoClassifyText(String text, int analyzeMaxWords,
			boolean ignoreCase, FilterType filter, Locale language) {
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("autoClassifyText(): maxWords=" + analyzeMaxWords + " ignoreCase=" + ignoreCase + " " + langFilter);
    	}

		Resource mapFragment =
			snsAutoClassifyText(text, analyzeMaxWords, filter, ignoreCase, langFilter);
		FullClassifyResult result = null; // TODO: snsMapper.mapToFullClassifyResult(mapFragment, langFilter);

    	if (log.isDebugEnabled()) {
    		int numTerms = result.getTerms() != null ? result.getTerms().size() : 0;
    		int numLocations = result.getLocations() != null ? result.getLocations().size() : 0;
    		int numEvents = result.getEvents() != null ? result.getEvents().size() : 0;
    		log.debug("FullClassifyResult: numTerms=" + numTerms + " numLocations=" + numLocations + " numEvents=" + numEvents);
    	}

		return result;
	}

    // ----------------------- PRIVATE -----------------------------------

	/** Call SNS findTopics. Map passed params to according SNS params. */
	private Resource snsFindTopics(String queryTerms,
			FilterType type, String searchType, boolean addDescriptors, String langFilter) {

    	Resource mapFragment = null;
    	try {
    		mapFragment = snsClient.findTopics(queryTerms, type, searchType,
    	            "FieldsType.captors", 0, 500, langFilter, addDescriptors);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.findTopics", e);
	    }
	    
	    /*if (null != mapFragment) {
	    	topics = mapFragment.getTopicMap().getTopic();
	    }*/
	    return mapFragment;
	}
	
	/** Call SNS getSimilarTerms. Map passed params to according SNS params. */
	/*private Topic[] snsGetSimilarTerms(String[] queryTerms, boolean ignoreCase, String langFilter) {
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
	}*/
	
	/** Call SNS getPSI. Map passed params to according SNS params. */
	/*private TopicMapFragment snsGetPSI(String topicId, String filter) {
    	TopicMapFragment mapFragment = null;
    	try {
    		mapFragment = snsClient.getPSI(topicId, 0, filter);
    	} catch (Exception e) {
        	log.error("Error calling snsClient.getPSI (topicId=" + topicId
            		+ ", filter=" + filter + "), we return null Details", e);
	    }
	    
	    return mapFragment;
	}*/
	
	/** Call SNS autoClassify. Map passed params to according SNS params. */
    private Resource snsAutoClassifyText(String text,
    		int analyzeMaxWords, FilterType filterType, boolean ignoreCase, String langFilter) {
    	String filter = getSNSFilterType(filterType);
    	Resource res = null;
    	try {
    		res = snsClient.autoClassify(text, analyzeMaxWords, filter, ignoreCase, langFilter);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.autoClassify", e);
    	}
    	
    	return res;
    }

	/**
	 * 
	 * @param url is the url to analyze
	 * @param langFilter
	 * @return the resources in the following order: Thesaurus, Gazetteer, Chronical
	 */
    private Resource[] snsAutoClassifyURL(URL url, FilterType filter, String langFilter) {
    	Resource[] resources = new Resource[3];
    	try {
    		if (filter == null || filter == FilterType.ONLY_TERMS)
    			resources[0] = snsClient.autoClassifyToUrl(url.toString(), FilterType.ONLY_TERMS, langFilter);
    		if (filter == null || filter == FilterType.ONLY_LOCATIONS)
    			resources[1] = snsClient.autoClassifyToUrl(url.toString(), FilterType.ONLY_LOCATIONS, langFilter);
    		if (filter == null || filter == FilterType.ONLY_EVENTS)
    			resources[2] = snsClient.autoClassifyToUrl(url.toString(), FilterType.ONLY_EVENTS, langFilter);

    	} catch (AxisFault f) {
    		log.info("Error while calling autoClassifyToUrl.", f);
    		if (f.getFaultString().contains("Timeout"))
    			throw new RuntimeException(ERROR_SNS_TIMEOUT);
    		else
    			throw new RuntimeException(ERROR_SNS_INVALID_URL);

    	} catch (Exception e) {
	    	log.error("Error calling snsClient.autoClassifyToUrl", e);
    	}
    	
    	return resources;
    }

	/** Call SNS getHierachy. Map passed params to according SNS params. */
	private Resource snsGetHierarchy(String root,
			long depth, HierarchyDirection hierarchyDir, boolean includeSiblings,
			String langFilter) {
		if (root == null) {
			root = "scheme";
		}
		String direction = (hierarchyDir == HierarchyDirection.DOWN) ? "down" : "up";

    	Resource resource = null;
    	try {
            resource = snsClient.getHierachy(depth, direction,
                    includeSiblings, langFilter, root);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.getHierachy with root=" + root, e);
	    }
	    
	    return resource;
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
	private String getSNSSearchType(de.ingrid.external.ThesaurusService.MatchingType matchingType) {
		// default is all locations !
    	String searchType = "begins_with";
    	if (matchingType == de.ingrid.external.ThesaurusService.MatchingType.CONTAINS) {
    		searchType = "contains";
    	} else if (matchingType == de.ingrid.external.ThesaurusService.MatchingType.EXACT) {
    		searchType = "exact";    		
    	}
		
    	return searchType;
	}

	/** Determine SearchType for SNS dependent from passed matching type. */
	private String getSNSSearchType(de.ingrid.external.GazetteerService.MatchingType matchingType) {
		// default is all locations !
    	String searchType = "begins_with";
    	if (matchingType == de.ingrid.external.GazetteerService.MatchingType.CONTAINS) {
    		searchType = "contains";
    	} else if (matchingType == de.ingrid.external.GazetteerService.MatchingType.EXACT) {
    		searchType = "exact";    		
    	}
		
    	return searchType;
	}
	
	/** Determine SearchType for SNS dependent from passed matching type. */
	private String getSNSSearchType(de.ingrid.external.ChronicleService.MatchingType matchingType) {
		// default is all locations !
		String searchType = "begins_with";
		if (matchingType == de.ingrid.external.ChronicleService.MatchingType.CONTAINS) {
			searchType = "contains";
		} else if (matchingType == de.ingrid.external.ChronicleService.MatchingType.EXACT) {
			searchType = "exact";    		
		}
		
		return searchType;
	}

	/** Determine filter type for SNS dependent from passed FilterType. */
	private String getSNSFilterType(de.ingrid.external.FullClassifyService.FilterType filterType) {
		// default is all !
		String filter = null;
    	if (filterType == de.ingrid.external.FullClassifyService.FilterType.ONLY_TERMS) {
    		filter = SNS_FILTER_THESA;
    	} else if (filterType == de.ingrid.external.FullClassifyService.FilterType.ONLY_LOCATIONS) {
    		filter = SNS_FILTER_LOCATIONS;
    	} else if (filterType == de.ingrid.external.FullClassifyService.FilterType.ONLY_EVENTS) {
    		filter = SNS_FILTER_EVENTS;
    	}
		
    	return filter;
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

	@Override
	public Event[] findEventsFromQueryTerm(String term, de.ingrid.external.ChronicleService.MatchingType matchingType, 
			String[] inCollections, String dateStart, String dateEnd, Locale lang) {
		List<Event> events = new ArrayList<Event>();
		
		String langFilter = getSNSLanguageFilter(lang);
		String type = getSNSSearchType(matchingType);
		
		// create an empty collection so that code below is executed!
		if (inCollections == null) inCollections = new String[] { "" };
		
		try {
			List<String> uniqueResults = new ArrayList<String>();
			// TODO: iterate over all collections or request more search results and filter afterwards 
			for (String collection : inCollections) {
				Resource eventsRes = snsClient.findEvents(term, type, collection, 0, dateStart, dateEnd, langFilter, 10);
				NodeIterator it = RDFUtils.getResults(eventsRes);
		    	while (it.hasNext()) {
		    		RDFNode eventNode = it.next();
		    		String eventId = RDFUtils.getEventId(eventNode.asResource());
		    		if (uniqueResults.contains(eventId)) continue;
		    		Resource eventRes = snsClient.getTerm(eventId, langFilter, FilterType.ONLY_EVENTS);
		    		events.add(snsMapper.mapToEvent(eventRes, langFilter));
		    		uniqueResults.add(eventId);
		    	}
			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new Event[0];
		}
		return events.toArray(new Event[events.size()]);
	}

	@Override
	public Event[] getAnniversaries(String date, Locale lang) {
		List<Event> events = new ArrayList<Event>();
		
		String langFilter = getSNSLanguageFilter(lang);
		try {
			Resource eventsRes = snsClient.anniversary(date, langFilter);
			events = snsMapper.mapToAnniversaries(eventsRes, langFilter);
			/*ResIterator it = RDFUtils.getConcepts(eventsRes.getModel());
	    	while (it.hasNext()) {
	    		Resource eventNode = it.next();
	    		String eventId = RDFUtils.getEventId(eventNode);
	    		Resource eventRes = snsClient.getTerm(eventId, langFilter, FilterType.ONLY_EVENTS);
	    		events.add(snsMapper.mapToEvent(eventRes, langFilter));
	    	}*/
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new Event[0];
		}
		return events.toArray(new Event[events.size()]);
	}

	@Override
	public Event getEvent(String eventId, Locale lang) {
		String langFilter = getSNSLanguageFilter(lang);
		Resource eventRes = snsClient.getTerm(eventId, langFilter, FilterType.ONLY_EVENTS);
		return snsMapper.mapToEvent(eventRes, langFilter);		
	}
}
