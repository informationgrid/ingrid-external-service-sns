package de.ingrid.external.sns;

import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.slb.taxi.webservice.xtm.stubs.TopicMapFragment;
import com.slb.taxi.webservice.xtm.stubs.TopicMapFragmentIndexedDocument;
import com.slb.taxi.webservice.xtm.stubs.xtm.Association;
import com.slb.taxi.webservice.xtm.stubs.xtm.InstanceOf;
import com.slb.taxi.webservice.xtm.stubs.xtm.Member;
import com.slb.taxi.webservice.xtm.stubs.xtm.Occurrence;
import com.slb.taxi.webservice.xtm.stubs.xtm.Topic;

import de.ingrid.external.om.Event;
import de.ingrid.external.om.FullClassifyResult;
import de.ingrid.external.om.IndexedDocument;
import de.ingrid.external.om.Location;
import de.ingrid.external.om.RelatedTerm;
import de.ingrid.external.om.Term;
import de.ingrid.external.om.RelatedTerm.RelationType;
import de.ingrid.external.om.Term.TermType;
import de.ingrid.external.om.impl.EventImpl;
import de.ingrid.external.om.impl.FullClassifyResultImpl;
import de.ingrid.external.om.impl.IndexedDocumentImpl;
import de.ingrid.external.om.impl.LocationImpl;
import de.ingrid.external.om.impl.RelatedTermImpl;
import de.ingrid.external.om.impl.TermImpl;

/**
 * Singleton encapsulating methods for mapping SNS data structures to API beans.
 */
public class SNSMapper {

	private final static Logger log = Logger.getLogger(SNSMapper.class);
	private final static SimpleDateFormat expiredDateParser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");

	private static SNSMapper myInstance;

