/*
 * **************************************************-
 * ingrid-external-service-sns
 * ==================================================
 * Copyright (C) 2014 - 2024 wemove digital solutions GmbH
 * ==================================================
 * Licensed under the EUPL, Version 1.2 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 * 
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * https://joinup.ec.europa.eu/software/page/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * **************************************************#
 */
package de.ingrid.external.sns;

import de.ingrid.external.om.Event;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

public class SNSClientTest {

    private SNSClient snsClient;
    private SNSService chronicalService;

    @BeforeEach
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

    @Test
    public void testFindEventsAtError() {
        //http://sns.uba.de/chronik/t7df45747_12f9d9eeddd_-7a46
        // get all events by setting date to null
        Resource eventsRes = snsClient.getTermByUri("http://sns.uba.de/chronik/", "http://sns.uba.de/chronik/t7df45747_12f9d9eeddd_-7a46", "de");
        assertNotNull(eventsRes);
    }

    @Test
    @Disabled("Search in description does not work anymore. Events are not supported anymore.")
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

    @Test
    @Disabled("Search in description does not work anymore. Events are not supported anymore.")
    public void testFindEventsFromTo() throws Exception {
        Resource eventsRes = snsClient.findEvents("", "contains", null, 0, "2019-01-01", "2019-08-29", "de", 10);
        assertNotNull(eventsRes);
        assertTrue(RDFUtils.getResults(eventsRes).toList().size() > 0);

        // see https://redmine.wemove.com/issues/2236
        eventsRes = snsClient.findEvents("", "contains", null, 0, "2019-01-25", "2019-01-25", "de", 10);
        assertNotNull(eventsRes);
        List<RDFNode> resultList = RDFUtils.getResults(eventsRes).toList();
        assertTrue(resultList.size() > 0);
    }

    @Test
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

    @Test
    @Disabled("Search in description does not work anymore. Events are not supported anymore.")
    public void testGetEventTerm() {
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
            assertNotSame(events[i].getId(), events2[i].getId());
        }

        events = chronicalService.getAnniversaries("1978-10-10", lang);
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

    @Test
    public void testGetEvent() {
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
        // can be null unfortunately
        // e.g.: https://sns.uba.de/chronik/de/concepts/_42ea37f4.html
        // assertNotNull(event.getTypeId());

        // can be null unfortunately
        // Problems with Anniversary https://sns.uba.de/chronik/_35c66fd1 NO DESCRIPTION !!!
        // when we pass other date it works
        assertNotNull(event.getDescription());

        //assertNotNull(event.getTimeAt());
        //assertNotNull(event.getTimeRangeFrom());
        //assertNotNull(event.getTimeRangeTo());
    }

}
