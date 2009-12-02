package de.ingrid.external.sns;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.slb.taxi.webservice.xtm.stubs.TopicMapFragment;
import com.slb.taxi.webservice.xtm.stubs.TopicMapFragmentIndexedDocument;
import com.slb.taxi.webservice.xtm.stubs.xtm.Occurrence;
import com.slb.taxi.webservice.xtm.stubs.xtm.Topic;

import de.ingrid.external.om.Event;
import de.ingrid.external.om.FullClassifyResult;
import de.ingrid.external.om.IndexedDocument;
import de.ingrid.external.om.Location;
import de.ingrid.external.om.Term;
import de.ingrid.external.om.Term.TermType;
import de.ingrid.external.om.impl.EventImpl;
import de.ingrid.external.om.impl.FullClassifyResultImpl;
import de.ingrid.external.om.impl.IndexedDocumentImpl;
import de.ingrid.external.om.impl.LocationImpl;
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
     * <b>NOTICE: Removes expired topics !!!</b>
     * @param topics sns topics representing locations
     * @return the locations NEVER NULL (but may be empty)
     */
    public List<Location> mapToLocations(Topic[] topics) {
    	List<Location> resultList = new ArrayList<Location>();

    	if ((null != topics)) {
            for (Topic topic : topics) {
        		// createLocationTopic returns null for expired topics
        		Location loc = mapToLocation(topic);
        		if (null != loc) {
        			resultList.add(loc);
        		}
			}
        }
    	
    	return resultList;
    }

    /** Creates a Location from the given topic.<br/>
     * <b>NOTICE: Returns null if topic expired !!!</b>
     * @param topic sns topic representing location
     * @return the location <b>OR NULL IF TOPIC EXPIRED !!!</b>
     */
    public Location mapToLocation(Topic topic) {
    	LocationImpl result = new LocationImpl();
    	result.setId(topic.getId());
    	result.setName(topic.getBaseName(0).getBaseNameString().get_value());
    	String typeId = topic.getInstanceOf(0).getTopicRef().getHref();
    	typeId = typeId.substring(typeId.lastIndexOf("#")+1);
    	result.setTypeId(typeId);
    	result.setTypeName(resourceBundle.getString("sns.topic.ref."+typeId));

    	// If the topic doesn't contain any more information return the basic info
    	if (topic.getOccurrence() == null) {
    		return result;
    	}


    	// Iterate over all occurrences and extract the relevant information (bounding box wgs84 coords and the qualifier)
    	for(int i = 0; i < topic.getOccurrence().length; ++i) {
//    		log.debug(topic.getOccurrence(i).getInstanceOf().getTopicRef().getHref());
    		if (topic.getOccurrence(i).getInstanceOf().getTopicRef().getHref().endsWith("wgs84BoxOcc")) {
//    			log.debug("WGS84 Coordinates: "+topic.getOccurrence(i).getResourceData().get_value());        	            			
        		String coords = topic.getOccurrence(i).getResourceData().get_value();
        		String[] ar = coords.split("\\s|,");
        		if (ar.length == 4) {
        			result.setBoundingBox(new Float(ar[0]), new Float(ar[1]), new Float(ar[2]), new Float(ar[3]));
        		}
    		} else if (topic.getOccurrence(i).getInstanceOf().getTopicRef().getHref().endsWith("qualifier")) {
//    			log.debug("Qualifier: "+topic.getOccurrence(i).getResourceData().get_value());        	            			
        		result.setQualifier(topic.getOccurrence(i).getResourceData().get_value());
    		} else if (topic.getOccurrence(i).getInstanceOf().getTopicRef().getHref().endsWith("nativeKeyOcc")) {
    			String nativeKeyOcc = topic.getOccurrence(i).getResourceData().get_value();
    			String[] keys = nativeKeyOcc.split(" ");
    			for (String nativeKey : keys) {
    				if (nativeKey.startsWith(SNS_NATIVE_KEY_PREFIX)) {
    					result.setNativeKey(nativeKey.substring(SNS_NATIVE_KEY_PREFIX.length()));
    				}
    			}
    		} else if (topic.getOccurrence(i).getInstanceOf().getTopicRef().getHref().endsWith("expiredOcc")) {
                try {
                    Date expiredDate = expiredDateParser.parse(topic.getOccurrence(i).getResourceData().get_value());
                    if ((null != expiredDate) && expiredDate.before(new Date())) {
                        return null;
                    }
                } catch (java.text.ParseException e) {
                    log.error("Not expected date format in sns expiredOcc.", e);
                }
    		}
    	}
    	if (result.getQualifier() == null)
    		result.setQualifier(result.getTypeId());
    	return result;
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
    			resultList.add(mapToTerm(topic));
			}
        }
    	
    	return resultList;
    }

    /** Creates a Term from the given topic.<br/>
     * @param topic sns topic representing a term
     * @return the term, NEVER NULL
     */
    public Term mapToTerm(Topic topic) {
    	TermImpl result = new TermImpl();

		result.setId(topic.getId());
		result.setName(topic.getBaseName(0).getBaseNameString().get_value());
		result.setType(getTermType(topic));

		result.setInspireThemes(getInspireThemes(topic));

    	if (isGemet(topic)) {
    		// if GEMET, then the title is used for the title in SNSTopic and, in case UMTHES is different
    		// the UMTHES value is stored in alternateTitle
    		result.setAlternateName(result.getName());
    		result.setName(getGemetName(topic));
    		result.setAlternateId(getGemetId(topic));
    	}

    	return result;
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
    	
    	result.setLocations(mapToLocations(locTopics.toArray(new Topic[locTopics.size()])));
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

    private boolean isGemet(Topic topic) {
    	Occurrence occ = getOccurrence(topic, "gemet1.0");
    	if (null != occ) {
    		return true;
    	}
    	
    	return false;
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
