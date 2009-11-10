package de.ingrid.external.sns;

import java.util.Locale;

import junit.framework.TestCase;
import de.ingrid.external.GazetteerService;
import de.ingrid.external.GazetteerService.QueryType;
import de.ingrid.external.om.Location;

public class GazetteerTest extends TestCase {
	
	private GazetteerService gazetteerService;
	
	public void setUp() {
		SNSService snsService = new SNSService();
		try {
			snsService.init();
		} catch (Exception e) {
			e.printStackTrace();
		}
		gazetteerService = snsService;
	}
	
	public final void testGetLocationsFromQueryTerms() {
		Location[] locations;

		// german locations
		String queryTerms = "Frankfurt";
		Locale locale = Locale.GERMAN;
		
		// all locations
		locations = gazetteerService.getLocationsFromQueryTerms(queryTerms,
				QueryType.ALL_LOCATIONS,
				locale);
		assertTrue(locations.length > 0);

		// only administrative locations
		locations = gazetteerService.getLocationsFromQueryTerms(queryTerms,
				QueryType.ONLY_ADMINISTRATIVE_LOCATIONS,
				locale);
		assertTrue(locations.length > 0);

		// english results
		queryTerms = "Frankfurt";
		locale = Locale.ENGLISH;

		// all locations
		locations = gazetteerService.getLocationsFromQueryTerms(queryTerms,
				QueryType.ALL_LOCATIONS,
				locale);
		assertTrue(locations.length > 0);

		// only administrative locations
		locations = gazetteerService.getLocationsFromQueryTerms(queryTerms,
				QueryType.ONLY_ADMINISTRATIVE_LOCATIONS,
				locale);
		assertTrue(locations.length > 0);
	}
}
