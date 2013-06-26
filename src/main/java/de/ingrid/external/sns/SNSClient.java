/*
 * Copyright (c) 1997-2005 by media style GmbH
 * 
 * $Source: DispatcherTest.java,v $
 */
package de.ingrid.external.sns;

import java.net.URL;
import java.net.URLEncoder;
import java.rmi.RemoteException;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.DoesNotExistException;

import de.ingrid.external.FullClassifyService.FilterType;

/**
 * Adapter which provides the access to the sns webservice.
 * 
 * created on 21.07.2005
 * <p>
 * 
 * @author hs
 */
public class SNSClient {

	private final static Logger log = Logger.getLogger(SNSClient.class);	

    private String fUserName;

    private String fPassword;

    private String fLanguage;

	private URL fUrlThesaurus;
	private URL fUrlGazetteer;
	private URL fUrlChronicle;


    /**
     * Constructs an instance by using the given parameters.
     * 
     * @param userName
     *            Is used for authentication on the webservice.
     * @param password
     *            Is used for authentication on the webservice.
     * @param language
     *            Is used to specify the preferred language for requests.
     * @throws Exception
     */
    public SNSClient(String userName, String password, String language) throws Exception {
        this(userName, password, language, null, null, null);
    }

    /**
     * Constructs an instance by using the given parameters.
     * 
     * @param userName
     *            Is used for authentication on the webservice.
     * @param password
     *            Is used for authentication on the webservice.
     * @param language
     *            Is used to specify the preferred language for requests.
     * @param url
     * @throws Exception
     */
    public SNSClient(String userName, String password, String language, URL urlThesaurus, URL urlGazetteer, URL urlChronicle) throws Exception {
        this.fUserName = userName;
        this.fPassword = password;
        this.fLanguage = language;
        this.fUrlThesaurus = urlThesaurus;
        this.fUrlGazetteer = urlGazetteer;
        this.fUrlChronicle = urlChronicle;
    }

    /**
     * Sends a findTopics request by using the underlying webservice client.<br>
     * All parameters will passed to the _findTopics request object.
     * 
     * @param queryTerm
     *            The Query.
     * @param path
     *            The path is used to qualify the result.
     * @param searchType
     *            Can be one of the provided <code>SearchType</code>s.
     * @param fieldsType
     *            Can be one of the provided <code>FieldsType</code>s.
     * @param offset
     *            Defines the number of topics to skip.
     * @param pageSize TODO
     * @param lang
     *            Is used to specify the preferred language for requests.
     * @param includeUse
     * @return The response object.
     * @throws Exception
     * @see SearchType
     * @see FieldsType
     */
    public synchronized Resource findTopics(String queryTerm, FilterType type, String searchType,
            String fieldsType, long offset, long pageSize, String lang, boolean includeUse) throws Exception {
    	
    	//try.iqvoc.net/search?t=labeling skos base&qt=exact&q=dance&for=concept&c=indoors&l=en
    	if (queryTerm == null) {
            throw new IllegalArgumentException("QueryTerm can not be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset can not be lower than 0");
        }
    	
    	// create an empty model
        Model model = ModelFactory.createDefaultModel();
        String query = null;
        // encode parameter
        queryTerm = URLEncoder.encode(queryTerm, "utf8");
        String params = "t=labeling-skosxl-base&qt="+searchType+"&q=" + queryTerm + "&l[]=" + lang + "&page=" + offset;
        
       	query = getUrlByFilter(type) + "search.rdf?" + params;
        
        // String query = "http://boden-params.herokuapp.com/en/search.rdf?utf8=%E2%9C%93&t=labeling-skos-base&qt=begins_with&q=wasser&for=all&c=&l%5B%5D=de&l%5B%5D=en";

        try {
        	// read the RDF/XML file
        	model.read(query);
        } catch (DoesNotExistException e) {
        	log.error("The search-function does not exist: " + query);
        	return null;
        } catch (Exception e) {
        	log.error("The URI seems to have a problem: " + query);
        	return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }
        
        return model.getResource(query);
    	
        /*
        FindTopics topicRequest = new FindTopics();
        topicRequest.setUser(this.fUserName);
        topicRequest.setPassword(this.fPassword);
        topicRequest.setLang(lang);
        topicRequest.setPath(path);
        topicRequest.setSearchType(searchType);
        topicRequest.setFields(fieldsType);
        topicRequest.setOffset(BigInteger.valueOf(offset));
        topicRequest.setPageSize(BigInteger.valueOf(pageSize));
        topicRequest.setQueryTerm(queryTerm);
        if (includeUse) {
            topicRequest.setIncludeUse("true");
        } else {
            topicRequest.setIncludeUse("false");
        }

        return this.fXtmSoapPortType.findTopicsOp(topicRequest);*/
    }

