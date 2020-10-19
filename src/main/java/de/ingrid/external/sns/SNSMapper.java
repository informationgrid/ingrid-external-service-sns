/*
 * **************************************************-
 * ingrid-external-service-sns
 * ==================================================
 * Copyright (C) 2014 - 2020 wemove digital solutions GmbH
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

import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceRequiredException;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import de.ingrid.external.om.Event;
import de.ingrid.external.om.FullClassifyResult;
import de.ingrid.external.om.IndexedDocument;
import de.ingrid.external.om.Link;
import de.ingrid.external.om.Location;
import de.ingrid.external.om.RelatedTerm;
import de.ingrid.external.om.RelatedTerm.RelationType;
import de.ingrid.external.om.Term;
import de.ingrid.external.om.Term.TermType;
import de.ingrid.external.om.TreeTerm;
import de.ingrid.external.om.impl.EventImpl;
import de.ingrid.external.om.impl.FullClassifyResultImpl;
import de.ingrid.external.om.impl.IndexedDocumentImpl;
import de.ingrid.external.om.impl.LinkImpl;
import de.ingrid.external.om.impl.LocationImpl;
import de.ingrid.external.om.impl.RelatedTermImpl;
import de.ingrid.external.om.impl.TermImpl;
import de.ingrid.external.om.impl.TreeTermImpl;

/**
 * Singleton encapsulating methods for mapping SNS data structures to API beans.
 */
public class SNSMapper {

	/** Direction of a hierarchy operation for mapping of results */
    public enum HierarchyDirection { DOWN, UP }

	private final static Logger log = Logger.getLogger(SNSMapper.class);
	private final static SimpleDateFormat expiredDateParser = new SimpleDateFormat("yyyy-MM-dd");

	private static SNSMapper myInstance;

    // Settings
	private String SNS_NATIVE_KEY_PREFIX;
	private ResourceBundle resourceMapper;
	
	private Pattern patternNumber = Pattern.compile("-?\\d+");


	/** Get The Singleton */
	public static synchronized SNSMapper getInstance(ResourceBundle resourceBundle) {
		if (myInstance == null) {
	        myInstance = new SNSMapper(resourceBundle);
	      }
		return myInstance;
	}

	private SNSMapper(ResourceBundle resourceBundle) {
		//this.resourceBundle = resourceBundle;
		this.resourceMapper = ResourceBundle.getBundle("mapping");
    	SNS_NATIVE_KEY_PREFIX = resourceBundle.getString("sns.nativeKeyPrefix");
	}
	
	
	
	public TreeTerm mapTreeTerm(Resource resource, String lang) {
        TreeTerm treeTerm = new TreeTermImpl();
        treeTerm.setId(RDFUtils.getId(resource));
        treeTerm.setName(RDFUtils.getName(resource, lang));
        treeTerm.setType(Term.TermType.DESCRIPTOR);
        addParentInfo(treeTerm, resource);
        
        // check for children (simple check)
        // needed to presentation ("plus"-sign in front of node)
        NodeIterator it = RDFUtils.getChildren(resource.getModel());
        while (it.hasNext()) {
            RDFNode node = it.next();
            TreeTerm child = new TreeTermImpl();
            child.setId(RDFUtils.getId(node.asResource()));
            treeTerm.addChild(child);
        }
        
        return treeTerm;
    }
	
	private void addParentInfo(TreeTerm treeTerm, Resource resource) {
        //RDFNode parentNode = RDFUtils.getParent(resource.getModel());
        //if (parentNode != null) {
            TreeTerm parentTreeTerm = new TreeTermImpl();
        //    parentTreeTerm.setId(parentNode.toString());
            treeTerm.addParent(parentTreeTerm);
        //}
        
    }
	
	
	/*
	 * ==========================================================
	 */

	

