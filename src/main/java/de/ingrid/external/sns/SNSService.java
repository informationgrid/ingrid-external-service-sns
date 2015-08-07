/*
 * **************************************************-
 * ingrid-external-service-sns
 * ==================================================
 * Copyright (C) 2014 - 2015 wemove digital solutions GmbH
 * ==================================================
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 * 
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl5
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * **************************************************#
 */
package de.ingrid.external.sns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
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
import de.ingrid.external.om.Term.TermType;
import de.ingrid.external.om.TreeTerm;
import de.ingrid.external.om.impl.FullClassifyResultImpl;
import de.ingrid.external.om.impl.LocationImpl;
import de.ingrid.external.om.impl.TermImpl;
import de.ingrid.external.sns.SNSMapper.HierarchyDirection;

/**
 * SNS Access implementing abstract gazetteer, thesaurus, fullClassify API (external services).
 */
public class SNSService implements GazetteerService, ThesaurusService, FullClassifyService, ChronicleService {

	private final static Logger log = Logger.getLogger(SNSService.class);	

	private static final String ADMINISTRATIVE_IDENTIFIER = "-admin-";

    // Error string for the frontend
    private static String ERROR_SNS_TIMEOUT = "SNS_TIMEOUT";
    private static String ERROR_SNS_INVALID_URL = "SNS_INVALID_URL";

    private SNSClient snsClient;
    private SNSMapper snsMapper;