    /**
     * Sends a getPSI request by using the underlying webservice client. All parameters will passed to the _getPSI
     * request object.
     * 
     * @param topicID
     *            Current topic ID.
     * @param distance
     *            The distance-Parameter isn't used. Source: Interface-Spec. version 0.6.
     * @param filter
     *            Define filter for limit to a topic.
     * @return The response object.
     * @throws Exception
     */
    /*
    public synchronized Resource getPSI(String topicID, int distance, String filter) throws Exception {
        if (topicID == null) {
            throw new IllegalArgumentException("TopicID can not be null");
        }
        if (distance < 0 || distance > 3) {
            throw new IllegalArgumentException("Distance must have a value between 0 and 3");
        }

        GetPSI psiRequest = new GetPSI();
        psiRequest.setUser(this.fUserName);
        psiRequest.setPassword(this.fPassword);
        // The distance-Parameter isn't used. Source: Interface-Spec. version 0.6.
        psiRequest.setDistance(BigInteger.valueOf(distance));
        psiRequest.setId(topicID);
        if (null != filter) {
            psiRequest.setFilter(filter);
        }

        return this.fXtmSoapPortType.getPSIOp(psiRequest);
    	return null;
    }*/
    
    public synchronized Resource getTerm(String termId, String lang, FilterType type) {
   		return getTermByUri(getUrlByFilter(type), termId, lang);
    }
    
    public synchronized Resource getTermByUri(String uri, String termId, String lang) {
    
    	if (termId == null) {
    		throw new IllegalArgumentException("The ID must not be null!");
    	}
    	// create an empty model
        Model model = ModelFactory.createDefaultModel();

        int pos = termId.lastIndexOf('/')+1;
        String type = determineType(termId);
        String query = uri + lang + type + termId.substring(pos).trim() + ".rdf";

        try {
        	// read the RDF/XML file
        	model.read(query);
        } catch (DoesNotExistException e) {
        	log.error("The term does not exist: " + query);
        	return null;
        } catch (Exception e) {
        	log.error("The URI seems to have a problem: " + query);
        	return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }
        
        return model.getResource(termId);
    }

    private String determineType(String termId) {
		//if (termId.indexOf("/collections/") != -1)
		//	return "/collections/";
		return "/concepts/";
	}

	/**
     * Sends a autoClassify request by using the underlying webservice client.<br>
     * All parameters will passed to a _autoClassify request object.
     * 
     * @param document
     *            The text to analyze.
     * @param analyzeMaxWords
     *            The maximal number of words to analyze for a document.
     * @param filter
     *            Define filter for limit to a topic.
     * @param ignoreCase
     *            Set to true ignore capitalization of the document.
     * @param lang
     *            Language distinction.
     * @return A topic map fragment.
     * @throws Exception
     */
    public synchronized Resource autoClassify(String document, int analyzeMaxWords, String filter,
            boolean ignoreCase, String lang) throws Exception {
        if (document == null) {
            throw new IllegalArgumentException("document can not be null");
        }
        if (analyzeMaxWords < 0) {
            throw new IllegalArgumentException("AnalyzeMaxWords can not be lower than 0");
        }

        
        
        /*
        AutoClassify classifyRequest = new AutoClassify();
        classifyRequest.setUser(this.fUserName);
        classifyRequest.setPassword(this.fPassword);
        if (lang != null) {
            classifyRequest.setLang(lang);
        }
        classifyRequest.setDocument(document);
        classifyRequest.setAnalyzeMaxWords("" + analyzeMaxWords);
        if (ignoreCase) {
            classifyRequest.setIgnoreCase("true");
        } else {
            classifyRequest.setIgnoreCase("false");
        }
        if (null != filter) {
            classifyRequest.setFilter(filter);
        }

        return this.fXtmSoapPortType.autoClassifyOp(classifyRequest);*/
        return null;
    }

