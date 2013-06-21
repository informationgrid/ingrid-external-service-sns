package de.ingrid.external.sns;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.Locale;
import java.util.ResourceBundle;

import junit.framework.TestCase;

import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;

import de.ingrid.external.FullClassifyService.FilterType;
import de.ingrid.external.ThesaurusService.MatchingType;
import de.ingrid.external.om.Event;

public class SNSClientTest extends TestCase {

	private SNSClient snsClient;
	private SNSService thesaurusService;

	public void setUp() {
		ResourceBundle resourceBundle = ResourceBundle.getBundle("sns");
		
		SNSService snsService = new SNSService();
		try {
			snsService.init();
		} catch (Exception e) {
			e.printStackTrace();
		}
		thesaurusService = snsService;
		
		try {
			snsClient = new SNSClient(
	    			resourceBundle.getString("sns.username"),
	    			resourceBundle.getString("sns.password"),
	    			resourceBundle.getString("sns.language"),
	        		new URL(resourceBundle.getString("sns.serviceURL.thesaurus")),
	        		new URL(resourceBundle.getString("sns.serviceURL.gazetteer")),
	        		new URL(resourceBundle.getString("sns.serviceURL.chronicle")));
			// snsMapper =   
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
    public void testFindEventsAt() throws Exception {
    	// get all events by setting date to null
    	Resource eventsRes = snsClient.findEvents("wasser", "contains", "FieldsType.allfields", 
        		0, null, "de", 10);
        assertNotNull(eventsRes);
        // TODO: result is 40 because result's numResultsPerPage is always 40
        assertEquals(40, RDFUtils.getResults(eventsRes).toList().size());
        
        eventsRes = snsClient.findEvents("wasser", "contains", "FieldsType.allfields", 
        		0, "1976-08-31", "de", 10);
        assertNotNull(eventsRes);
        assertEquals(40, RDFUtils.getResults(eventsRes).toList().size());
    }
    
    public void testFindEventsFromTo() throws Exception {
        Resource eventsRes = snsClient.findEvents("wasser", "contains", "FieldsType.allfields", 
        		0, "1976-08-31", "1978-08-31", "de", 10);
        assertNotNull(eventsRes);
        assertEquals(40, RDFUtils.getResults(eventsRes).toList().size());
    }

	public void testAnniversary() throws RemoteException {
		// Deutsche Ereignisse
		Resource res = snsClient.anniversary("2013-01-01", "de");
		ResIterator resIt = RDFUtils.getConcepts(res.getModel());
		assertTrue(resIt.toList().size() > 0);
		
		// Englische Ereignisse
		res = snsClient.anniversary("2013-01-01", "en");
		resIt = RDFUtils.getConcepts(res.getModel());
		assertTrue(resIt.toList().size() > 0);
	}
	
	public void testGetEventTerm() throws RemoteException {
		// Deutsche Ereignisse
		Locale lang = new Locale("de");
		Event[] events = thesaurusService.findEventsFromQueryTerm("wasser", de.ingrid.external.ChronicleService.MatchingType.CONTAINS, null, null, lang);
		assertTrue(events.length > 0);
		for (Event event : events) {
			checkEvent(event);
		}
		
		events = thesaurusService.getAnniversaries("10.10.1978", lang);
		assertTrue(events.length > 0);
		for (Event event : events) {
			checkEvent(event);
		}
		
		/*Resource res = snsClient.getTerm("", "de", FilterType.ONLY_EVENTS);
		ResIterator resIt = RDFUtils.getConcepts(res.getModel());
		
		// Englische Ereignisse
		res = snsClient.anniversary("2013-01-01", "en");
		resIt = RDFUtils.getConcepts(res.getModel());
		assertTrue(resIt.toList().size() > 0);*/
	}
	
	private void checkEvent(Event event) {
		assertNotNull(event);
		assertNotNull(event.getId());
		assertNotNull(event.getTitle());
		assertNotNull(event.getTypeId());
		assertNotNull(event.getDescription());
		assertNotNull(event.getTimeAt());
		assertNotNull(event.getTimeRangeFrom());
		assertNotNull(event.getTimeRangeTo());
	}

}