    /** Creates a Location list from the given topics.<br/>
     * @param topics sns topics representing locations
     * @param removeExpired if true checks topics whether expired and REMOVES expired ones
     * @param langFilter pass requested SNS language for mapping of title ... 
     * @return the locations NEVER NULL (but may be empty)
     */
    public List<Location> mapToLocationsFromResults(Resource topics, boolean removeExpired, String langFilter) {
    	List<Location> resultList = new ArrayList<Location>();
    	if (topics == null) return resultList;

    	NodeIterator it = RDFUtils.getResults(topics);
    	while (it.hasNext()) {
			RDFNode node = it.next();
			// TODO: location typeId not in searchResult
			// iterate over all exclusively fetched terms!?
			
			Location loc = mapToLocation(node.asResource(), new LocationImpl(), langFilter);
			if (removeExpired && !loc.getIsExpired())
				resultList.add(loc);
		}
        	
    	return resultList;    	
    }
    
    public List<Location> mapToLocationsFromLocation(Resource topics, boolean removeExpired, String langFilter) {
    	List<Location> resultList = new ArrayList<Location>();

    	StmtIterator it = RDFUtils.getRelatedConcepts(topics);
    	while (it.hasNext()) {
			Resource nodeRes = it.next().getResource();
			Location loc = mapToLocation(nodeRes, new LocationImpl(), langFilter);
			if (removeExpired && !loc.getIsExpired())
				resultList.add(loc);
		}
        	
    	return resultList;
    }

    /** Creates a Location from the given topic.<br/>
     * NOTICE: also checks whether location topic is expired and sets flag in Location !
     * @param topic sns topic representing location
     * @param outLocation the location the topic is mapped to, NEVER NULL 
     * @param langFilter pass requested SNS language for mapping of title ... 
     * @return again the outLocation after mapping, NEVER NULL
     */
    public Location mapToLocation(Resource topic, Location outLocation, String langFilter) {
    	return mapToLocation(topic, outLocation, true, langFilter);
    }

    /** Creates a Location from the given topic.<br/>
     * @param topic sns topic representing location
     * @param outLocation the location the topic is mapped to, NEVER NULL 
     * @param checkExpired if true checks topic whether expired (sets isExpired in location)
     * @param langFilter pass requested SNS language for mapping of title ... 
     * @return again the outLocation after mapping, NEVER NULL
     */
    private Location mapToLocation(Resource topic, Location outLocation, boolean checkExpired, String langFilter) {
    	outLocation.setId(RDFUtils.getId(topic));
    	outLocation.setName(RDFUtils.getName(topic, langFilter));
    	
    	// check for type name/id
    	String typeId = RDFUtils.getMemberOf(topic);
    	if (typeId != null) {
    		String id = typeId.substring(typeId.lastIndexOf('/')+1);
    		outLocation.setTypeId(id);

    		try {
    			String typeName = resourceMapper.getString("gazetteer."+langFilter+"."+id);
    			outLocation.setTypeName(typeName);
    		} catch (MissingResourceException e) {}
    	}
    	
    	// TODO: determine qualifier like "Stadt" in "<rdf:type rdf:resource="http://schema.org/City"/>" 
    	//outLocation.setQualifier();
    	
    	String nativeKey = RDFUtils.getNativeKey(topic, SNS_NATIVE_KEY_PREFIX);
    	if ( nativeKey != null ) {
    	    outLocation.setNativeKey( nativeKey );
    	// otherwise if it's not "Gemeinde"
    	} else if (!"-location-admin-use6-".equals( typeId )) {
    	    Matcher m = patternNumber.matcher( outLocation.getId() );
    	    String key = null;
    	    while (m.find()) key = m.group();
    	    outLocation.setNativeKey( key );
    	// otherwise log a warning that there was no native key for a "Gemeinde"
    	} else {
    	    log.warn( "No native key could be determined for: " + topic.getURI() );
    	}
        // in case we didn't find a key, we use the number from the identifier
    	
    	// check for bounding box
    	float[] points = RDFUtils.getBoundingBox(topic);
    	if (points != null) {
    		// if bounding box is a coordinate then use same coordinate again
    	    // FIXME: bounding box for points has wrong order (https://github.com/innoq/iqvoc_gazetteer/issues/14)
	    	if (points.length == 2)
	    		outLocation.setBoundingBox(points[1], points[0], points[1], points[0]);
	    	else if (points.length == 4)
	    		outLocation.setBoundingBox(points[0], points[1], points[2], points[3]);
    	}
    	
    	if (outLocation.getQualifier() == null)
    		outLocation.setQualifier(outLocation.getTypeName());
    	
    	// ALSO EXPIRED IF REQUESTED !
    	if (checkExpired) {
    		outLocation.setIsExpired(isExpired(topic));
    		outLocation.setExpiredDate(RDFUtils.getExpireDate(topic));
    	}
    	
    	// check if successor exists
    	outLocation.setSuccessorIds( RDFUtils.getSuccessors(topic));
    	
    	return outLocation;
    }