    /**
     * Sends a autoClassify request by using the underlying webservice client.<br>
     * All parameters will passed to a _autoClassify request object.
     * 
     * @param url
     *            The url to analyze.
     * @param analyzeMaxWords
     *            The maximal number of words to analyze for a document.
     * @param filter
     *            Define filter for limit to a topic.
     * @param ignoreCase
     *            Set to true ignore capitalization of the document.
     * @param lang
     *            Language distinction.
     * @return A topic map fragment.
     * @throws Exception
     */
    public synchronized Resource autoClassifyToUrl(String url, FilterType type, String lang) throws Exception {
        if (url == null) {
            throw new IllegalArgumentException("Url can not be null");
        }
        /*if (analyzeMaxWords < 0) {
            throw new IllegalArgumentException("AnalyzeMaxWords can not be lower than 0");
        }*/

        Model model = ModelFactory.createDefaultModel();

    	//http://iqvoc-chronicle.innoq.com/de/search.html?t=note-base&qt=contains&q=Bundesregierung&c=&l%5B%5D=de&date_min=1976-08-31&date_max=1976-08-31&commit=Suche
        String params = "/autoclassify/extract.rdf?uri=" + url;
        
        String query = getUrlByFilter(type) + lang + params;

        try {
        	// read the RDF/XML file
        	model.read(query);
        } catch (DoesNotExistException e) {
        	log.error("The autoclassify-function does not exist: " + query);
        	return null;
        } catch (Exception e) {
        	log.error("The URI seems to have a problem: " + query);
        	return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }
        
        return model.getResource(query);
        
        
        /*
        AutoClassify classifyRequest = new AutoClassify();
        classifyRequest.setUser(this.fUserName);
        classifyRequest.setPassword(this.fPassword);
        if (lang != null) {
            classifyRequest.setLang(lang);
        }
        classifyRequest.setUrl(url);
        classifyRequest.setAnalyzeMaxWords("" + analyzeMaxWords);
        if (ignoreCase) {
            classifyRequest.setIgnoreCase("true");
        } else {
            classifyRequest.setIgnoreCase("false");
        }
        if (null != filter) {
            classifyRequest.setFilter(filter);
        }

        return this.fXtmSoapPortType.autoClassifyOp(classifyRequest);*/
    }

    private String getUrlByFilter(FilterType type) {
		if (type == FilterType.ONLY_TERMS)
			return this.fUrlThesaurus.toString();
		else if (type == FilterType.ONLY_LOCATIONS)
			return this.fUrlGazetteer.toString();
		else if (type == FilterType.ONLY_EVENTS)
			return this.fUrlChronicle.toString();
		
		return null;
	}

	/**
     * Search the environment chronicles bases on findTopicslimits his however on the event types and extends the search
     * conditions by a time range or date.
     * @param searchType
     *            Can be one of the provided <code>SearchType</code>s.
     * @param fieldsType
     *            Can be one of the provided <code>FieldsType</code>s.
     * @param offset
     *            Defines the number of topics to skip.
     * @param at
     *            Exact time as parameter for the search for events.
     * @param lang
     *            Is used to specify the preferred language for requests.
     * @param length
     *            Number of elements that should be retrieved.
     * @param query
     *            The Query.
     * @param pathArray
     *            Array of paths for a topic type as search criterion.
     * 
     * @return A topic map fragment.
     * @throws RemoteException
     * @see SearchType
     * @see FieldsType
     */
    public synchronized Resource findEvents(String queryParam, String searchType, String fieldsType,
            long offset, String at, String lang, int length)
            throws RemoteException {
    	
    	return findEvents(queryParam, searchType, fieldsType, offset, at, at, lang, length);
    	
        /*if (log.isDebugEnabled()) {
            log.debug("findEvents: term=" + query + ", ignoreCase=" + ignoreCase +
            		", atDate=" + at +
            		", searchType=" + searchType + ", eventPath= " + pathArray +
            		", lang=" + lang);
        }

        FindEvents findEvents = new FindEvents();
        findEvents.setUser(this.fUserName);
        findEvents.setPassword(this.fPassword);
        findEvents.setQueryTerm(query);

        if (ignoreCase) {
            findEvents.setIgnoreCase("true");
        } else {
            findEvents.setIgnoreCase("false");
        }
        findEvents.setSearchType(searchType);
        findEvents.setLang(lang);
        findEvents.setPath(pathArray);
        // no fields type when looking for events !
        // from manual: "Falls mit path=/event gesucht wird, wird ohne Übergabe von fields auch
        // die Ereignisbeschreibung (description) durchsucht."
        //findEvents.setFields(fieldsType);
        findEvents.setOffset(BigInteger.valueOf(offset));
        findEvents.setPageSize(BigInteger.valueOf(length));
        // from manual: "Wird gar keine Zeitangabe übergeben, werden die anderen Suchbedingungen ohne 
        // zeitliche Einschränkung ausgewertet."
        if (at != null) {
            findEvents.setAt(at);        	
        }

        return this.fXtmSoapPortType.findEventsOp(findEvents);*/
    }