    // Init Method is called by the Spring Framework on initialization
    public void init() throws Exception {
    	ResourceBundle resourceBundle = ResourceBundle.getBundle("sns");

    	if (log.isInfoEnabled()) {
    		log.info("initializing SNSService, creating SNSClient ! " 
    				+ "Thesaurus: " + resourceBundle.getString("sns.serviceURL.thesaurus")
    				+ "Gazetteer: " + resourceBundle.getString("sns.serviceURL.gazetteer")
    				+ "Chronicle: " + resourceBundle.getString("sns.serviceURL.chronicle"));
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
    	String excludedTerms = "";

    	if (log.isDebugEnabled()) {
    		log.debug("getRelatedLocationsFromLocation(): " + locationId + " includeFrom=" + includeFrom + " " + langFilter);
    	}

    	// no language in SNS for getPSI !!!
    	// NOTICE: includes location with passed id at FIRST position !
    	Resource topic = snsClient.getTerm(locationId, langFilter, FilterType.ONLY_LOCATIONS);
    	if (topic != null) {
	    	// iterate over all related locations fetched exclusively to get all information about
	    	// type and expiration date
	    	// List<Location> resultList = snsMapper.mapToLocationsFromLocation(location, checkExpired, langFilter);
	    	List<Location> resultList = new ArrayList<Location>();

	    	StmtIterator it = RDFUtils.getRelatedConcepts(topic);
	    	while (it.hasNext()) {
				Resource nodeRes = it.next().getResource();
				// we expect all necessary data to be inside the result
				// otherwise we have to query all terms separately, which takes too long
				//Resource locationRes = snsClient.getTerm(RDFUtils.getId(nodeRes), langFilter, FilterType.ONLY_LOCATIONS);
				Location loc = snsMapper.mapToLocation(nodeRes, new LocationImpl(), langFilter);
				if (!loc.getIsExpired())
					resultList.add(loc);
				else
				    excludedTerms += loc.getId() + ",";
			}
	    	
	    	// NOTICE: includes location with passed id to the beginning!
	    	if (includeFrom) {
	    		Location fromLocation = snsMapper.mapToLocation(topic, new LocationImpl(), langFilter);
	    		if (!fromLocation.getIsExpired())
	    			resultList.add(0, fromLocation);
	    	}
	
	    	if (log.isDebugEnabled()) {
	    	    int excludedLength = excludedTerms.isEmpty() ? 0 : excludedTerms.split(",").length;
	    		log.debug("return locations.size: " + resultList.size() + " (excluded: " + excludedLength + " => " + excludedTerms + ")");
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
    	}
    	
    	if (log.isDebugEnabled()) {
    		log.debug("return: " + result);
    	}

	    return result;
	}

	@Override
	public Location[] getLocationsFromText(String text, int analyzeMaxWords,
			boolean ignoreCase, Locale language) {
	    FilterType type = FilterType.ONLY_LOCATIONS;
	    List<Location> resultList = new ArrayList<Location>();
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getLocationsFromText(): " + text + " " + langFilter);
    	}

    	Resource[] res = snsAutoClassifyText(text,
    			analyzeMaxWords, type, ignoreCase, langFilter);

    	// boolean checkExpired = true;
    	if (res[1] != null) {
        	NodeIterator it = RDFUtils.getResults(res[1]);
        	while (it.hasNext()) {
                RDFNode node = it.next();
                Resource locationRes = snsClient.getTerm(RDFUtils.getId(node.asResource()), langFilter, type);
                if (locationRes == null) continue;
                Location loc = snsMapper.mapToLocation(locationRes, new LocationImpl(), langFilter);
                
                // do not add expired locations
                if (!loc.getIsExpired())
                    resultList.add(loc);
        	}
    	}
    	//List<Location> resultList = snsMapper.mapToLocationsFromResults(res[1], checkExpired, langFilter);

    	if (log.isDebugEnabled()) {
    		log.debug("return locations.size: " + resultList.size());
    	}

	    return resultList.toArray(new Location[resultList.size()]);
	}

	@Override
	public Location[] findLocationsFromQueryTerm(String queryTerm,
			QueryType typeOfQuery, de.ingrid.external.GazetteerService.MatchingType matching, Locale language) {
    	FilterType type = FilterType.ONLY_LOCATIONS;
    	List<Location> resultList = new ArrayList<Location>();
    	String searchType = getSNSSearchType(matching);
    	String langFilter = getSNSLanguageFilter(language);
    	boolean addDescriptors = false;

    	if (log.isDebugEnabled()) {
    		log.debug("findLocationsFromQueryTerm(): " + queryTerm + " " + FilterType.ONLY_LOCATIONS + " " + searchType + " " + langFilter);
    	}

    	Resource topics = snsFindTopics(null, queryTerm, type, searchType, addDescriptors, langFilter);
    	if (topics == null) return new Location[0];
    	
    	NodeIterator it = RDFUtils.getResults(topics);
    	while (it.hasNext()) {
			RDFNode node = it.next();
			//Resource locationRes = snsClient.getTerm(RDFUtils.getId(node.asResource()), langFilter, type);
			//if (locationRes == null) continue;
			Location loc = snsMapper.mapToLocation(node.asResource(), new LocationImpl(), langFilter);
			
			// exclude administrative locations if wanted!
    		if (typeOfQuery == QueryType.ONLY_ADMINISTRATIVE_LOCATIONS && !isAdministrativeLocation(loc))
    			continue;
			
    		// do not add expired locations
    		if (!loc.getIsExpired())
    			resultList.add(loc);
		}

    	if (log.isDebugEnabled()) {
    		log.debug("return locations.size: " + resultList.size());
    	}

	    return resultList.toArray(new Location[resultList.size()]);
	}

    // ----------------------- ThesaurusService -----------------------------------

	private boolean isAdministrativeLocation(Location loc) {
		return loc.getTypeId() != null && loc.getTypeId().contains(ADMINISTRATIVE_IDENTIFIER);
	}

	@Override
	public Term[] findTermsFromQueryTerm(String queryTerm, de.ingrid.external.ThesaurusService.MatchingType matching,
			boolean addDescriptors, Locale language) {
		return findTermsFromQueryTerm(null, queryTerm, matching, addDescriptors, language);
	}
	
	@Override
	public Term[] findTermsFromQueryTerm(String url, String queryTerm, de.ingrid.external.ThesaurusService.MatchingType matching,
			boolean addDescriptors, Locale language) {
    	String searchType = getSNSSearchType(matching);
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("findTermsFromQueryTerm(): " + queryTerm + " " + searchType + " " + langFilter);
    	}

    	Resource res = snsFindTopics(url, queryTerm, FilterType.ONLY_TERMS, searchType,
    			addDescriptors, langFilter);
    	List<Term> resultList = snsMapper.mapToTerms(res, null, langFilter);

    	if (log.isDebugEnabled()) {
    		log.debug("return locations.size: " + resultList.size());
    	}

	    return resultList.toArray(new Term[resultList.size()]);
	}

	@Override
	public TreeTerm[] getHierarchyNextLevel(String termId, Locale language) {
		return getHierarchyNextLevel(null, termId, language);
	}
	
