/*
 * **************************************************-
 * ingrid-external-service-sns
 * ==================================================
 * Copyright (C) 2014 - 2022 wemove digital solutions GmbH
 * ==================================================
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 * 
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl5
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * **************************************************#
 */
package de.ingrid.external.sns;

import java.util.Locale;

import junit.framework.TestCase;
import de.ingrid.external.GazetteerService;
import de.ingrid.external.GazetteerService.MatchingType;
import de.ingrid.external.GazetteerService.QueryType;
import de.ingrid.external.om.Location;
import org.junit.Ignore;

// Chronic and gazetteer not supported anymore by SNS
@Ignore
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
        String locationId = "https://sns.uba.de/gazetteer/BERG41128"; // Großer Buchberg
        Locale locale = Locale.GERMAN;
        locations = gazetteerService.getRelatedLocationsFromLocation( locationId, true, locale );
        assertEquals( 6, locations.length );
        checkLocation( locations[0], locationId, "Gro\u00DFer Buchberg" );
        for (Location loc : locations) {
            checkLocation( loc );
            assertFalse( loc.getIsExpired() );
        }

        // valid location in german, DO NOT INCLUDE fromLocation
        locations = gazetteerService.getRelatedLocationsFromLocation( locationId, false, locale );
        assertEquals( 5, locations.length );
        for (Location loc : locations) {
            assertTrue( !locationId.equals( loc.getId() ) );
            checkLocation( loc );
            assertFalse( loc.getIsExpired() );
        }

        // TODO: in english ?
        locale = Locale.ENGLISH;
        locations = gazetteerService.getRelatedLocationsFromLocation( locationId, true, locale );
        // assertTrue(locations.length > 0);

        // EXPIRED !!!! INCLUDE fromLocation ! BUT IS REMOVED BECAUSE EXPIRED !!!!
        locationId = "https://sns.uba.de/gazetteer/GEMEINDE1515107014"; // Gehrden
        locale = Locale.GERMAN;
        locations = gazetteerService.getRelatedLocationsFromLocation( locationId, true, locale );
        assertEquals( 5, locations.length );
        for (Location loc : locations) {
            checkLocation( loc );
            assertFalse( "https://sns.uba.de/gazetteer/GEMEINDE0325300005".equals( loc.getId() ) );
            assertFalse( loc.getIsExpired() );
        }

        // valid location in german
        locationId = "https://sns.uba.de/gazetteer/NATURPARK31";
        locations = gazetteerService.getRelatedLocationsFromLocation( locationId, true, locale );
        assertTrue( locations.length > 0 );

        // TODO: in english ?
        locale = Locale.ENGLISH;
        locations = gazetteerService.getRelatedLocationsFromLocation( locationId, true, locale );
        // assertTrue(locations.length > 0);

        // INVALID location in german
        locationId = "https://sns.uba.de/gazetteer/wrong id";
        locale = Locale.GERMAN;
        locations = gazetteerService.getRelatedLocationsFromLocation( locationId, true, locale );
        assertTrue( locations.length == 0 );

        // in english ?
        locale = Locale.ENGLISH;
        locations = gazetteerService.getRelatedLocationsFromLocation( locationId, true, locale );
        assertTrue( locations.length == 0 );
    }

    public final void testGetLocation() {
        Location location;

        // valid location in german
        String locationId = "https://sns.uba.de/gazetteer/BERG41128"; // Großer Buchberg
        Locale locale = Locale.GERMAN;
        location = gazetteerService.getLocation( locationId, locale );
        checkLocation( location, locationId, "Gro\u00DFer Buchberg" );
        assertEquals( "Berg", location.getTypeName() );
        // TIP: if this has an error then the location is not correct from the SNS, see Ticket
        // https://github.com/innoq/iqvoc_gazetteer/issues/14
        assertEquals( 9.051591f, location.getBoundingBox()[0] );
        assertEquals( 50.152817f, location.getBoundingBox()[1] );
        assertFalse( location.getIsExpired() );

        // in english ? SAME NAME because locale ignored by SNS, id determines language !
        // NO ENGLISH LOCATIONS IN SNS !!!

        // TODO: wait for English support
		/*locale = Locale.ENGLISH;
		location = gazetteerService.getLocation(locationId, locale);
		checkLocation(location, locationId, "Gro\u00DFer Buchberg");
		assertFalse(location.getIsExpired());*/

        // valid location. NOTICE: locale ignored
        locationId = "https://sns.uba.de/gazetteer/NATURPARK31"; // Hessischer Spessart
        location = gazetteerService.getLocation( locationId, locale );
        checkLocation( location, locationId, "Hessischer Spessart" );
        assertEquals( "Naturpark", location.getTypeName() );
        assertFalse( location.getIsExpired() );

        // valid location. NOTICE: locale ignored
        locationId = "https://sns.uba.de/gazetteer/GEMEINDE0641200000"; // Frankfurt am Main
        location = gazetteerService.getLocation( locationId, locale );
        checkLocation( location, locationId, "Frankfurt am Main" );
        assertEquals( "Gemeinde", location.getTypeName() );
        assertEquals( "Gemeinde", location.getQualifier() );
        assertEquals( "06412000", location.getNativeKey() );
        assertNotNull( location.getBoundingBox() );
        assertEquals( 8.4673764f, location.getBoundingBox()[0] );
        assertEquals( 50.013846f, location.getBoundingBox()[1] );
        assertEquals( 8.8057514f, location.getBoundingBox()[2] );
        assertEquals( 50.227580f, location.getBoundingBox()[3] );
        assertFalse( location.getIsExpired() );

        // EXPIRED LOCATION !
        locationId = "https://sns.uba.de/gazetteer/GEMEINDE1510100000"; // Dessau
        location = gazetteerService.getLocation( locationId, locale );
        checkLocation( location, locationId, "Dessau" );
        assertTrue( location.getIsExpired() );
        // TODO: successor is "Dessau-Roßlau"!

        // INVALID location
        locationId = "https://sns.uba.de/gazetteer/wrong-id";
        locale = Locale.GERMAN;
        location = gazetteerService.getLocation( locationId, locale );
        assertNull( location );
    }

    public final void testGetLocationsFromText() {
        Location[] locations;

        // german locations
        String text = "Frankfurt Berlin Sachsenhausen Äppelwoi Handkäs Main";
        int analyzeMaxWords = 1000;
        boolean ignoreCase = true;
        Locale locale = Locale.GERMAN;

        locations = gazetteerService.getLocationsFromText( text, analyzeMaxWords, ignoreCase, locale );
        assertTrue( locations.length > 0 );
        Location checkLocation = null;
        for (Location location : locations) {
            if ("https://sns.uba.de/gazetteer/GEMEINDE1607103082".equals( location.getId() )) {
                checkLocation = location;
                break;
            }
        }
        assertNotNull( checkLocation );
        assertEquals( "https://sns.uba.de/gazetteer/GEMEINDE1607103082", checkLocation.getId() );
        assertEquals( "-location-admin-use6-", checkLocation.getTypeId() );
        assertEquals( "16071082", checkLocation.getNativeKey() );

        // english results
        text = "Frankfurt Main Sachsenhausen Airport";
        locale = Locale.ENGLISH;

        locations = gazetteerService.getLocationsFromText( text, analyzeMaxWords, ignoreCase, locale );
        // TODO: check if english is supported
        // assertTrue(locations.length > 0);
    }

    public final void testFindLocationsFromQueryTerm() {
        Location[] locations;

        // german locations
        String queryTerm = "berlin";
        Locale locale = Locale.GERMAN;

        // all locations, BEGINS_WITH
        locations = gazetteerService.findLocationsFromQueryTerm( queryTerm, QueryType.ALL_LOCATIONS, MatchingType.BEGINS_WITH, locale );
        assertEquals( 7, locations.length );
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( queryTerm ) );
        }

        // all locations, EXACT
        locations = gazetteerService.findLocationsFromQueryTerm( queryTerm, QueryType.ALL_LOCATIONS, MatchingType.EXACT, locale );
        assertEquals( 3, locations.length );
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( queryTerm ) );
        }

        // all locations, CONTAINS
        locations = gazetteerService.findLocationsFromQueryTerm( queryTerm, QueryType.ALL_LOCATIONS, MatchingType.CONTAINS, locale );
        assertEquals( 13, locations.length );
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( queryTerm ) );
        }

        // only administrative locations, CONTAINS
        locations = gazetteerService.findLocationsFromQueryTerm( queryTerm, QueryType.ONLY_ADMINISTRATIVE_LOCATIONS, MatchingType.CONTAINS, locale );
        assertEquals( 11, locations.length );
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( queryTerm ) );
            assertTrue( location.getTypeId().contains( "-admin-" ) );
            // assertNotNull(location.getNativeKey());
            assertNotNull( location.getBoundingBox() );
        }

        // only administrative locations, BEGINS_WITH
        locations = gazetteerService.findLocationsFromQueryTerm( queryTerm, QueryType.ONLY_ADMINISTRATIVE_LOCATIONS, MatchingType.BEGINS_WITH, locale );
        assertEquals( 6, locations.length );
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( queryTerm ) );
            assertTrue( location.getTypeId().contains( "-admin-" ) );
            // assertNotNull(location.getNativeKey());
            assertNotNull( location.getBoundingBox() );
        }

        // expired locations must be removed!
        queryTerm = "Dessau";
        locations = gazetteerService.findLocationsFromQueryTerm( queryTerm, QueryType.ALL_LOCATIONS, MatchingType.CONTAINS, locale );
        // assertEquals(2, locations.length);
        for (Location location : locations) {
            assertFalse( location.getIsExpired() );
        }

        // english results
        queryTerm = "frankfurt";
        // TODO: locale = Locale.ENGLISH;

        // all locations
        locations = gazetteerService.findLocationsFromQueryTerm( queryTerm, QueryType.ALL_LOCATIONS, MatchingType.BEGINS_WITH, locale );
        assertEquals( 4, locations.length );
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( queryTerm ) );
        }

        // only administrative locations
        locations = gazetteerService.findLocationsFromQueryTerm( queryTerm, QueryType.ONLY_ADMINISTRATIVE_LOCATIONS, MatchingType.BEGINS_WITH, locale );
        assertEquals( 4, locations.length );
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( queryTerm ) );
            assertNotNull( location.getNativeKey() );
            assertNotNull( location.getBoundingBox() );
        }

    }

    public final void testGetSuccessorFromExpired() {
        Location location = gazetteerService.getLocation( "https://sns.uba.de/gazetteer/GEMEINDE1510100000", new Locale( "de" ) );
        assertTrue( location.getIsExpired() );
        assertEquals( "2012-11-28", location.getExpiredDate() );
        assertEquals( 1, location.getSuccessorIds().length );
        assertEquals( "https://sns.uba.de/gazetteer/_4e9d66f0-1b80-0130-d0e8-482a1437a069", location.getSuccessorIds()[0] );
    }

    public final void testCheckTextEncoding() {
        Location location = gazetteerService.getLocation( "https://sns.uba.de/gazetteer/BIOSPHAERE3", new Locale( "de" ) );
        assertEquals( "Biosphärenreservat", location.getTypeName() );
    }

    public final void testWildcard() {
        Locale locale = Locale.GERMAN;

        // EXACT MATCH
        Location[] locations = gazetteerService.findLocationsFromQueryTerm( "berge", QueryType.ALL_LOCATIONS, MatchingType.EXACT, locale );
        assertEquals( 3, locations.length ); // the 4th is an expired location
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( "berge" ) );
        }

        // BEGINS_WITH MATCH
        locations = gazetteerService.findLocationsFromQueryTerm( "berge*", QueryType.ALL_LOCATIONS, MatchingType.BEGINS_WITH, locale );
        assertEquals( 18, locations.length );
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( "berge" ) );
        }

        // ENDS_WITH MATCH
        locations = gazetteerService.findLocationsFromQueryTerm( "*berge", QueryType.ALL_LOCATIONS, MatchingType.BEGINS_WITH, locale );
        assertEquals( 36, locations.length ); // is 206, but max returned is 40 (minus the expired ones)
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( "berge" ) );
        }

        // CONTAINS MATCH
        locations = gazetteerService.findLocationsFromQueryTerm( "*berge*", QueryType.ALL_LOCATIONS, MatchingType.BEGINS_WITH, locale );
        assertEquals( 35, locations.length ); // is 323, but max returned is 40 (minus the expired ones)
        for (Location location : locations) {
            checkLocation( location, null, null );
            assertTrue( location.getName().toLowerCase().contains( "berge" ) );
        }
    }

    private void checkLocation(Location location) {
        checkLocation( location, null, null );
    }

    private void checkLocation(Location location, String id, String name) {
        assertNotNull( location );
        assertNotNull( location.getId() );
        assertNotNull( location.getName() );
        if (id != null) {
            assertEquals( id, location.getId() );
        }
        if (name != null) {
            assertEquals( name, location.getName() );
        }
    }
}
