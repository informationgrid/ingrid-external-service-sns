/*
 * **************************************************-
 * ingrid-external-service-sns
 * ==================================================
 * Copyright (C) 2014 - 2023 wemove digital solutions GmbH
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
/*
 * Copyright (c) 1997-2005 by media style GmbH
 *
 * $Source: DispatcherTest.java,v $
 */
package de.ingrid.external.sns;

import de.ingrid.external.FullClassifyService.FilterType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.DoesNotExistException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.rmi.RemoteException;

/**
 * Adapter which provides the access to the sns webservice.
 * <p>
 * created on 21.07.2005
 * <p>
 *
 * @author hs
 */
public class SNSClient {

    private final static Logger log = LogManager.getLogger(SNSClient.class);

    public static final int MAX_HIERARCHY_DEPTH = 4;

    public static final int NUM_SEARCH_RESULTS = 40;

    public static final int PAGE_START = 1;

    private String fLanguage;

    private URL fUrlThesaurus;
    private URL fUrlGazetteer;
    private URL fUrlChronicle;


    /**
     * Constructs an instance by using the given parameters.
     *
     * @param userName Is used for authentication on the webservice.
     * @param password Is used for authentication on the webservice.
     * @param language Is used to specify the preferred language for requests.
     * @throws Exception
     */
    public SNSClient(String userName, String password, String language) throws Exception {
        this(userName, password, language, null, null, null);
    }

    /**
     * Constructs an instance by using the given parameters.
     *
     * @param userName Is used for authentication on the webservice.
     * @param password Is used for authentication on the webservice.
     * @param language Is used to specify the preferred language for requests.
     * @throws Exception
     */
    public SNSClient(String userName, String password, String language, URL urlThesaurus, URL urlGazetteer, URL urlChronicle) throws Exception {
        this.fLanguage = language;
        this.fUrlThesaurus = urlThesaurus;
        this.fUrlGazetteer = urlGazetteer;
        this.fUrlChronicle = urlChronicle;
    }

    /**
     * Sends a findTopics request by using the underlying webservice client.<br>
     * All parameters will passed to the _findTopics request object.
     *
     * @param queryTerm  The Query.
     * @param searchType Can be one of the provided <code>SearchType</code>s.
     * @param fieldsType Can be one of the provided <code>FieldsType</code>s.
     * @param offset     Defines the number of topics to skip.
     * @param pageSize   TODO
     * @param lang       Is used to specify the preferred language for requests.
     * @param includeUse
     * @return The response object.
     * @throws Exception
     */
    public synchronized Resource findTopics(String queryTerm, FilterType type, String searchType,
                                            String fieldsType, long offset, long pageSize, String lang, boolean includeUse) throws Exception {
        return findTopics(null, queryTerm, type, searchType, fieldsType, offset, pageSize, lang, includeUse);
    }