    /** Creates a Term list from the given topics.<br/>
     * @param topics sns topics representing terms
     * @param filter only use topics matching the given type, remove the other ones ! Pass null if all types
     * @param langFilter pass requested SNS language for mapping of title ... 
     * @return the terms NEVER NULL
     */
    public List<Term> mapToTerms(Resource searchResults, TermType filter, String langFilter) {
    	List<Term> resultList = new ArrayList<Term>();

    	if ((null != searchResults)) {
    		NodeIterator it = RDFUtils.getResults(searchResults);
    		
    		while (it.hasNext()) {
    			RDFNode node = it.next();
            	if (filter != null) {
            		if (!getTermType(RDFUtils.getType( node.asResource() ), false).equals(filter)) {
            			continue;
            		}
            	}
    			resultList.add(mapToTerm(node.asResource(), new TermImpl(), langFilter));
    		}
        }
    	
    	return resultList;
    }

    /** Creates a Term from the given topic.<br/>
     * @param inTopic sns topic representing a term
     * @param outTerm the term the topic is mapped to, NEVER NULL 
     * @param langFilter pass requested SNS language for mapping of title ... 
     * @return again the outTerm after mapping, NEVER NULL
     */
    public Term mapToTerm(Resource res, Term outTerm, String langFilter) {
    	// if the term is inside a search result the id is inside a link-tag
		outTerm.setId(RDFUtils.getId(res));
		String name = RDFUtils.getName(res, langFilter);
		if (name == null) {
		    // get first alternative Label
		    List<String> altLabels = RDFUtils.getAltLabels(res, langFilter);
		    if (altLabels.size() > 0) {
		        name = altLabels.get(0);
		    }
		}
		outTerm.setName(name);

		boolean isTopTerm = RDFUtils.isTopConcept( res );
		outTerm.setType( getTermType( RDFUtils.getType( res ), isTopTerm ) );

		//outTerm.setInspireThemes(getInspireThemes(inTopic));

		String gemet = RDFUtils.getGemetRef(res);
    	if (gemet != null) {
    		// if GEMET, then the title is used for the title in SNSTopic and, in case UMTHES is different
    		// the UMTHES value is stored in alternateTitle
    		outTerm.setAlternateName(outTerm.getName());
    		//TODO outTerm.setName(getGemetName(inTopic));
    		outTerm.setAlternateId(getGemetId(gemet));
    	}

    	return outTerm;
    }
    
    public List<Term> mapSimilarToTerms(Resource searchResults, String langFilter) {
    	List<Term> resultList = new ArrayList<Term>();

    	if ((null != searchResults)) {
    		List<String> labels = RDFUtils.getAltLabels(searchResults, langFilter);
    		
    		for (String label : labels) {
    			// since synonyms do not have an extra ID we just use the label as identifier
    			// necessary, because otherwise the iBus throwse other terms away when copying array
				Term t = new TermImpl(label, label, Term.TermType.DESCRIPTOR);
    			resultList.add(t);
    		}
        }
    	
    	return resultList;
    }