    /**
     * The request findEvents bases on findTopics, limits his however on the event types and extends the search
     * conditions by a time range or date.
     * @param searchType
     *            Can be one of the provided <code>SearchType</code>s.
     * @param inCollections
     *            Can be one of the provided <code>FieldsType</code>s.
     * @param offset
     *            Defines the number of topics to skip.
     * @param from
     *            Search from a time point in histrory on.
     * @param to
     *            Search until to time point in histrory on.
     * @param lang
     *            Is used to specify the preferred language for requests.
     * @param length
     *            Number of elements that should be retrieved.
     * @param query
     *            The Query
     * @param pathArray
     *            Array of paths for a topic type as search criterion.
     * 
     * @return A topic map fragment.
     * @throws RemoteException
     * @see SearchType
     * @see FieldsType
     */
    public synchronized Resource findEvents(String queryParam, String searchType, String inCollection,
            long offset, String from, String to, String lang, int length)
            throws RemoteException {
    	
    	
    	Model model = ModelFactory.createDefaultModel();
    	
    	//http://iqvoc-chronicle.innoq.com/de/search.html?t=note-base&qt=contains&q=Bundesregierung&c=&l%5B%5D=de&date_min=1976-08-31&date_max=1976-08-31&commit=Suche
    	String collParams = "".equals(inCollection) ? "" : "&c=" + inCollection;
    	
    	String params = "?t=note-base&qt="+searchType+"&q=" + queryParam + "&date_min=" + from + 
    			"&date_max=" + to + collParams + "&l[]=" + "de";
        String query = this.fUrlChronicle.toString() + "search.rdf" + params;

        try {
        	// read the RDF/XML file
        	model.read(query);
        } catch (DoesNotExistException e) {
        	log.error("The search-function does not exist: " + query);
        	return null;
        } catch (Exception e) {
        	log.error("The URI seems to have a problem: " + query);
        	return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }
        
        return model.getResource(query);
    	
        /*if (log.isDebugEnabled()) {
            log.debug("findEvents: term=" + query + ", ignoreCase=" + ignoreCase +
            		", fromDate=" + from + ", toDate=" + to +
            		", searchType=" + searchType + ", eventPath= " + pathArray +
            		", lang=" + lang);
        }

        FindEvents findEvents = new FindEvents();
        findEvents.setUser(this.fUserName);
        findEvents.setPassword(this.fPassword);
        findEvents.setQueryTerm(query);
        if (ignoreCase) {
            findEvents.setIgnoreCase("true");
        } else {
            findEvents.setIgnoreCase("false");
        }
        findEvents.setSearchType(searchType);
        findEvents.setLang(lang);
        findEvents.setPath(pathArray);
        // no fields type when looking for events !
        // from manual: "Falls mit path=/event gesucht wird, wird ohne Übergabe von fields auch
        // die Ereignisbeschreibung (description) durchsucht."
        // findEvents.setFields(fieldsType);
        findEvents.setOffset(BigInteger.valueOf(offset));
        findEvents.setPageSize(BigInteger.valueOf(length));
        // from manual: "Wird gar keine Zeitangabe übergeben, werden die anderen Suchbedingungen ohne 
        // zeitliche Einschränkung ausgewertet."
        if (from != null) {
            findEvents.setFrom(from);        	
        }
        if (to != null) {
            findEvents.setTo(to);        	
        }

        return this.fXtmSoapPortType.findEventsOp(findEvents);*/
    }

    /**
     * Anniversaries for past years are returned for a given date .
     * 
     * @param date
     *            The date which can be indicated.
     * @return A topic map fragment.
     * @throws RemoteException
     */
    public synchronized Resource anniversary(String date, String lang) throws RemoteException {
    	// http://iqvoc-chronicle.innoq.com/de/anniversary?date=2013-05-01
    	
    	// create an empty model
        Model model = ModelFactory.createDefaultModel();

        String query = this.fUrlChronicle.toString() + lang + "/anniversary.rdf?date=" + date;

        try {
        	// read the RDF/XML file
        	model.read(query);
        } catch (DoesNotExistException e) {
        	log.error("The anniversary-function does not exist: " + query);
        	return null;
        } catch (Exception e) {
        	log.error("The URI seems to have a problem: " + query);
        	return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }
        
        return model.getResource(query);
    }
    
    
    public Resource getHierachy(long depth, String direction, boolean includeSiblings,
            String lang, String root) throws RemoteException {
    	return getHierachy(this.fUrlThesaurus, depth, direction, includeSiblings, lang, root);
    }

