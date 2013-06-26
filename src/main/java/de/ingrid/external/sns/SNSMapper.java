package de.ingrid.external.sns;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
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
	//private final static SimpleDateFormat expiredDateParser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
	private final static SimpleDateFormat expiredDateParser = new SimpleDateFormat("yyyy-MM-dd");

	private static SNSMapper myInstance;

    // Settings
    private ResourceBundle resourceBundle; 
	private String SNS_NATIVE_KEY_PREFIX;
	private ResourceBundle resourceMapper;

    /** The three main SNS topic types */
    private enum TopicType {EVENT, LOCATION, THESA}


	/** Get The Singleton */
	public static synchronized SNSMapper getInstance(ResourceBundle resourceBundle) {
		if (myInstance == null) {
	        myInstance = new SNSMapper(resourceBundle);
	      }
		return myInstance;
	}

	private SNSMapper(ResourceBundle resourceBundle) {
		this.resourceBundle = resourceBundle;
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
			
			resultList.add(mapToLocation(node.asResource(), new LocationImpl(), langFilter));
		}
        	
    	return resultList;
    	
    	/*if ((null != topics)) {
    		boolean checkExpired = !removeExpired;
            for (Topic topic : topics) {
            	if (removeExpired && isExpired(topic)) {
            		continue;
            	}

            	// TODO: implement
        		resultList.add(mapToLocation(topic, new LocationImpl(), checkExpired, langFilter));
			}
        }*/
    	
    }
    
    public List<Location> mapToLocationsFromLocation(Resource topics, boolean removeExpired, String langFilter) {
    	List<Location> resultList = new ArrayList<Location>();

    	StmtIterator it = RDFUtils.getRelatedConcepts(topics);
    	while (it.hasNext()) {
			Resource nodeRes = it.next().getResource();
			resultList.add(mapToLocation(nodeRes, new LocationImpl(), langFilter));
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
    		outLocation.setTypeId(typeId);
    		String id = typeId.substring(typeId.lastIndexOf('/')+1);
    		try {
    			String typeName = resourceMapper.getString("gazetteer."+langFilter+"."+id);
    			outLocation.setTypeName(typeName);
    		} catch (MissingResourceException e) {}
    	}
    	
    	// TODO: determine qualifier like "Stadt" in "<rdf:type rdf:resource="http://schema.org/City"/>" 
    	//outLocation.setQualifier();
    	
    	// TODO: determine native key like "06412000" in 
    	// <skos:notation rdf:datatype="http://iqvoc-gazetteer.innoq.com/agsNotation" xml:lang="none">06412000</skos:notation>
        // <skos:notation rdf:datatype="http://iqvoc-gazetteer.innoq.com/rsNotation" xml:lang="none">064120000000</skos:notation>
    	//outLocation.setNativeKey();
    	
    	// check for bounding box
    	// TODO: what if more than one bounding box?
    	float[] points = RDFUtils.getBoundingBox(topic);
    	if (points != null) {
    		// if bounding box is a coordinate then use same coordinate again
	    	if (points.length == 2)
	    		outLocation.setBoundingBox(points[0], points[1], points[0], points[1]);
	    	else if (points.length == 4)
	    		outLocation.setBoundingBox(points[0], points[1], points[2], points[3]);
    	}
    	
    	// ALSO EXPIRED IF REQUESTED !
    	if (checkExpired) {
    		// TODO: check expired!
    		outLocation.setIsExpired(isExpired(topic));
    	}
    	
    	
    	/*String typeId = topic.getInstanceOf(0).getTopicRef().getHref();
    	typeId = typeId.substring(typeId.lastIndexOf("#")+1);
    	outLocation.setTypeId(typeId);
    	String typeName = typeId;
    	try {
    		typeName = resourceBundle.getString("sns.topic.ref."+typeId);
    	} catch (MissingResourceException ex) {}*/
    	//outLocation.setTypeName(typeName);

    	// If the topic doesn't contain any more information return the basic info
    	/*
    	if (topic.getOccurrence() != null) {
        	// Iterate over all occurrences and extract the relevant information (bounding box wgs84 coords and the qualifier)
        	for(int i = 0; i < topic.getOccurrence().length; ++i) {
//        		log.debug(topic.getOccurrence(i).getInstanceOf().getTopicRef().getHref());
        		if (topic.getOccurrence(i).getInstanceOf().getTopicRef().getHref().endsWith("wgs84BoxOcc")) {
//        			log.debug("WGS84 Coordinates: "+topic.getOccurrence(i).getResourceData().get_value());        	            			
            		String coords = topic.getOccurrence(i).getResourceData().get_value();
            		String[] ar = coords.split("\\s|,");
            		if (ar.length == 4) {
            			outLocation.setBoundingBox(new Float(ar[0]), new Float(ar[1]), new Float(ar[2]), new Float(ar[3]));
            		}
        		} else if (topic.getOccurrence(i).getInstanceOf().getTopicRef().getHref().endsWith("qualifier")) {
//        			log.debug("Qualifier: "+topic.getOccurrence(i).getResourceData().get_value());        	            			
            		outLocation.setQualifier(topic.getOccurrence(i).getResourceData().get_value());
        		} else if (topic.getOccurrence(i).getInstanceOf().getTopicRef().getHref().endsWith("nativeKeyOcc")) {
        			String nativeKeyOcc = topic.getOccurrence(i).getResourceData().get_value();
        			String[] keys = nativeKeyOcc.split(" ");
        			for (String nativeKey : keys) {
        				if (nativeKey.startsWith(SNS_NATIVE_KEY_PREFIX)) {
        					outLocation.setNativeKey(nativeKey.substring(SNS_NATIVE_KEY_PREFIX.length()));
        				}
        			}
        		}
        	}

        	if (outLocation.getQualifier() == null)
        		outLocation.setQualifier(outLocation.getTypeName());
        	
        	// ALSO EXPIRED IF REQUESTED !
        	if (checkExpired) {
        		outLocation.setIsExpired(isExpired(topic));
        	}
    	}*/

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
            		if (!getTermType(node.asResource()).equals(filter)) {
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
		if (name != null) {
			outTerm.setName(name);
			outTerm.setType(TermType.DESCRIPTOR);			
		} else {
			// get first alternative Label
			List<String> altLabels = RDFUtils.getAltLabels(res, langFilter);
			if (altLabels.size() > 0) {
				name = altLabels.get(0);
				outTerm.setName(name);
			}
			// since this is only an alternative Label we declare it as a Non-Descriptor
			outTerm.setType(TermType.NON_DESCRIPTOR);			
		}

		//outTerm.setInspireThemes(getInspireThemes(inTopic));

		String gemet = RDFUtils.getGemetRef(res);
    	if (gemet != null) {
    		// if GEMET, then the title is used for the title in SNSTopic and, in case UMTHES is different
    		// the UMTHES value is stored in alternateTitle
    		outTerm.setAlternateName(outTerm.getName());
    		//outTerm.setName(getGemetName(inTopic));
    		outTerm.setAlternateId(getGemetId(gemet));
    	}

    	return outTerm;
    }
    
    public List<Term> mapSimilarToTerms(Resource searchResults, String langFilter) {
    	List<Term> resultList = new ArrayList<Term>();

    	if ((null != searchResults)) {
    		List<String> labels = RDFUtils.getAltLabels(searchResults, langFilter);
    		
    		for (String label : labels) {
				Term t = new TermImpl("???", label, Term.TermType.DESCRIPTOR);
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
    	
        /*final Topic[] topics = getTopics(mapFragment);
        final Association[] associations = getAssociations(mapFragment);

        // iterate through associations to find the correct associations !
        if (associations != null) {
            for (Association association : associations) {
                RelatedTerm relTerm = getRelatedTermBasics(fromTopicId, association);
                if (relTerm != null) {
                	final Topic foundTopic = getTopicById(topics, relTerm.getId(), false);
                	if (foundTopic != null) {
                    	resultList.add((RelatedTerm)mapToTerm(foundTopic, relTerm, langFilter));                		
                	}
                }
            }
        }
		*/
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
		//for (ModelWrapper parent : parents) {
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
				StmtIterator it = RDFUtils.getChildren(child.asResource());
				while (it.hasNext()) {
					Statement node = it.next();
					TreeTerm subChild = new TreeTermImpl();
					subChild.setId(RDFUtils.getId(node.getResource()));
					treeTerm.addChild(subChild);
				}
			}
		//}
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
		//for (ModelWrapper parent : parents) {
    	if (whichDirection == HierarchyDirection.DOWN) {
			StmtIterator children = RDFUtils.getChildren(termResource);
			while (children.hasNext()) {
				TreeTerm treeTerm = new TreeTermImpl();
				Statement child = children.next();
				treeTerm.setId(RDFUtils.getId(child.getResource()));
				//Resource childResource = termResource.getModel().getResource(identifier);
				treeTerm.setName(RDFUtils.getName(child.getResource(), langFilter));
				treeTerm.setType(Term.TermType.DESCRIPTOR);
				
				// needed to determine that it's not a top-term!
				TreeTerm term = new TreeTermImpl();
				term.setId(RDFUtils.getId(termResource));
				treeTerm.addParent(term);
				
				resultList.add(treeTerm);
				
				// check for children (simple check)
				// needed to presentation ("plus"-sign in front of node)
				StmtIterator it = RDFUtils.getChildren(child.getResource());
				while (it.hasNext()) {
					Statement node = it.next();
					TreeTerm subChild = new TreeTermImpl();
					subChild.setId(RDFUtils.getId(node.getResource()));
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
			RDFNode parent = RDFUtils.getParent(termResource);
			while (parent != null) {
				TreeTerm parentTerm = new TreeTermImpl();
				parentTerm.setId(RDFUtils.getId(parent.asResource()));
				parentTerm.setName(RDFUtils.getName(parent.asResource(), langFilter));
				parentTerm.setType(Term.TermType.DESCRIPTOR);
				parentTerm.addChild(term);
				term.addParent(parentTerm);
				
				parent = RDFUtils.getParent(termResource.getModel().getResource(parentTerm.getId()));
				term = parentTerm;
			}
    		
    	}
		return resultList;
    	
        /*final Topic[] topics = getTopics(mapFragment);
        final Association[] associations = getAssociations(mapFragment);

        // iterate through associations and set up all according TreeTerms !
        
        // all TreeTerrms are stored in Map
        Map<String, TreeTerm> treeTermMap = new HashMap<String, TreeTerm>();
        // ids of top terms are stored in list 
        List<String> topTermIdsList = new ArrayList<String>();

        if (associations != null) {
            for (Association assoc : associations) {

            	// determine parent and child from association
                Topic parentTopic = null;
                Topic childTopic = null;
                final Member[] members = assoc.getMember();
                for (Member member : members) {
                	final Topic foundTopic =
                		getTopicById(topics, member.getTopicRef()[0].getHref(), false);
                	
                	// check additional filtering of language if requested !
                	if (langFilter != null) {
                		if (!isLanguage(foundTopic, langFilter)) {
                			continue;
                		}
                	}

                    final String assocMember = member.getRoleSpec().getTopicRef().getHref();
                    if (assocMember.endsWith("#narrowerTermMember")) {
                        childTopic = foundTopic;
                    } else if (assocMember.endsWith("#widerTermMember")) {
                        parentTopic = foundTopic;
                    }
                }

                // set up according tree terms !
                if ((null != parentTopic) && (null != childTopic)) {
                    TreeTerm parentTreeTerm = null;
                    if (treeTermMap.containsKey(parentTopic.getId())) {
                        parentTreeTerm = treeTermMap.get(parentTopic.getId());
                    } else {
                    	parentTreeTerm = (TreeTerm) mapToTerm(parentTopic, new TreeTermImpl(), langFilter);
                        treeTermMap.put(parentTopic.getId(), parentTreeTerm);
                    }
                    TreeTerm childTreeTerm = null;
                    if (treeTermMap.containsKey(childTopic.getId())) {
                    	childTreeTerm = treeTermMap.get(childTopic.getId());
                    } else {
                    	childTreeTerm = (TreeTerm) mapToTerm(childTopic, new TreeTermImpl(), langFilter);
                        treeTermMap.put(childTopic.getId(), childTreeTerm);
                    }

                    // set up parent child relation in TreeTerms
                    parentTreeTerm.addChild(childTreeTerm);
                    childTreeTerm.addParent(parentTreeTerm);
                    
                    // remember top nodes if top nodes were requested !
                    if (fromTopicId == null &&
                    		parentTopic.getInstanceOf(0).getTopicRef().getHref().endsWith("#topTermType")) {
                    	if (!topTermIdsList.contains(parentTreeTerm.getId())) {
                    		topTermIdsList.add(parentTreeTerm.getId());	
                    	}
                    }
                }
            }
        } else {
        	// NO ASSOCIATIONS ! But we may have a topic !
        	// e.g. when TOP TERM and hierarchy is UP or leaf term and hierarchy is DOWN !
        	// we just map all topics with NO parent or child data
        	if (topics != null) {
        		for (Topic topic : topics) {
                    treeTermMap.put(topic.getId(), (TreeTerm) mapToTerm(topic, new TreeTermImpl(), langFilter));
        		}
        	}
        }

        // set up return list dependent from request
        List<TreeTerm> resultList = new ArrayList<TreeTerm>();
        
        if (fromTopicId == null) {
            // top terms request
        	for (String topTermId : topTermIdsList) {
        		resultList.add(treeTermMap.get(topTermId));
        	}
        } else {
        	// hierarchy request        	
        	// fetch start term
        	TreeTerm startTerm = treeTermMap.get(fromTopicId);
        	if (startTerm != null) {
            	if (whichDirection == HierarchyDirection.DOWN) {
            		// DOWN ! startTerm isn't part of list. If leaf then no children !
            		if (startTerm.getChildren() != null) {
                		for (Term childTerm : startTerm.getChildren()) {
                			resultList.add(treeTermMap.get(childTerm.getId()));
                		}            			
            		}
            	} else {
            		// UP ! startTerm is first term in list.
            		resultList.add(startTerm);
            	}
        	}
        }
        
    	return resultList;*/
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
    	String id = typeUrl.substring(typeUrl.lastIndexOf('/')+1);
    	result.setTypeId(id);
    	
    	result.setDescription(RDFUtils.getDefinition(eventRes, lang));
    	result.setTimeAt(convertToDate(RDFUtils.getDateStart(eventRes)));
    	result.setTimeRangeFrom(result.getTimeAt());
    	result.setTimeRangeTo(convertToDate(RDFUtils.getDateEnd(eventRes)));
    	
    	StmtIterator moreInfo = RDFUtils.getFurtherInfo(eventRes);
    	while (moreInfo.hasNext()) {
    		Link l = new LinkImpl();
    		Resource info = moreInfo.next().getResource();
    		l.setTitle(RDFUtils.getDctTitle(info));
    		l.setLinkAddress(RDFUtils.getDctPage(info));
    		result.addLink(l);
    	}
    	
    	return result;
    	/*

    	for (Occurrence occ: topic.getOccurrence()) {
    		if (occ.getInstanceOf().getTopicRef().getHref().endsWith("descriptionOcc")) {
    			if (occ.getScope().getTopicRef()[0].getHref().endsWith("de") && occ.getResourceData() != null)
    				result.setDescription(occ.getResourceData().get_value());

    		} else if (occ.getInstanceOf().getTopicRef().getHref().endsWith("temporalAtOcc")) {        		
//    			log.debug("Temporal at: "+occ.getResourceData().get_value());

    		} else if (occ.getInstanceOf().getTopicRef().getHref().endsWith("temporalFromOcc")) {        		
//    			log.debug("Temporal from: "+occ.getResourceData().get_value());

    		} else if (occ.getInstanceOf().getTopicRef().getHref().endsWith("temporalToOcc")) {        		
//    			log.debug("Temporal to: "+occ.getResourceData().get_value());
        	}
    	}

    	return result;*/
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
    	
    	result.setIndexedDocument(mapToIndexedDocument(resThesaurus));
    	result.setLocations(mapToLocationsFromResults(resGazetteer, true, langFilter));
    	result.setTerms(mapToTerms(resThesaurus, null, langFilter));
    	result.setEvents(mapToEvents(resChronical, langFilter));

    	return result;
    }

    /** Creates a IndexedDocument from the given TopicMapFragmentIndexedDocument
     * @param inDoc result of fullClassify as delivered by SNS
     * @return the IndexedDocument NEVER NULL
     */
    private IndexedDocument mapToIndexedDocument(Resource inDoc) {
    	IndexedDocumentImpl result = new IndexedDocumentImpl();

    	// TODO: implement
    	/*
    	result.setClassifyTimeStamp(inDoc.getTimestamp());
    	result.setTitle(inDoc.getTitle());
    	result.setDescription(inDoc.get_abstract());
    	if (inDoc.getUri() != null) {
    		try {
            	result.setURL(new URL(inDoc.getUri()));
    		} catch (Exception e) {
    	    	log.warn("Error mapping URI: " + inDoc.getUri(), e);
    		}
    	}
    	if (inDoc.getLang() != null) {
    		try {
            	result.setLang(new Locale(inDoc.getLang()));    			
    		} catch (Exception e) {
    	    	log.warn("Error mapping Lang: " + inDoc.getLang(), e);
    		}
    	}

    	result.setTimeAt(convertToDate(inDoc.getAt()));
    	result.setTimeFrom(convertToDate(inDoc.getFrom()));
    	result.setTimeTo(convertToDate(inDoc.getTo()));*/

    	return result;
    }

	/** Get topics from fragment
	 * @param mapFragment sns result
	 * @return the topics OR NULL
	 */
    /*
	public Topic[] getTopics(TopicMapFragment mapFragment) {
		Topic[] topics = null;
	    if (null != mapFragment) {
	    	topics = mapFragment.getTopicMap().getTopic();
	    }
	    return topics;
	}
	*/
	
    /** Return topic with given id from topic list.
     * @param topics list of topics
     * @param topicId id of topic to extract from list
     * @param removeExpired if true checks whether topic is expired and returns null if so !
     * @return the found topic or NULL if topic not found or is expired (if requested)
     */
    /*
    private Topic getTopicById(Topic[] topics, String topicId, boolean removeExpired) {
    	// determine topic from topic list
        for (Topic topic : topics) {
            if (topicId.equals(topic.getId())) {
            	// topic found, check expired if requested
            	if (!removeExpired || !isExpired(topic)) {
                	return topic;
            	}
            }
        }

        return null;
    }
    */

	/** Get associations from fragment
	 * @param mapFragment sns result
	 * @return the associations OR NULL
	 *//*
	private Association[] getAssociations(TopicMapFragment mapFragment) {
		Association[] associations = null;
	    if (null != mapFragment) {
	    	associations = mapFragment.getTopicMap().getAssociation();
	    }
	    return associations;
	}
	
    private TopicType getTopicType(Topic topic) {
		String instance = topic.getInstanceOf()[0].getTopicRef().getHref();
//		log.debug("InstanceOf: "+instance);
		if (instance.indexOf("topTermType") != -1 || instance.indexOf("nodeLabelType") != -1
		 || instance.indexOf("descriptorType") != -1 || instance.indexOf("nonDescriptorType") != -1) {
			return TopicType.THESA;

		} else if (instance.indexOf("activityType") != -1 || instance.indexOf("anniversaryType") != -1
				 || instance.indexOf("conferenceType") != -1 || instance.indexOf("disasterType") != -1
				 || instance.indexOf("historicalType") != -1 || instance.indexOf("interYearType") != -1
				 || instance.indexOf("legalType") != -1 || instance.indexOf("observationType") != -1
				 || instance.indexOf("natureOfTheYearType") != -1 || instance.indexOf("publicationType") != -1) {
			return TopicType.EVENT;

		} else { // if instance.indexOf("nationType") != -1 || ...
			return TopicType.LOCATION;
		}
    }

    private String getTopicTitle(Topic topic, String langFilter) {
        BaseName[] baseNames = topic.getBaseName();
        // Set a default if for the selected language nothing exists.
        String title = baseNames[0].getBaseNameString().get_value();
        for (int i = 0; i < baseNames.length; i++) {
            final Scope scope = baseNames[i].getScope();
            if (scope != null) {
                final String href = scope.getTopicRef()[0].getHref();
                if (href.endsWith('#' + langFilter)) {
                    title = baseNames[i].getBaseNameString().get_value();
                    break;
                }
            }
        }
        
        return title;
    }

    private Occurrence getOccurrence(Topic topic, String occurrenceType) {
    	Occurrence result = null;
    	
    	if (null != topic.getOccurrence()) {
	    	for (Occurrence occ: topic.getOccurrence()) {
	    		if (occ.getInstanceOf().getTopicRef().getHref().endsWith(occurrenceType)) {
	    			result = occ;
	    		}
	    	}
    	}

    	return result;
    }
*/
    private TermType getTermType(Resource r) {
    	String nodeType = "descriptorType";

		if (nodeType.indexOf("topTermType") != -1) 
			return TermType.NODE_LABEL;
		else if (nodeType.indexOf("nodeLabelType") != -1) 
			return TermType.NODE_LABEL;
		else if (nodeType.indexOf("descriptorType") != -1) 
			return TermType.DESCRIPTOR;
		// SNS 2.1
		else if (nodeType.indexOf("nonDescriptorType") != -1 ||
				// SNS 2.0
				nodeType.indexOf("synonymType") != -1)
			return TermType.NON_DESCRIPTOR;
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

    private boolean isLanguage(Topic topic, String langFilter) {
        BaseName[] baseNames = topic.getBaseName();
        for (int i = 0; i < baseNames.length; i++) {
            final Scope scope = baseNames[i].getScope();
            if (scope != null) {
                final String href = scope.getTopicRef()[0].getHref();
                if (href.endsWith('#' + langFilter)) {
                	return true;
                }
            }
        }
        
        return false;
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
    /*

    private String getGemetName(Topic topic) {
    	String result = null;

    	Occurrence occ = getOccurrence(topic, "gemet1.0");
    	if (null != occ) {
    		String gemetOccurence = occ.getResourceData().get_value();
        	if (gemetOccurence != null) {
        		String[] gemetParts = gemetOccurence.split("@");
//            	log.debug("gemet title: "+gemetParts[1]);
        		result = gemetParts[1];
        	}
    	}
    	
    	return result;
    }
    */

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

    /*
    private List<String> getInspireThemes(Topic topic) {
    	List<String> inspireThemes = new ArrayList<String>();
    	
    	if (null != topic.getOccurrence()) {
	    	for (Occurrence occ: topic.getOccurrence()) {
	    		if (occ.getInstanceOf().getTopicRef().getHref().endsWith("iTheme2007")) {
	    			String inspireOccurence = occ.getResourceData().get_value();

	    			// TODO AW: ENGLISH also!!!
	    			if (inspireOccurence != null) {
	    	    		String[] inspireParts = inspireOccurence.split("@");
		    			inspireThemes.add(inspireParts[1]);
	    	    	}
	    		}
	    	}
    	}
    	
		return inspireThemes;
	}
     */
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
//	    		log.debug(pe);
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