    /** Creates a RelatedTerm list from the given TopicMapFragment (result of relation operation).<br/>
     * @param fromTopicId id of the topic to get related terms for
     * @param mapFragment sns result of relation operation (getPSI)
     * @param langFilter pass requested SNS language for mapping of title ... 
     * @return the related terms NEVER NULL
     */
    public List<RelatedTerm> mapToRelatedTerms(String fromTopicId,
    		Resource res,
    		String langFilter) {
    	List<RelatedTerm> resultList = new ArrayList<RelatedTerm>();

    	// get all synonyms
    	List<String> altLabels = RDFUtils.getAltLabels(res, langFilter);
    	resultList.addAll(mapSynonymsFromAltLabels(altLabels, res.getURI()));
    	
    	// get all parents
    	StmtIterator iterator = RDFUtils.getParents(res);
    	resultList.addAll(mapResourceToRelatedTerm(iterator, langFilter, RelationType.PARENT, TermType.DESCRIPTOR));
    	
    	// get all children
    	iterator = RDFUtils.getChildren(res);
    	resultList.addAll(mapResourceToRelatedTerm(iterator, langFilter, RelationType.CHILD, TermType.DESCRIPTOR));
    	
    	// get all related concepts
    	iterator = RDFUtils.getRelatedConcepts(res);
    	resultList.addAll(mapResourceToRelatedTerm(iterator, langFilter, RelationType.RELATIVE, TermType.DESCRIPTOR));    	

    	return resultList;
    }
    
    private List<RelatedTerm> mapSynonymsFromAltLabels(List<String> altLabels, String id) {
    	List<RelatedTerm> result = new ArrayList<RelatedTerm>();
    	for (String altLabel : altLabels) {
    		RelatedTerm rt = new RelatedTermImpl();
    		rt.setId(id);
    		rt.setName(altLabel);
    		rt.setRelationType(RelationType.RELATIVE);
    		rt.setType(TermType.NON_DESCRIPTOR);
			result.add(rt);
		}
		return result;
	}
    
    private List<RelatedTerm> mapResourceToRelatedTerm(StmtIterator resourceIt, String lang, RelationType relType, TermType termType) {
    	List<RelatedTerm> result = new ArrayList<RelatedTerm>();
    	while (resourceIt.hasNext()) {
    		Statement stmt = resourceIt.next();
    		RelatedTerm rt = new RelatedTermImpl();
    		rt.setId(RDFUtils.getId(stmt.getResource()));
    		rt.setName(RDFUtils.getName(stmt.getResource(), lang));
    		rt.setRelationType(relType);
    		rt.setType(termType);
			result.add(rt);
		}
		return result;
	}

	public List<TreeTerm> mapRootToTreeTerms(String fromTopicId,
    		HierarchyDirection whichDirection,
    		Resource termResource,
    		String langFilter) {
    	
    	List<TreeTerm> resultList = new ArrayList<TreeTerm>();
    	ResIterator children = RDFUtils.getTopConceptsOf(termResource.getModel());
		while (children.hasNext()) {
			TreeTerm treeTerm = new TreeTermImpl();
			RDFNode child = (RDFNode) children.next();
			treeTerm.setId(RDFUtils.getId(child.asResource()));
			treeTerm.setName(RDFUtils.getName(child.asResource(), langFilter));
			treeTerm.setType(Term.TermType.DESCRIPTOR);
			
			resultList.add(treeTerm);
			
			// check for children (simple check)
			// needed to presentation ("plus"-sign in front of node)
			/*StmtIterator it = RDFUtils.getChildren(child.asResource());
			while (it.hasNext()) {
				Statement node = it.next();
				TreeTerm subChild = new TreeTermImpl();
				subChild.setId(RDFUtils.getId(node.getResource()));
				treeTerm.addChild(subChild);
			}*/
			// always add a (dummy) child and assume the root nodes have children
			TreeTerm dummyChild = new TreeTermImpl();
			dummyChild.setId( "dummy" );
			treeTerm.addChild( dummyChild );
			
		}
		return resultList;
    }