    public synchronized Resource findTopics(String url, String queryTerm, FilterType type, String searchType,
                                            String fieldsType, long offset, long pageSize, String lang, boolean includeUse) throws Exception {

        if (queryTerm == null) {
            throw new IllegalArgumentException("QueryTerm can not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("FilterType can not be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset can not be lower than 0");
        }

        // create an empty model
        Model model = ModelFactory.createDefaultModel();
        String query = null;
        // encode parameter
        queryTerm = URLEncoder.encode(queryTerm, "utf8");
        String params = "t=labels&qt=" + searchType + "&q=" + queryTerm + "&l=" + lang + "&page=" + offset;

        String host = (url == null) ? getUrlByFilter(type) : HtmlUtils.prepareUrl(url);
        query = host + "search.rdf?" + params;

        if (log.isDebugEnabled()) {
            log.debug("Searching by: {}", query);
        }

        try {
            // read the RDF/XML file
            model.read(query);
        } catch (DoesNotExistException e) {
            log.error("The search-function does not exist: {}", query, e);
            return null;
        } catch (Exception e) {
            log.error("The URI seems to have a problem: {}", query, e);
            return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }

        return model.getResource(query);
    }

    public synchronized Resource getTerm(String termId, String lang, FilterType type) {
        if (type == null) {
            throw new IllegalArgumentException("FilterType can not be null");
        }
        return getTermByUri(getUrlByFilter(type), termId, lang);
    }

    public synchronized Resource getTermByUri(String uri, String termId, String lang) {

        if (termId == null) {
            throw new IllegalArgumentException("The ID must not be null!");
        }
        // create an empty model
        Model model = ModelFactory.createDefaultModel();

        int pos = termId.lastIndexOf('/') + 1;
        String type = determineType(termId);
        String query = HtmlUtils.prepareUrl(uri) + lang + type + termId.substring(pos).trim() + ".rdf";

        if (log.isDebugEnabled()) {
            log.debug("Fetching term from: {}", query);
        }

        try {
            // read the RDF/XML file
            model.read(query);
        } catch (DoesNotExistException e) {
            log.error("The term does not exist: {}", query, e);
            return null;
        } catch (Exception e) {
            log.error("The URI seems to have a problem: {}", query, e);
            return null;
        }

        // write it to standard out
        // throws error!
        /*if (log.isDebugEnabled()) {
            model.write(System.out);
        }*/

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
     * @param document        The text to analyze.
     * @param analyzeMaxWords The maximal number of words to analyze for a document.
     * @param type            Define filter for limit to a topic.
     * @param ignoreCase      Set to true ignore capitalization of the document.
     * @param lang            Language distinction.
     * @return A topic map fragment.
     * @throws Exception
     */
    public synchronized Resource autoClassify(String document, int analyzeMaxWords, FilterType type,
                                              boolean ignoreCase, String lang) throws Exception {
        if (document == null) {
            throw new IllegalArgumentException("document can not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("FilterType can not be null");
        }
        if (analyzeMaxWords < 0) {
            throw new IllegalArgumentException("AnalyzeMaxWords can not be lower than 0");
        }

        // open a connection to the target url
        String query = getUrlByFilter(type) + lang + "/autoclassify/plain.rdf";
        URL url = new URL(query);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("charset", "utf-8");
        connection.connect();

        // send the document data to analyze
        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
        String content = "content=" + document;
        // replace all '%' as the SNS returns 400 Bad Request otherwise REDMINE-2321
        content = content.replaceAll("%", " ");
        writer.write(content);
        writer.flush();

        // read the response into the model
        Model model = ModelFactory.createDefaultModel();
        try {
            model.read(connection.getInputStream(), null);
        } catch (FileNotFoundException e) {
            log.warn("The autoClassify-Service for type: {} seems not to be available: {}", type, e.getMessage());
            return null;
        }

        if (log.isDebugEnabled()) {
            model.write(System.out);
        }

        return model.getResource(query);
    }

    /**
     * Sends a autoClassify request by using the underlying webservice client.<br>
     * All parameters will passed to a _autoClassify request object.
     *
     * @param url             The url to analyze.
     * @param lang            Language distinction.
     * @return A topic map fragment.
     * @throws Exception
     */
    public synchronized Resource autoClassifyToUrl(String url, FilterType type, String lang) throws Exception {
        if (url == null) {
            throw new IllegalArgumentException("Url can not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("FilterType can not be null");
        }

        Model model = ModelFactory.createDefaultModel();

        String params = "/autoclassify/extract.rdf?uri=" + url;
        String query = getUrlByFilter(type) + lang + params;

        if (log.isDebugEnabled()) {
            log.debug("AutoClassify by: {}", query);
        }

        try {
            // read the RDF/XML file
            model.read(query);
        } catch (DoesNotExistException e) {
            log.error("The autoclassify-function does not exist: {}", query, e);
            return null;
        } catch (Exception e) {
            log.error("The URI seems to have a problem: {}", query, e);
            return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }

        return model.getResource(query);
    }

    private String getUrlByFilter(FilterType type) {
        String uri = null;
        if (type == FilterType.ONLY_TERMS)
            uri = this.fUrlThesaurus.toString();
        else if (type == FilterType.ONLY_LOCATIONS)
            uri = this.fUrlGazetteer.toString();
        else if (type == FilterType.ONLY_EVENTS)
            uri = this.fUrlChronicle.toString();

        return HtmlUtils.prepareUrl(uri);
    }

    /**
     * Search the environment chronicles bases on findTopicslimits his however on the event types and extends the search
     * conditions by a time range or date.
     *
     * @param searchType Can be one of the provided <code>SearchType</code>s.
     * @param fieldsType Can be one of the provided <code>FieldsType</code>s.
     * @param offset     Defines the number of topics to skip.
     * @param at         Exact time as parameter for the search for events.
     * @param lang       Is used to specify the preferred language for requests.
     * @param length     Number of elements that should be retrieved.
     * @return A topic map fragment.
     * @throws UnsupportedEncodingException
     */
    public synchronized Resource findEvents(String queryParam, String searchType, String fieldsType,
                                            long offset, String at, String lang, int length)
            throws RemoteException, UnsupportedEncodingException {

        return findEvents(queryParam, searchType, fieldsType, offset, at, at, lang, length);
    }

    /**
     * The request findEvents bases on findTopics, limits his however on the event types and extends the search
     * conditions by a time range or date.
     *
     * @param searchType    Can be one of the provided <code>SearchType</code>s.
     * @param offset        Defines the number of topics to skip.
     * @param from          Search from a time point in histrory on.
     * @param to            Search until to time point in histrory on.
     * @param lang          Is used to specify the preferred language for requests.
     * @param length        Number of elements that should be retrieved.
     * @return A topic map fragment.
     * @throws RemoteException
     * @throws UnsupportedEncodingException
     */
    public synchronized Resource findEvents(String queryParam, String searchType, String inCollection,
                                            long offset, String from, String to, String lang, int length)
            throws RemoteException, UnsupportedEncodingException {

        Model model = ModelFactory.createDefaultModel();

        if (from == null) from = "";
        if (to == null) to = "";

        String collParams = (inCollection == null || "".equals(inCollection)) ? "" : "&c=" + inCollection;
        // TODO: use t=notes instead of t=pref_labels to get more search results
        // however an error occurred when doing so (06.05.2014)
        // 23.10.2019: Problem with t=notes use t=definitions see https://redmine.wemove.com/issues/2236
        String params = "?t=definitions&qt=" + searchType + "&q=" + URLEncoder.encode(queryParam, "utf8") + "&date_min=" + from +
                "&date_max=" + to + collParams + "&l=" + "de" + "&page=" + offset + "&for=concept";
        String query = getUrlByFilter(FilterType.ONLY_EVENTS) + "search.rdf" + params;

        if (log.isDebugEnabled()) {
            log.debug("Searching for Events with: {}", query);
        }

        try {
            // read the RDF/XML file
            model.read(query);
        } catch (DoesNotExistException e) {
            log.error("The search-function does not exist: {}", query, e);
            return null;
        } catch (Exception e) {
            log.error("The URI seems to have a problem: {}", query, e);
            return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }

        return model.getResource(query);
    }

    /**
     * Anniversaries for past years are returned for a given date .
     *
     * @param date The date which can be indicated.
     * @return A topic map fragment.
     * @throws RemoteException
     */
    public synchronized Resource anniversary(String date, String lang) throws RemoteException {
        // create an empty model
        Model model = ModelFactory.createDefaultModel();

        String query = getUrlByFilter(FilterType.ONLY_EVENTS) + lang + "/anniversary.rdf?date=" + date;

        if (log.isDebugEnabled()) {
            log.debug("Getting anniversary from: {}", query);
        }

        try {
            // read the RDF/XML file
            model.read(query);
        } catch (DoesNotExistException e) {
            log.error("The anniversary-function does not exist: {}", query, e);
            return null;
        } catch (Exception e) {
            log.error("The URI seems to have a problem: {}", query, e);
            return null;
        }

        // write it to standard out
        // throws error!
        /*if (log.isDebugEnabled()) {
            model.write(System.out);
        }*/

        return model.getResource(query);
    }


    public Resource getHierachy(long depth, String direction, boolean includeSiblings,
                                String lang, String root) throws RemoteException {
        return getHierachy(this.fUrlThesaurus, depth, direction, includeSiblings, lang, root);
    }

    /**
     * Request to get a hierachical notion.
     *
     * @param depth           Returned hierachy depth.
     * @param direction       Direction of hierachy. "up" and "down" are supported.
     * @param includeSiblings Get all siblings of the topics even if they aren't in the hierachy.
     * @param lang            Language of the request.
     * @param root            Topic id of the start notion.
     * @return The requestet hierachical notion.
     * @throws RemoteException
     */
    public Resource getHierachy(URL url, long depth, String direction, boolean includeSiblings,
                                String lang, String root) throws RemoteException {

        String uri = HtmlUtils.prepareUrl(url.toString());
        String host = uri;

        // create an empty model
        Model model = ModelFactory.createDefaultModel();
        String params = "?dir=" + direction + "&siblings=" + includeSiblings;
        if (depth != -1) params += "&depth=" + depth;
        if (includeSiblings) params += "&siblings=" + includeSiblings;

        String doc = root.substring(root.lastIndexOf("/") + 1);
        Model hierarchy = model.read(host + lang + "/hierarchy/" + doc + ".rdf" + params);
        return hierarchy.getResource(host + doc);
    }

    /**
     * To a handed over output term SNS determines syntactically or semantically similar thesaurus terms. This request
     * can be implemented for several terms at the same time, whereby the results are assigned to their output term in
     * each case.
     *
     * @param ignoreCase Set to true ignore capitalization of the document.
     * @param terms      Output term, to which similar terms are looked for.
     * @param lang       Is used to specify the preferred language for requests.
     * @return A topic map fragment.
     * @throws RemoteException
     */
    public synchronized Resource getSimilarTerms(boolean ignoreCase, String[] terms, String lang) {

        if ((null == terms) || (terms.length < 1)) {
            throw new IllegalArgumentException("No terms set.");
        }

        // create an empty model
        Model model = ModelFactory.createDefaultModel();

        // prepare terms as url-parameter
        String paramTerms = "";
        for (String term : terms) {
            try {
                paramTerms += URLEncoder.encode(term, "utf8") + "+";
            } catch (UnsupportedEncodingException e) {
                log.error("Error during encoding url parameter: ", e);
            }
        }
        paramTerms = paramTerms.substring(0, paramTerms.length() - 1);
        String query = getUrlByFilter(FilterType.ONLY_TERMS) + lang + "/similar.rdf?terms=" + paramTerms;

        if (log.isDebugEnabled()) {
            log.debug("Getting similar terms from: {}", query);
        }

        try {
            // read the RDF/XML file
            model.read(query);
        } catch (DoesNotExistException e) {
            log.error("The search-function does not exist: {}", query, e);
            return null;
        } catch (Exception e) {
            log.error("The URI seems to have a problem: {}", query, e);
            return null;
        }

        // write it to standard out
        if (log.isDebugEnabled()) {
            model.write(System.out);
        }

        return model.getResource(query + "#top");
    }

    /**
     * Set timeout in milliseconds for the SN-Service connection.
     *
     * @param timeout Timeout in milliseconds.
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
