package de.ingrid.external.sns;

import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Date;
import java.util.Locale;
import java.util.ResourceBundle;

import junit.framework.TestCase;

import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;

import de.ingrid.external.om.Event;

public class SNSClientTest extends TestCase {

	private SNSClient snsClient;
	private SNSService chronicalService;

	public void setUp() {
		ResourceBundle resourceBundle = ResourceBundle.getBundle("sns");
		
		SNSService snsService = new SNSService();
		try {
			snsService.init();
		} catch (Exception e) {
			e.printStackTrace();
		}
		chronicalService = snsService;
		
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
	
	public void testFindEventsAtError() throws Exception { 
	    //http://sns.uba.de/chronik/t7df45747_12f9d9eeddd_-7a46
	    // get all events by setting date to null
        Resource eventsRes = snsClient.getTermByUri( "http://sns.uba.de/chronik/", "http://sns.uba.de/chronik/t7df45747_12f9d9eeddd_-7a46", "de" ); 
        assertNotNull( eventsRes );
	}
	
    public void testFindEventsAt() throws Exception {
    	// get all events by setting date to null
    	Resource eventsRes = snsClient.findEvents("wasser", "contains", null, 
        		0, null, "de", 10);
        assertNotNull(eventsRes);
        // TODO: result is 40 because result's numResultsPerPage is always 40
        assertEquals(40, RDFUtils.getResults(eventsRes).toList().size());
        
        eventsRes = snsClient.findEvents("Explosion", "contains", null, 
        		0, "2011-09-12", "de", 10);
        assertNotNull(eventsRes);
        assertEquals(1, RDFUtils.getResults(eventsRes).toList().size());
    }
    
    public void testFindEventsFromTo() throws Exception {
        Resource eventsRes = snsClient.findEvents("Mauer", "contains", null, 
        		0, "1972-07-22", "2001-07-22", "de", 10);
        assertNotNull(eventsRes);
        assertTrue( RDFUtils.getResults(eventsRes).toList().size() > 0);
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
		Event[] events = chronicalService.findEventsFromQueryTerm("wasser", de.ingrid.external.ChronicleService.MatchingType.CONTAINS, null, null, null, lang, 0, 10);
		assertTrue(events.length > 0);
		for (int i = 0; i < 10; i++) {
			checkEvent(events[i]);
		}
		
		lang = new Locale("de");
		Event[] events2 = chronicalService.findEventsFromQueryTerm("wasser", de.ingrid.external.ChronicleService.MatchingType.CONTAINS, null, null, null, lang, 1, 10);
		assertTrue(events2.length > 0);
		for (int i = 0; i < 10; i++) {
			checkEvent(events2[i]);
			assertTrue(events[i].getId() != events2[i].getId());
		}
		
		events = chronicalService.getAnniversaries("10.10.1978", lang);
		assertTrue(events.length > 0);
		for (Event event : events) {
		    // INFO: one of the events has no title, but it's a problem with the SNS
		    // => https://github.com/innoq/iqvoc_chronicle/issues/94
			checkEvent(event);
		}
		
		/*Resource res = snsClient.getTerm("", "de", FilterType.ONLY_EVENTS);
		ResIterator resIt = RDFUtils.getConcepts(res.getModel());
		
		// Englische Ereignisse
		res = snsClient.anniversary("2013-01-01", "en");
		resIt = RDFUtils.getConcepts(res.getModel());
		assertTrue(resIt.toList().size() > 0);*/
	}
	
	public void testGetEvent() throws RemoteException {
		Event e = chronicalService.getEvent("https://sns.uba.de/chronik/t2a639eb3_12b99052384_-37ba", new Locale("de"));
		assertEquals(Date.valueOf("2010-10-05"), e.getTimeAt());
		assertEquals(Date.valueOf("2010-10-05"), e.getTimeRangeFrom());
		assertEquals(Date.valueOf("2010-10-05"), e.getTimeRangeTo());
		assertEquals("activity", e.getTypeId());
		assertEquals("https://sns.uba.de/chronik/t2a639eb3_12b99052384_-37ba", e.getId());
		assertEquals(2, e.getLinks().size());
	}
	
	private void checkEvent(Event event) {
		assertNotNull(event);
		assertNotNull(event.getId());
		assertNotNull(event.getTitle());
		assertNotNull(event.getTypeId());
		assertNotNull(event.getDescription());
		//assertNotNull(event.getTimeAt());
		//assertNotNull(event.getTimeRangeFrom());
		//assertNotNull(event.getTimeRangeTo());
	}

}