    /** Creates a hierarchy list of tree terms dependent from hierarchy operation.<br/>
     * @param fromTopicId id of starting topic ! PASS NULL IF ALL TOP TERMS WERE REQUESTED! 
     * @param whichDirection in which direction was the hierarchy operation performed
     * @param mapFragment sns result of hierarchy operation (getHierachy)
     * @param langFilter Only deliver results of this language. We need additional filtering
     * 		cause SNS delivers results of different languages from first request. Pass NULL
     * 		if all languages ! 
     * @return the according tree terms NEVER NULL
     */
    public List<TreeTerm> mapToTreeTerms(String fromTopicId,
    		HierarchyDirection whichDirection,
    		Resource termResource,
    		String langFilter) {
    	
    	List<TreeTerm> resultList = new ArrayList<TreeTerm>();
    	if (whichDirection == HierarchyDirection.DOWN) {
			StmtIterator children = RDFUtils.getChildren(termResource);
			while (children.hasNext()) {
				TreeTerm treeTerm = new TreeTermImpl();
				Statement child = children.next();
				treeTerm.setId(RDFUtils.getId(child.getResource()));
				treeTerm.setName(RDFUtils.getName(child.getResource(), langFilter));
				treeTerm.setType(Term.TermType.DESCRIPTOR);
				
				// needed to determine that it's not a top-term!
				TreeTerm term = new TreeTermImpl();
				term.setId(RDFUtils.getId(termResource));
				term.setName(RDFUtils.getName(termResource, langFilter));
				treeTerm.addParent(term);
				
				resultList.add(treeTerm);
				
				// check for children (simple check)
				// needed to presentation ("plus"-sign in front of node)
				StmtIterator it = RDFUtils.getChildren(child.getResource());
				while (it.hasNext()) {
					Statement node = it.next();
					TreeTerm subChild = new TreeTermImpl();
					subChild.setId(RDFUtils.getId(node.getResource()));
					subChild.setName(RDFUtils.getName(node.getResource(), langFilter));
					treeTerm.addChild(subChild);
				}
			}
    	} else {
    		// set start term
    		TreeTerm term = new TreeTermImpl();
			term.setId(RDFUtils.getId(termResource));
			term.setName(RDFUtils.getName(termResource, langFilter));
			term.setType(Term.TermType.DESCRIPTOR);
			resultList.add(term);
			getAllParentsFrom(term, termResource, langFilter);
    	}
		return resultList;
    }
    
    private TreeTerm[] getAllParentsFrom(TreeTerm term, Resource res, String lang) {
    	List<TreeTerm> terms = new ArrayList<TreeTerm>();
    	
    	StmtIterator parents = RDFUtils.getParents(res);
    	while (parents.hasNext()) {
    		Statement parent = parents.next();
    		TreeTerm parentTerm = new TreeTermImpl();
    		parentTerm.setId(RDFUtils.getId(parent.getResource()));
    		parentTerm.setName(RDFUtils.getName(parent.getResource(), lang));
    		parentTerm.setType(Term.TermType.DESCRIPTOR);
    		parentTerm.addChild(term);
    		Resource grandParentRes = parent.getModel().getResource(parentTerm.getId());
    		// recursive call!
    		getAllParentsFrom(parentTerm, grandParentRes, lang);
    		term.addParent(parentTerm);
    		terms.add(parentTerm);    		
    	}
		return terms.toArray(new TreeTerm[terms.size()]);
    }
    

    /** Creates an Event list from the given topics.<br/>
     * @param topics sns topics representing events
     * @return the events NEVER NULL
     */
    public List<Event> mapToEvents(Resource eventsRes, String lang) {
    	List<Event> resultList = new ArrayList<Event>();
    	if (eventsRes == null) return resultList;

    	NodeIterator it = RDFUtils.getResults(eventsRes);
    	while (it.hasNext()) {
    		RDFNode eventNode = it.next();
    		resultList.add(mapToEvent(eventNode.asResource(), lang));
    	}
		
    	
    	return resultList;
    }