	@Override
	public TreeTerm[] getHierarchyNextLevel(String url, String termId, Locale language) {
		long depth = 2;
		HierarchyDirection direction = HierarchyDirection.DOWN;
		boolean includeSiblings = false;
    	String langFilter = getSNSLanguageFilter(language);
		// if top terms wanted adapt parameters
		if (termId == null) {
			// depth 1 is enough, fetches children of top terms
		    // BUT we use 0 to speed up the process and assume that every root node has
		    // at least one child
			depth = 0;
		}

    	if (log.isDebugEnabled()) {
    		log.debug("getHierarchyNextLevel(): " + termId + " " + langFilter);
    	}

		Resource mapFragment = snsGetHierarchy(url, termId, depth, direction, includeSiblings, langFilter);

		List<TreeTerm> resultList = new ArrayList<TreeTerm>();
		if (mapFragment != null) {
			// we also need language for additional filtering ! SNS delivers wrong results !
			if (termId == null)
				resultList = snsMapper.mapRootToTreeTerms(termId, direction, mapFragment, langFilter);
			else
				resultList = snsMapper.mapToTreeTerms(termId, direction, mapFragment, langFilter);
	
	    	if (log.isDebugEnabled()) {
	    		log.debug("return terms.size: " + resultList.size());
	    	}
		}
	    return resultList.toArray(new TreeTerm[resultList.size()]);
	}

	@Override
	public TreeTerm getHierarchyPathToTop(String termId, Locale language) {
		return getHierarchyPathToTop(null, termId, language);
	}
	
	@Override
	public TreeTerm getHierarchyPathToTop(String url, String termId, Locale language) {
		long depth = SNSClient.MAX_HIERARCHY_DEPTH; // fetch maximum available
		HierarchyDirection direction = HierarchyDirection.UP;
		boolean includeSiblings = false;
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getHierarchyPathToTop(): " + termId + " " + langFilter);
    	}

		Resource hierarchy = snsGetHierarchy(url, termId, depth, direction, includeSiblings, langFilter);

		if (hierarchy == null) return null;
		
    	List<TreeTerm> resultList = snsMapper.mapToTreeTerms(termId, direction, hierarchy, langFilter);
    	
    	// get unresolved parents due to too short hierarchy
    	// in case the analyzed node has no direct parents we stop here
    	// because we cannot determine the correct top level node!
    	if (resultList.get( 0 ).getParents() != null) {
    		checkForNonRootElements(resultList, hierarchy, url, language);    		
    	}    	

    	if (log.isDebugEnabled()) {
    		log.debug("return startTerm: " + resultList.get(0));
    	}

