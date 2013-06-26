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

		// valid location in german, INCLUDE fromLocation
		String locationId = "http://iqvoc-gazetteer.innoq.com/BERG41128";  // Großer Buchberg
		Locale locale = Locale.GERMAN;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, true, locale);
		assertEquals(6, locations.length);
		checkLocation(locations[0], locationId, "Gro\u00DFer Buchberg");
		// TODO: related locations do not include labels!
		/*for (Location loc : locations) {
			checkLocation(loc);			
			assertFalse(loc.getIsExpired());
		}*/

		// valid location in german, DO NOT INCLUDE fromLocation
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, false, locale);
		assertEquals(5, locations.length);
		/*for (Location loc : locations) {
			assertTrue(!locationId.equals(loc.getId()));
			checkLocation(loc);			
			assertFalse(loc.getIsExpired());
		}*/

		// TODO: in english ?
		locale = Locale.ENGLISH;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, true, locale);
		//assertTrue(locations.length > 0);

		// EXPIRED !!!! INCLUDE fromLocation ! BUT IS REMOVED BECAUSE EXPIRED !!!!
		locationId = "http://iqvoc-gazetteer.innoq.com/GEMEINDE0325300005"; // Gehrden
		locale = Locale.GERMAN;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, true, locale);
		assertEquals(1, locations.length);
		/*for (Location loc : locations) {
			checkLocation(loc);
			assertFalse("GEMEINDE0325300005".equals(loc.getId()));
			assertFalse(loc.getIsExpired());
		}*/

		// valid location in german
		locationId = "http://iqvoc-gazetteer.innoq.com/NATURPARK31";
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, true, locale);
		assertTrue(locations.length > 0);

		// TODO: in english ?
		locale = Locale.ENGLISH;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, true, locale);
		//assertTrue(locations.length > 0);

		// INVALID location in german
		locationId = "http://iqvoc-gazetteer.innoq.com/wrong id";
		locale = Locale.GERMAN;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, true, locale);
		assertTrue(locations.length == 0);

		// in english ?
		locale = Locale.ENGLISH;
		locations = gazetteerService.getRelatedLocationsFromLocation(locationId, true, locale);
		assertTrue(locations.length == 0);
	}

	public final void testGetLocation() {
		Location location;

		// valid location in german
		String locationId = "http://iqvoc-gazetteer.innoq.com/BERG41128"; // Großer Buchberg
		Locale locale = Locale.GERMAN;
		location = gazetteerService.getLocation(locationId, locale);
		checkLocation(location, locationId, "Gro\u00DFer Buchberg");
		assertEquals("Berg", location.getTypeName());
		assertFalse(location.getIsExpired());

		// in english ?  SAME NAME because locale ignored by SNS, id determines language !
		// NO ENGLISH LOCATIONS IN SNS !!!
		
		// TODO: wait for English support
		/*locale = Locale.ENGLISH;
		location = gazetteerService.getLocation(locationId, locale);
		checkLocation(location, locationId, "Gro\u00DFer Buchberg");
		assertFalse(location.getIsExpired());*/

		// valid location. NOTICE: locale ignored
		locationId = "http://iqvoc-gazetteer.innoq.com/NATURPARK31"; // Hessischer Spessart
		location = gazetteerService.getLocation(locationId, locale);
		checkLocation(location, locationId, "Hessischer Spessart");
		assertEquals("Naturpark", location.getTypeName());
		assertFalse(location.getIsExpired());

		// valid location. NOTICE: locale ignored
		locationId = "http://iqvoc-gazetteer.innoq.com/GEMEINDE0641200000"; // Frankfurt am Main
		location = gazetteerService.getLocation(locationId, locale);
		checkLocation(location, locationId, "Frankfurt am Main");
		assertEquals("Gemeinde", location.getTypeName());
		// TODO: assertEquals("Stadt", location.getQualifier());
		// TODO: assertEquals("06412000", location.getNativeKey());
		assertNotNull(location.getBoundingBox());
		assertFalse(location.getIsExpired());

		// EXPIRED LOCATION !
		locationId = "http://iqvoc-gazetteer.innoq.com/GEMEINDE1510100000"; // Dessau
		location = gazetteerService.getLocation(locationId, locale);
		checkLocation(location, locationId, "Dessau");
		assertTrue(location.getIsExpired());

		// INVALID location
		locationId = "http://iqvoc-gazetteer.innoq.com/wrong-id";
		locale = Locale.GERMAN;
		location = gazetteerService.getLocation(locationId, locale);
		assertNull(location);
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
		String queryTerm = "berlin";
		Locale locale = Locale.GERMAN;
		
		// all locations, BEGINS_WITH
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ALL_LOCATIONS, MatchingType.BEGINS_WITH,
				locale);
		assertEquals(7, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().toLowerCase().contains(queryTerm));
		}

		// all locations, EXACT
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ALL_LOCATIONS, MatchingType.EXACT,
				locale);
		assertEquals(3, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().toLowerCase().contains(queryTerm));
		}

		// all locations, CONTAINS
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ALL_LOCATIONS, MatchingType.CONTAINS,
				locale);
		assertEquals(14, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().toLowerCase().contains(queryTerm));
		}

		// only administrative locations, BEGINS_WITH
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ONLY_ADMINISTRATIVE_LOCATIONS, MatchingType.BEGINS_WITH,
				locale);
		assertEquals(7, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().toLowerCase().contains(queryTerm));
			//assertNotNull(location.getNativeKey());
			//TODO: bounding box not received during search
			//assertNotNull(location.getBoundingBox());
		}

		// english results
		queryTerm = "frankfurt";
		// TODO: locale = Locale.ENGLISH;
/*
		// all locations
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ALL_LOCATIONS, MatchingType.BEGINS_WITH,
				locale);
		assertEquals(5, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().toLowerCase().contains(queryTerm));
		}

		// only administrative locations
		locations = gazetteerService.findLocationsFromQueryTerm(queryTerm,
				QueryType.ONLY_ADMINISTRATIVE_LOCATIONS, MatchingType.EXACT,
				locale);
		assertEquals(4, locations.length);
		for (Location location : locations) {
			checkLocation(location, null, null);
			assertTrue(location.getName().toLowerCase().contains(queryTerm));
			assertNotNull(location.getNativeKey());
			assertNotNull(location.getBoundingBox());
		}
		*/
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