    /** Creates an Event from the given topic.<br/>
     * @param eventRes sns topic representing an event
     * @return the event, NEVER NULL
     */
    public Event mapToEvent(Resource eventRes, String lang) {
    	EventImpl result = new EventImpl();
    	
    	result.setId(RDFUtils.getId(eventRes));
    	result.setTitle(RDFUtils.getName(eventRes, lang));
    	
    	String typeUrl = RDFUtils.getMemberOf(eventRes);
    	// some events do not seem to have a category
    	// e.g.: https://sns.uba.de/chronik/de/concepts/_42ea37f4.html
    	if (typeUrl != null) {
        	String id = typeUrl.substring(typeUrl.lastIndexOf('/')+1);
        	result.setTypeId(id);
    	}
    	
    	result.setDescription(RDFUtils.getDefinition(eventRes, lang));
    	result.setTimeAt(convertToDate(RDFUtils.getDateStart(eventRes)));
    	result.setTimeRangeFrom(result.getTimeAt());
    	result.setTimeRangeTo(convertToDate(RDFUtils.getDateEnd(eventRes)));
    	
    	StmtIterator moreInfo = RDFUtils.getFurtherInfo(eventRes);
    	while (moreInfo.hasNext()) {
    	    Statement nextInfo = moreInfo.next();
    	    try {
        		Link l = new LinkImpl();
        		Resource info = nextInfo.getResource();
        		l.setTitle(RDFUtils.getDctTitle(info));
        		l.setLinkAddress(RDFUtils.getDctPage(info));
        		result.addLink(l);
    	    } catch (ResourceRequiredException ex) {
    	        log.error( "Resource could not be extracted from 'seeAlso'-field, which contains: " + nextInfo.getString(), ex );
    	    }
    	}
    	
    	return result;

    }

    /** Creates a FullClassifyResult from the given mapFragment
     * @param resThesaurus result of fullClassify as delivered by Thesaurus-SNS
     * @param resGazetteer result of fullClassify as delivered by Gazetteer-SNS
     * @param resChronical result of fullClassify as delivered by Chronical-SNS
     * @param langFilter pass requested SNS language for mapping of title ... 
     * @return the FullClassifyResult NEVER NULL
     */
    public FullClassifyResult mapToFullClassifyResult(Resource resThesaurus, Resource resGazetteer, Resource resChronical, String langFilter) {
    	FullClassifyResultImpl result = new FullClassifyResultImpl();
    	
    	//result.setIndexedDocument(mapToIndexedDocument(resThesaurus));
    	result.setLocations(mapToLocationsFromResults(resGazetteer, true, langFilter));
    	result.setTerms(mapToTerms(resThesaurus, null, langFilter));
    	result.setEvents(mapToEvents(resChronical, langFilter));
    		    // do not add any locations since those should come from the WFS Service
    		    // which does not support autoClassify! (REDMINE-551)
    			// locTopics.add(topic);

    	return result;
    }

    /** Creates a IndexedDocument from the given TopicMapFragmentIndexedDocument
     * @param inDoc result of fullClassify as delivered by SNS
     * @return the IndexedDocument NEVER NULL
     */
    public IndexedDocument mapToIndexedDocument(String inDoc, URL url) {
    	IndexedDocumentImpl result = new IndexedDocumentImpl();

    	result.setClassifyTimeStamp(new Date());
    	result.setTitle(HtmlUtils.getHtmlTagContent(inDoc, "title"));
    	result.setDescription(HtmlUtils.getHtmlMetaTagContent(inDoc, "description"));
    	result.setURL(url);
    	
    	String lang = HtmlUtils.getHtmlDocLanguage(inDoc);
		try {
        	result.setLang(new Locale(lang));	
		} catch (Exception e) {
	    	log.warn("Error mapping Lang: " + lang, e);
	    	result.setLang(new Locale("de"));
		}
    	/*

    	result.setTimeAt(convertToDate(inDoc.getAt()));
    	result.setTimeFrom(convertToDate(inDoc.getFrom()));
    	result.setTimeTo(convertToDate(inDoc.getTo()));
    	 */
    	return result;
    }
    