	    return resultList.get(0);
	}

	/**
	 * This method checks all parent elements who have no further parent element if they are top elements.
	 * If not then the hierarchy is fetched from the last parent and the getHierarchyPathToTop is called.
	 */
	private void checkForNonRootElements(List<TreeTerm> resultList, Resource hierarchy, String url, Locale language) {
		for (TreeTerm treeTerm : resultList) {
			Resource parentRes = hierarchy.getModel().getResource(treeTerm.getId());
			if (treeTerm.getParents() != null) {
				checkForNonRootElements(treeTerm.getParents(), hierarchy, url, language);
			} else if (treeTerm.getParents() == null && !RDFUtils.isTopConcept(parentRes)) {
				List<TreeTerm> parents = getHierarchyPathToTop(url, treeTerm.getId(), language).getParents();
				if (parents != null) {
					for (TreeTerm parent : parents) {
						treeTerm.addParent(parent);					
					}
				}
			}
		}
		
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
    	String langFilter = getSNSLanguageFilter(language);

    	if (log.isDebugEnabled()) {
    		log.debug("getTerm(): " + termId + " " + langFilter);
    	}

    	// no language in SNS for getPSI !!!
    	Term topic = null;
    	Resource term = snsClient.getTerm(termId, langFilter, FilterType.ONLY_TERMS);
    	
    	if (term != null) {
	    	topic = snsMapper.mapToTerm(term, new TermImpl(), langFilter);
	
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

    	Resource[] res = snsAutoClassifyText(text,
    			analyzeMaxWords, FilterType.ONLY_TERMS, ignoreCase, langFilter);

    	List<Term> resultList = snsMapper.mapToTerms(res[0], TermType.DESCRIPTOR, langFilter);

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
		//FullClassifyResult result = snsMapper.mapToFullClassifyResult(resources[0], resources[1], resources[2], langFilter);
		FullClassifyResult result = convertResourcesToFullClassifyResult( resources, langFilter );
		result.setIndexedDocument(snsMapper.mapToIndexedDocument(getHtmlContent(url), url));

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
    	
		Resource[] resources = snsAutoClassifyText(text, analyzeMaxWords, filter, ignoreCase, langFilter);
		FullClassifyResult result = convertResourcesToFullClassifyResult( resources, langFilter );
		

    	if (log.isDebugEnabled()) {
    		int numTerms = result.getTerms() != null ? result.getTerms().size() : 0;
    		int numLocations = result.getLocations() != null ? result.getLocations().size() : 0;
    		int numEvents = result.getEvents() != null ? result.getEvents().size() : 0;
    		log.debug("FullClassifyResult: numTerms=" + numTerms + " numLocations=" + numLocations + " numEvents=" + numEvents);
    	}

		return result;
	}

    // ----------------------- PRIVATE -----------------------------------

	
	private FullClassifyResult convertResourcesToFullClassifyResult(Resource[] resources, String langFilter) {
	    List<Term> terms = new ArrayList<Term>();
        List<Location> locations = new ArrayList<Location>();
        List<Event> events = new ArrayList<Event>();
        for (int i=0; i<resources.length; i++) {
            Resource resource = resources[i];
            if (resource != null) {
                FilterType type = null;
                if (i == 0) type = FilterType.ONLY_TERMS;
                else if (i == 1) type = FilterType.ONLY_LOCATIONS;
                else if (i == 2) type = FilterType.ONLY_EVENTS;
                NodeIterator it = RDFUtils.getResults(resource);
                while (it.hasNext()) {
                    RDFNode node = it.next();
                    Resource locationRes = snsClient.getTerm(RDFUtils.getId(node.asResource()), langFilter, type);
                    if (locationRes == null) continue;
                    if (i == 0)
                        terms.add( snsMapper.mapToTerm( locationRes, new TermImpl(), langFilter) );
                    else if (i == 1)
                        locations.add( snsMapper.mapToLocation(locationRes, new LocationImpl(), langFilter) );
                    else if (i == 2)
                        events.add( snsMapper.mapToEvent( locationRes, langFilter) );
                }
            }
        }
        //FullClassifyResult result = snsMapper.mapToFullClassifyResult(resources[0], resources[1], resources[2], langFilter);
        FullClassifyResultImpl result = new FullClassifyResultImpl();
        result.setLocations(locations);
        result.setTerms(terms);
        result.setEvents(events);
        
        return result;
	}
	
	/** Call SNS findTopics. Map passed params to according SNS params. 
	 * @param url defines the service url to search in
	 */
	private Resource snsFindTopics(String url,
			String queryTerms, FilterType type, String searchType, boolean addDescriptors, String langFilter) {

    	Resource mapFragment = null;
    	try {
    		mapFragment = snsClient.findTopics(url, queryTerms, type, searchType,
    	            "FieldsType.captors", 0, 500, langFilter, addDescriptors);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.findTopics", e);
	    }
	    
	    return mapFragment;
	}
	
	/** Call SNS autoClassify. Map passed params to according SNS params. */
    private Resource[] snsAutoClassifyText(String text,
    		int analyzeMaxWords, FilterType filter, boolean ignoreCase, String langFilter) {
    	Resource[] resources = new Resource[3];
    	try {
    	    if (filter == null || filter == FilterType.ONLY_TERMS)
                resources[0] = snsClient.autoClassify(text, analyzeMaxWords, FilterType.ONLY_TERMS, ignoreCase, langFilter);
            if (filter == null || filter == FilterType.ONLY_LOCATIONS)
                resources[1] = snsClient.autoClassify(text, analyzeMaxWords, FilterType.ONLY_LOCATIONS, ignoreCase, langFilter);
            if (filter == null || filter == FilterType.ONLY_EVENTS)
                resources[2] = snsClient.autoClassify(text, analyzeMaxWords, FilterType.ONLY_EVENTS, ignoreCase, langFilter);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.autoClassify for text", e);
    	}
    	
    	return resources;
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

	/** Call SNS getHierachy. Map passed params to according SNS params. 
	 * @param url defines the service url to get the hierarchy from
	 */
	private Resource snsGetHierarchy(String url,
			String root, long depth, HierarchyDirection hierarchyDir,
			boolean includeSiblings, String langFilter) {
		if (root == null) {
			root = "scheme";
		}
		String direction = (hierarchyDir == HierarchyDirection.DOWN) ? "down" : "up";

    	Resource resource = null;
    	try {
    		if (url == null)
    			resource = snsClient.getHierachy(depth, direction, includeSiblings, langFilter, root);
    		else
    			resource = snsClient.getHierachy(new URL(url), depth, direction, includeSiblings, langFilter, root);
    	} catch (Exception e) {
	    	log.error("Error calling snsClient.getHierachy with root=" + root, e);
	    }
	    
	    return resource;
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
	/*private String getSNSFilterType(de.ingrid.external.FullClassifyService.FilterType filterType) {
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
	}*/

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

	/**
	 * So far the SNS-Service always delivers 40 results and you can only define the page. Moreover
	 * it is not possible to set more than one Collection to match the results. That brings the problem
	 * of paging. The only workaround here is to request all collections with all necessary pages and
	 * only extract "length" entries from the result. The formular is:
	 * pagesToFetch = ROUND((page*length)/staticPageNumCount) + ROUND(length/staticPageNumCount) + pageOffset
	 * E.g.: page=10, length=10: fetch for each collection/type the pages 0,1,2 (which returns 120 results
	 * for each collection (strict page results=40). Given we request 3 collections this means we get max. 360 hits from which we only 
	 * return 10 hits. 
	 * 
	 */
	@Override
	public Event[] findEventsFromQueryTerm(String term, de.ingrid.external.ChronicleService.MatchingType matchingType, 
			String[] inCollections, String dateStart, String dateEnd, Locale lang, int page, int length) {
		List<Event> events = new ArrayList<Event>();
		int totalResults = 0;
		
		String langFilter = getSNSLanguageFilter(lang);
		String type = getSNSSearchType(matchingType);
		
		// create an empty collection so that code below is executed!
		if (inCollections == null) inCollections = new String[] { "" };
		
		try {
			List<String> uniqueResults = new ArrayList<String>();
			int maxPage = new Double(Math.floor((page*length) / SNSClient.NUM_SEARCH_RESULTS)).intValue() + SNSClient.PAGE_START;
			int extraPages = new Double(Math.floor(length / SNSClient.NUM_SEARCH_RESULTS)).intValue();
			int start = new Long((page*length) % SNSClient.NUM_SEARCH_RESULTS).intValue();
			int pos = 0;
			
			// iterate over all collections or request more search results and filter afterwards 
			for (String collection : inCollections) {
				int numResultsOfCollection = 0; 
				for (int p=1; p <= (maxPage+extraPages); p++) {
					Resource eventsRes = snsClient.findEvents(term, type, collection, p, dateStart, dateEnd, langFilter, length);
					numResultsOfCollection = RDFUtils.getTotalResults(eventsRes.getModel());

					// return after all requested hits are collected
					if (events.size() == length) break;
					
					// TODO: iterator is not sorted, should be result1, result2, ...
					NodeIterator it = RDFUtils.getResults(eventsRes);
			    	while (it.hasNext()) {
			    		// only start to return results from correct page
			    		RDFNode eventNode = it.next();
			    		String eventId = RDFUtils.getEventId(eventNode.asResource());
			    		// skip identical results
			    		if (uniqueResults.contains(eventId)) continue;
			    		if (start > pos++) continue;
			    		// get complete concept and map it to an event
			    		Resource eventRes = snsClient.getTerm(eventId, langFilter, FilterType.ONLY_EVENTS);
			    		events.add(snsMapper.mapToEvent(eventRes, langFilter));
			    		uniqueResults.add(eventId);
			    		// return after all requested hits are collected
			    		if (events.size() == length) break;
			    	}
				}
				totalResults += numResultsOfCollection;
			}
		} catch (RemoteException e) {
			e.printStackTrace();
			return new Event[0];
		}
		return events.toArray(new Event[totalResults]);
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
			log.error("Error when getting anniversaries!", e);
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
	
	private String getHtmlContent(URL url) {
		String html = "";
		try {
			URLConnection urlConnection;
			urlConnection = url.openConnection();
			urlConnection.addRequestProperty("accept", "text/html");
			urlConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.2; WOW64)");
			//urlConnection.addRequestProperty("Content-Type", "text/html; charset=UTF-8");
			
			String contentType = "ISO-8859-1";
			String urlContentType = urlConnection.getContentType();
			if (urlContentType != null && urlContentType.contains("charset="))
				contentType = urlContentType.substring(urlContentType.indexOf("charset=") + 8);
			
			BufferedReader dis = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), contentType));
			String tmp = "";
			while ((tmp = dis.readLine()) != null) {
				html += " " + tmp;
			}
			dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	    return html;
	}

    @Override
    public Term[] findTermsFromQueryTerm(String arg0, String arg1,
            de.ingrid.external.ThesaurusService.MatchingType arg2, boolean arg3, Locale arg4) {
        log.warn( "this method is not supported!" );
        return null;
    }

    @Override
    public TreeTerm[] getHierarchyNextLevel(String arg0, String arg1, Locale arg2) {
        log.warn( "this method is not supported!" );
        return null;
    }

    @Override
    public TreeTerm getHierarchyPathToTop(String arg0, String arg1, Locale arg2) {
        log.warn( "this method is not supported!" );
        return null;
    }
}
