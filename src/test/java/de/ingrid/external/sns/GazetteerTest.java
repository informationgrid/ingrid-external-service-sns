package de.ingrid.external.sns;

import java.util.Locale;

import junit.framework.TestCase;
import de.ingrid.external.GazetteerService;
import de.ingrid.external.GazetteerService.MatchingType;
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
	
	public final void testGetRelatedLocationsFromLocation() {
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

	public final void testGetLocationsFromText() {
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

	public final void testFindLocationsFromQueryTerm() {
		Location[] locations;

		// german locations
		String queryTerm = "Frankfurt";
		Locale locale = Locale.GERMAN;
		
		// all locations, BEGINS_WITH
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ALL_LOCATIONS, MatchingType.BEGINS_WITH,
				locale);
		assertEquals(5, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().contains(queryTerm));
		}

		// all locations, EXACT
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ALL_LOCATIONS, MatchingType.EXACT,
				locale);
		assertEquals(4, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().contains(queryTerm));
		}

		// all locations, CONTAINS
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ALL_LOCATIONS, MatchingType.CONTAINS,
				locale);
		assertEquals(5, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().contains(queryTerm));
		}

		// only administrative locations, BEGINS_WITH
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ONLY_ADMINISTRATIVE_LOCATIONS, MatchingType.BEGINS_WITH,
				locale);
		assertEquals(4, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().contains(queryTerm));
			assertNotNull(location.getNativeKey());
			assertNotNull(location.getBoundingBox());
		}

		// english results
		queryTerm = "Frankfurt";
		locale = Locale.ENGLISH;

		// all locations
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ALL_LOCATIONS, MatchingType.BEGINS_WITH,
				locale);
		assertEquals(5, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().contains(queryTerm));
		}

		// only administrative locations
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ONLY_ADMINISTRATIVE_LOCATIONS, MatchingType.EXACT,
				locale);
		assertEquals(4, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().contains(queryTerm));
			assertNotNull(location.getNativeKey());
			assertNotNull(location.getBoundingBox());
		}
	}

	private void checkLocation(Location location) {
		checkLocation(location, null, null);
	}
	private void checkLocation(Location location, String id, String name) {
		assertNotNull(location);
		assertNotNull(location.getId());
		assertNotNull(location.getName());
		if (id != null) {
			assertEquals(id, location.getId());			
		}
		if (name != null) {
			assertEquals(name, location.getName());
		}
	}
}