    /**
     * Request to get a hierachical notion.
     * 
     * @param association
     *            Name of association to map hierachy. For now only "narrowerTermAssoc" is supported.
     * @param depth
     *            Returned hierachy depth.
     * @param direction
     *            Direction of hierachy. "up" and "down" are supported.
     * @param includeSiblings
     *            Get all siblings of the topics even if they aren't in the hierachy.
     * @param lang
     *            Language of the request.
     * @param root
     *            Topic id of the start notion.
     * @return The requestet hierachical notion.
     * @throws RemoteException
     */
    public Resource getHierachy(URL url, long depth, String direction, boolean includeSiblings,
            String lang, String root) throws RemoteException {
    	
    	int pos;
    	String uri = url.toString();
    	// extract identifier from url to search for it via hierarchy-api
		pos = uri.indexOf(url.getHost()) + url.getHost().length();
    	
    	// create an empty model
        Model model = ModelFactory.createDefaultModel();
    	String params = "?dir=" + direction + "&siblings=" + includeSiblings;
    	if (depth != 0) params += "&depth=" + depth; 
    	if (includeSiblings) params += "&siblings="+includeSiblings;
    	
    	String doc = root.substring(root.lastIndexOf("/")+1);
        Model hierarchy = model.read(uri.substring(0, pos) + "/"+lang+"/hierarchy/" + doc + ".rdf" + params);
		return hierarchy.getResource(uri+doc);
    	
        /*GetHierarchy hierarchy = new GetHierarchy();
        hierarchy.setAssociation(association);
        hierarchy.setDepth(BigInteger.valueOf(depth));
        hierarchy.setDirection(direction);
        hierarchy.setIncludeSiblings(Boolean.valueOf(includeSiblings));
        hierarchy.setLang(lang);
        hierarchy.setRoot(root);
        hierarchy.setUser(this.fUserName);
        hierarchy.setPassword(this.fPassword);

        return this.fXtmSoapPortType.getHierarchyOp(hierarchy);*/
    }

    /**
     * To a handed over output term SNS determines syntactically or semantically similar thesaurus terms. This request
     * can be implemented for several terms at the same time, whereby the results are assigned to their output term in
     * each case.
     * 
     * @param ignoreCase
     *            Set to true ignore capitalization of the document.
     * @param terms
     *            Output term, to which similar terms are looked for.
     * @param lang
     *            Is used to specify the preferred language for requests.
     * @return A topic map fragment.
     * @throws RemoteException
     */
    public synchronized Resource getSimilarTerms(boolean ignoreCase, String[] terms, String lang) {
    	
    	// create an empty model
        Model model = ModelFactory.createDefaultModel();

        // prepare terms as url-parameter
        String paramTerms = "";
        for (String term : terms) {
			paramTerms += term + "+";
		}
        paramTerms = paramTerms.substring(0, paramTerms.length()-1);
        String query = this.fUrlThesaurus.toString() + lang + "/similar.rdf?terms=" + paramTerms;

        try {
        	// read the RDF/XML file
        	model.read(query);
        } catch (DoesNotExistException e) {
        	log.error("The search-function does not exist: " + query);
        	return null;
        } catch (Exception e) {
        	log.error("The URI seems to have a problem: " + query);
        	return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }
        
        return model.getResource(query + "#top");
    	
        /*if ((null == terms) || (terms.length < 1)) {
            throw new IllegalArgumentException("No terms set.");
        }

        GetSimilarTerms getSimilarTerms = new GetSimilarTerms();
        getSimilarTerms.setUser(this.fUserName);
        getSimilarTerms.setPassword(this.fPassword);
        getSimilarTerms.setLang(lang);
        if (ignoreCase) {
            getSimilarTerms.setIgnoreCase("true");
        } else {
            getSimilarTerms.setIgnoreCase("false");
        }
        getSimilarTerms.setTerm(terms);

        return this.fXtmSoapPortType.getSimilarTermsOp(getSimilarTerms);*/
    }

    /**
     * Set timeout in milliseconds for the SN-Service connection.
     * 
     * @param timeout
     *            Timeout in milliseconds.
     */
    public void setTimeout(final int timeout) {
        //this.fSoapBinding.setTimeout(timeout);
    }

    /**
     * Get the preferred language for requests.
     * 
     * @return The language for this client.
     */
    public String getLanguage() {
        return this.fLanguage;
    }
}
