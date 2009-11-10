package de.ingrid.external.sns;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.slb.taxi.webservice.xtm.stubs.xtm.Topic;

import de.ingrid.external.om.Location;
import de.ingrid.external.sns.om.SNSLocation;

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

    /** Creates a Location from the given topic.
     * @param topic sns topic representing location
     * @return the location or NULL
     */
    public Location mapTopicToLocation(Topic topic) {
    	SNSLocation result = new SNSLocation();
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
}