    // Settings
    private ResourceBundle resourceBundle; 
	private String SNS_NATIVE_KEY_PREFIX; 

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
    	SNS_NATIVE_KEY_PREFIX = resourceBundle.getString("sns.nativeKeyPrefix");
	}

    /** Creates a Location list from the given topics.<br/>
     * @param topics sns topics representing locations
     * @param checkExpired if true checks topics whether expired and REMOVES expired ones
     * @return the locations NEVER NULL (but may be empty)
     */
    public List<Location> mapToLocations(Topic[] topics,
    		boolean checkExpired) {
    	List<Location> resultList = new ArrayList<Location>();

    	if ((null != topics)) {
            for (Topic topic : topics) {
            	if (checkExpired && isExpired(topic)) {
            		continue;
            	}

        		resultList.add(mapToLocation(topic, new LocationImpl()));
			}
        }
    	
    	return resultList;
    }

    /** Creates a Location from the given topic.<br/>
     * @param topic sns topic representing location
     * @param outLocation the location the topic is mapped to, NEVER NULL 
     * @return again the outLocation after mapping, NEVER NULL
     */
    public Location mapToLocation(Topic topic, Location outLocation) {
    	outLocation.setId(topic.getId());
    	outLocation.setName(topic.getBaseName(0).getBaseNameString().get_value());
    	String typeId = topic.getInstanceOf(0).getTopicRef().getHref();
    	typeId = typeId.substring(typeId.lastIndexOf("#")+1);
    	outLocation.setTypeId(typeId);
    	outLocation.setTypeName(resourceBundle.getString("sns.topic.ref."+typeId));

    	// If the topic doesn't contain any more information return the basic info
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
    	}

    	if (outLocation.getQualifier() == null)
    		outLocation.setQualifier(outLocation.getTypeId());

    	return outLocation;
    }

    /** Creates a Term list from the given topics.<br/>
     * @param topics sns topics representing terms
     * @param filter only use topics matching the given type, remove the other ones ! Pass null if all types
     * @return the terms NEVER NULL
     */
    public List<Term> mapToTerms(Topic[] topics, TermType filter) {
    	List<Term> resultList = new ArrayList<Term>();

    	if ((null != topics)) {
            for (Topic topic : topics) {
            	if (filter != null) {
            		if (!getTermType(topic).equals(filter)) {
            			continue;
            		}
            	}
    			resultList.add(mapToTerm(topic, new TermImpl()));
			}
        }
    	
    	return resultList;
    }

    /** Creates a Term from the given topic.<br/>
     * @param inTopic sns topic representing a term
     * @param outTerm the term the topic is mapped to, NEVER NULL 
     * @return again the outTerm after mapping, NEVER NULL
     */
    public Term mapToTerm(Topic inTopic, Term outTerm) {
		outTerm.setId(inTopic.getId());
		outTerm.setName(inTopic.getBaseName(0).getBaseNameString().get_value());
		outTerm.setType(getTermType(inTopic));

		outTerm.setInspireThemes(getInspireThemes(inTopic));

    	if (isGemet(inTopic)) {
    		// if GEMET, then the title is used for the title in SNSTopic and, in case UMTHES is different
    		// the UMTHES value is stored in alternateTitle
    		outTerm.setAlternateName(outTerm.getName());
    		outTerm.setName(getGemetName(inTopic));
    		outTerm.setAlternateId(getGemetId(inTopic));
    	}

    	return outTerm;
    }

    /** Creates a RelatedTerm list from the given TopicMapFragment.<br/>
     * @param sourceTopicId id of the topic to get related terms for
     * @param mapFragment sns result of relations (getPSI)
     * @param checkExpired if true checks topics whether expired and REMOVES expired ones
     * @return the related terms NEVER NULL
     */
    public List<RelatedTerm> mapToRelatedTerms(String sourceTopicId,
    		TopicMapFragment mapFragment,
    		boolean checkExpired) {
    	List<RelatedTerm> resultList = new ArrayList<RelatedTerm>();

        final Topic[] topics = getTopics(mapFragment);
        final Association[] associations = getAssociations(mapFragment);

        // iterate through associations to find the correct associations !
        if (associations != null) {
            for (Association association : associations) {
                RelatedTerm relTerm = getBasicRelatedTerm(sourceTopicId, association);
                if (relTerm != null) {
                	// determine topic from topic list
                    for (Topic topic : topics) {
                        if (relTerm.getId().equals(topic.getId())) {
                        	// topic found
                        	// check expired
                        	if (!checkExpired || !isExpired(topic)) {
                            	// map data and add to result list
                            	resultList.add((RelatedTerm)mapToTerm(topic, relTerm));
                        	}
                        	break;                        		
                        }
                    }
                }
            }
        }

    	return resultList;
    }

    /** Creates an Event list from the given topics.<br/>
     * @param topics sns topics representing events
     * @return the events NEVER NULL
     */
    public List<Event> mapToEvents(Topic[] topics) {
    	List<Event> resultList = new ArrayList<Event>();

    	if ((null != topics)) {
            for (Topic topic : topics) {
    			resultList.add(mapToEvent(topic));
			}
        }
    	
    	return resultList;
    }

    /** Creates an Event from the given topic.<br/>
     * @param topic sns topic representing an event
     * @return the event, NEVER NULL
     */
    public Event mapToEvent(Topic topic) {
    	EventImpl result = new EventImpl();

		result.setId(topic.getId());
		result.setTitle(topic.getBaseName(0).getBaseNameString().get_value());

    	for (Occurrence occ: topic.getOccurrence()) {
    		if (occ.getInstanceOf().getTopicRef().getHref().endsWith("descriptionOcc")) {
    			if (occ.getScope().getTopicRef()[0].getHref().endsWith("de") && occ.getResourceData() != null)
    				result.setDescription(occ.getResourceData().get_value());

    		} else if (occ.getInstanceOf().getTopicRef().getHref().endsWith("temporalAtOcc")) {        		
//    			log.debug("Temporal at: "+occ.getResourceData().get_value());
    	    	result.setTimeAt(convertToDate(occ.getResourceData().get_value()));

    		} else if (occ.getInstanceOf().getTopicRef().getHref().endsWith("temporalFromOcc")) {        		
//    			log.debug("Temporal from: "+occ.getResourceData().get_value());
    	    	result.setTimeRangeFrom(convertToDate(occ.getResourceData().get_value()));

    		} else if (occ.getInstanceOf().getTopicRef().getHref().endsWith("temporalToOcc")) {        		
//    			log.debug("Temporal to: "+occ.getResourceData().get_value());
    	    	result.setTimeRangeTo(convertToDate(occ.getResourceData().get_value()));
        	}
    	}

    	return result;
    }

    /** Creates a FullClassifyResult from the given mapFragment
     * @param inMap result of fullClassify as delivered by SNS
     * @return the FullClassifyResult NEVER NULL
     */
    public FullClassifyResult mapToFullClassifyResult(TopicMapFragment inMap) {
    	FullClassifyResultImpl result = new FullClassifyResultImpl();
    	
    	result.setIndexedDocument(mapToIndexedDocument(inMap.getIndexedDocument()));
    	
    	Topic[] topics = inMap.getTopicMap().getTopic();
    	if (null == topics) {
    		topics = new Topic[0];
    	}

    	// sort topics into different Lists
    	List<Topic> locTopics = new ArrayList<Topic>();
    	List<Topic> thesaTopics = new ArrayList<Topic>();
    	List<Topic> eventTopics = new ArrayList<Topic>();

    	for (Topic topic : topics) {
    		TopicType topicType = getTopicType(topic);
    		if (topicType == TopicType.LOCATION) {
    			locTopics.add(topic);
    		} else if (topicType == TopicType.THESA) {
    			thesaTopics.add(topic);
    		} else if (topicType == TopicType.EVENT) {
    			eventTopics.add(topic);
    		}
    	}
    	
    	result.setLocations(mapToLocations(locTopics.toArray(new Topic[locTopics.size()]), true));
    	result.setTerms(mapToTerms(thesaTopics.toArray(new Topic[thesaTopics.size()]), null));
    	result.setEvents(mapToEvents(eventTopics.toArray(new Topic[eventTopics.size()])));

    	return result;
    }

    /** Creates a IndexedDocument from the given TopicMapFragmentIndexedDocument
     * @param inDoc result of fullClassify as delivered by SNS
     * @return the IndexedDocument NEVER NULL
     */
    private IndexedDocument mapToIndexedDocument(TopicMapFragmentIndexedDocument inDoc) {
    	IndexedDocumentImpl result = new IndexedDocumentImpl();

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
    	result.setTimeTo(convertToDate(inDoc.getTo()));

    	return result;
    }

	/** Get topics from fragment
	 * @param mapFragment sns result
	 * @return the topics OR NULL
	 */
	public Topic[] getTopics(TopicMapFragment mapFragment) {
		Topic[] topics = null;
	    if (null != mapFragment) {
	    	topics = mapFragment.getTopicMap().getTopic();
	    }
	    return topics;
	}
	
	/** Get associations from fragment
	 * @param mapFragment sns result
	 * @return the associations OR NULL
	 */
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

    private TermType getTermType(Topic t) {
    	String nodeType = t.getInstanceOf(0).getTopicRef().getHref();

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
     * Determine whether the given association is a relation between terms and get
     * basic data of the relation to the target term (from source) encapsulated in a RelationTerm
     * containing PARTIAL info (id of target term, type of relation)
     * @param sourceTermId the id of the source term the association was determined from 
     * @param assoc an SNS association of the source term
     * @return the found related term OR NULL (if no association to a term)
     */
    private RelatedTerm getBasicRelatedTerm(String sourceTermId, Association assoc) {
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
                String targetTermId = member.getTopicRef()[0].getHref();
                if (!targetTermId.equals(sourceTermId)) {
                    // here is only the topic id available
                	result.setId(targetTermId);
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

    private boolean isGemet(Topic topic) {
    	Occurrence occ = getOccurrence(topic, "gemet1.0");
    	if (null != occ) {
    		return true;
    	}
    	
    	return false;
    }

    private boolean isExpired(Topic topic) {
        Date expDate = null;
        Occurrence[] occurrences = topic.getOccurrence();
        if (null != occurrences) {
            for (Occurrence occ : occurrences) {
                final InstanceOf instanceOf = occ.getInstanceOf();
                if (instanceOf != null) {
                    final String type = instanceOf.getTopicRef().getHref();
                    if (type.endsWith("expiredOcc")) {
                        try {
                            expDate = expiredDateParser.parse(occ.getResourceData().get_value());
                        } catch (ParseException e) {
                            log.error("Not expected date format in sns expiredOcc.", e);
                        }
                    }
                }
            }
        }

        boolean isExpired = false;
        if (expDate != null) {
        	isExpired = expDate.before(new Date());
        }

        return isExpired;
    }

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

    private String getGemetId(Topic topic) {
    	String result = null;

    	Occurrence occ = getOccurrence(topic, "gemet1.0");
    	if (null != occ) {
    		String gemetOccurence = occ.getResourceData().get_value();
        	if (gemetOccurence != null) {
        		String[] gemetParts = gemetOccurence.split("@");
//            	log.debug("gemet id: "+gemetParts[0]);
        		return gemetParts[0];
        	}
    	}

    	return result;
    }

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
}
