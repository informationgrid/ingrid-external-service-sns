package de.ingrid.external.sns;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import junit.framework.TestCase;
import de.ingrid.external.FullClassifyService;
import de.ingrid.external.FullClassifyService.FilterType;
import de.ingrid.external.om.FullClassifyResult;
import de.ingrid.external.om.IndexedDocument;

public class FullClassifyTest extends TestCase {
	
	private FullClassifyService fullClassifyService;
	
	public void setUp() {
		SNSService snsService = new SNSService();
		try {
			snsService.init();
		} catch (Exception e) {
			e.printStackTrace();
		}
		fullClassifyService = snsService;
	}
	
	public final void testAutoClassifyURL() throws MalformedURLException {
		FullClassifyResult result;

		// www.portalu.de, FULL DATA
		URL url = new URL ("http://www.portalu.de");			
		int analyzeMaxWords = 500;
		boolean ignoreCase = true;
		Locale locale = Locale.GERMAN;
		result = fullClassifyService.autoClassifyURL(url, analyzeMaxWords, ignoreCase, null, locale);
		checkFullClassifyResult(result, url);
		assertTrue(result.getTerms().size() > 0);
		assertTrue(result.getLocations().size() > 0);
		assertTrue(result.getEvents().size() > 0);

		// ONLY TERMS
		result = fullClassifyService.autoClassifyURL(url, analyzeMaxWords, ignoreCase, FilterType.ONLY_TERMS, locale);
		checkFullClassifyResult(result, url);
		assertTrue(result.getTerms().size() > 0);
		assertTrue(result.getLocations().size() == 0);
		assertTrue(result.getEvents().size() == 0);

		// ONLY LOCATIONS
		result = fullClassifyService.autoClassifyURL(url, analyzeMaxWords, ignoreCase, FilterType.ONLY_LOCATIONS, locale);
		checkFullClassifyResult(result, url);
		assertTrue(result.getTerms().size() == 0);
		assertTrue(result.getLocations().size() > 0);
		assertTrue(result.getEvents().size() == 0);

		// ONLY EVENTS
		result = fullClassifyService.autoClassifyURL(url, analyzeMaxWords, ignoreCase, FilterType.ONLY_EVENTS, locale);
		checkFullClassifyResult(result, url);
		assertTrue(result.getTerms().size() == 0);
		assertTrue(result.getLocations().size() == 0);
		assertTrue(result.getEvents().size() > 0);
	}
	
	private void checkFullClassifyResult(FullClassifyResult result, URL expectedUrl) {
		assertNotNull(result);
		checkIndexedDocument(result.getIndexedDocument(), expectedUrl);
	}

	private void checkIndexedDocument(IndexedDocument indexedDoc, URL expectedUrl) {
		assertNotNull(indexedDoc);
		assertNotNull(indexedDoc.getClassifyTimeStamp());
		assertTrue(indexedDoc.getTitle().length() > 0);
		assertTrue(indexedDoc.getDescription().length() > 0);
		assertEquals(expectedUrl, indexedDoc.getURL());
		assertNotNull(indexedDoc.getLang());
		assertTrue(indexedDoc.getTimeAt() != null ||
				(indexedDoc.getTimeFrom() != null && indexedDoc.getTimeTo() != null));
	}
}