    private TermType getTermType(String nodeType, boolean isTopTerm) {
		/*if (isTopTerm) 
			return TermType.NODE_LABEL;
		else */
        if (nodeType.indexOf("#Label") != -1)
			return TermType.NODE_LABEL;
		else if (nodeType.indexOf("#Concept") != -1)
			return TermType.DESCRIPTOR;
		else if (nodeType.indexOf("#Result") != -1)
		    return TermType.DESCRIPTOR;
		else
			return TermType.NODE_LABEL;
    }
    

    /**
     * Determine whether the given association is a relation of the passed from-term
     * to another term (to-term) and get basic data of the relation encapsulated in a
     * RelationTerm (id of to-term, type of relation)
     * @param fromTermId the id of the from-term the association was determined from 
     * @param assoc an SNS association of the from-term
     * @return the found related term OR NULL (if no association to a term)
     */
    /*
    private RelatedTerm getRelatedTermBasics(String fromTermId, Association assoc) {
    	RelatedTerm result = null;
    	
        final String assocType = assoc.getInstanceOf().getTopicRef().getHref();
        boolean isHierarchyRelation = assocType.endsWith("narrowerTermAssoc");

        if (isHierarchyRelation ||
        		assocType.endsWith("relatedTermsAssoc") ||
        		assocType.endsWith("synonymAssoc")) {
        	// we have a relation between terms !
        	result = new RelatedTermImpl();

        	// determine target term id and relation details !
            final Member[] members = assoc.getMember();
            for (Member member : members) {
                String toTermId = member.getTopicRef()[0].getHref();
                if (!toTermId.equals(fromTermId)) {
                    // here is only the topic id available
                	result.setId(toTermId);
                	// default relation ! TermType determines whether synonym or descriptor !  
                	result.setRelationType(RelationType.RELATIVE);
                	
                	// if hierarchy relation determine whether parent or child !  
                	if (isHierarchyRelation) {
                        final String assocMember = member.getRoleSpec().getTopicRef().getHref();
                        if (assocMember.endsWith("widerTermMember")) {
                        	result.setRelationType(RelationType.PARENT);
                        } else {
                        	result.setRelationType(RelationType.CHILD);
                        }
                	}
                }
            }
        }

        return result;
    }
	*/

    private boolean isExpired(Resource topic) {
        Date expDate = null;
        
        String date = RDFUtils.getExpireDate(topic);
        if (date != null) {
	        try {
	            expDate = expiredDateParser.parse(date);
	        } catch (ParseException e) {
	            log.error("Not expected date format in sns expiredOcc.", e);
	        }
        }
        
        boolean isExpired = false;
        if (expDate != null) {
        	isExpired = expDate.before(new Date());
        }

        return isExpired;
    }

    /**
     * Extract the id from a gemet url and return it according to 
     * style: GEMETID9242 from
     * e.g. http://www.eionet.europa.eu/gemet/concept/9242 
     * @param gemetUrl
     * @return
     */
    private String getGemetId(String gemetUrl) {
    	int pos = gemetUrl.lastIndexOf('/') + 1;
    	return "GEMETID" + gemetUrl.substring(pos);
    }

    private Date convertToDate(String dateString) {
    	if (dateString == null) {
    		return null;
    	}

    	Date result = null;

		SimpleDateFormat[] dateFormats = new SimpleDateFormat[] {
				new SimpleDateFormat("yyyy-MM-dd"),
				new SimpleDateFormat("yyyy-MM"),
				new SimpleDateFormat("yyyy")
		};

		for (SimpleDateFormat df : dateFormats) {
	    	try {
	    		result = df.parse(dateString);
	    	}
	    	catch (java.text.ParseException pe) {
	    		//log.debug(pe);
	    	}

	    	if (result != null) {
	    		break;
	    	}
		}

    	if (result == null) {
        	log.error("Error parsing date: "+dateString);    		
    	}

    	return result;
    }

	public List<Event> mapToAnniversaries(Resource eventsRes, String langFilter) {
		List<Event> events = new ArrayList<Event>();
		ResIterator it = RDFUtils.getConcepts(eventsRes.getModel());
    	while (it.hasNext()) {
    		Resource eventRes = it.next();
    		events.add(mapToEvent(eventRes, langFilter));
    	}
		return events;
	}
}
