package de.ingrid.external.sns;

import java.util.Locale;

import junit.framework.TestCase;
import de.ingrid.external.GazetteerService;
import de.ingrid.external.GazetteerService.QueryType;
import de.ingrid.external.om.Location;

public class GazetteerTest extends TestCase {
	
	private GazetteerService gazetteerService;
	
	public void setUp() {
		SNSServiceAccess snsService = new SNSServiceAccess();
		try {
			snsService.init();
		} catch (Exception e) {
			e.printStackTrace();
		}
		gazetteerService = snsService;
	}
	
	public final void testRelatedLocationsFromLocation() {
		Location[] locations;

		// valid location in german
		String locationId = "BERG41128";
		Locale locale = Locale.GERMAN;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, locale);
		assertTrue(locations.length > 0);

		// in english ?
		locale = Locale.ENGLISH;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, locale);
		assertTrue(locations.length > 0);

		// valid location in german
		locationId = "NATURPARK31";
		locale = Locale.GERMAN;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, locale);
		assertTrue(locations.length > 0);

		// in english ?
		locale = Locale.ENGLISH;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, locale);
		assertTrue(locations.length > 0);

		// INVALID location in german
		locationId = "wrong id";
		locale = Locale.GERMAN;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, locale);
		assertTrue(locations.length == 0);

		// in english ?
		locale = Locale.ENGLISH;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, locale);
		assertTrue(locations.length == 0);
	}

	public final void testLocationsFromText() {
		Location[] locations;

		// german locations
		String text = "Frankfurt Sachsenhausen Äppelwoi Handkäs Main";
		int analyzeMaxWords = 1000;
		boolean ignoreCase = true;
		Locale locale = Locale.GERMAN;
		
		locations = gazetteerService.getLocationsFromText(text, analyzeMaxWords, ignoreCase, locale);
		assertTrue(locations.length > 0);

		// english results
		text = "Frankfurt Main Sachsenhausen Airport";
		locale = Locale.ENGLISH;

		locations = gazetteerService.getLocationsFromText(text, analyzeMaxWords, ignoreCase, locale);
		assertTrue(locations.length > 0);
	}

	public final void testLocationsFromQueryTerms() {
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
