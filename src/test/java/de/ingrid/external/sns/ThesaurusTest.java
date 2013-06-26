package de.ingrid.external.sns;

import java.util.Locale;

import junit.framework.TestCase;
import de.ingrid.external.ThesaurusService;
import de.ingrid.external.ThesaurusService.MatchingType;
import de.ingrid.external.om.RelatedTerm;
import de.ingrid.external.om.Term;
import de.ingrid.external.om.TreeTerm;
import de.ingrid.external.om.RelatedTerm.RelationType;
import de.ingrid.external.om.Term.TermType;

public class ThesaurusTest extends TestCase {
	
	private ThesaurusService thesaurusService;
	
	public void setUp() {
		SNSService snsService = new SNSService();
		try {
			snsService.init();
		} catch (Exception e) {
			e.printStackTrace();
		}
		thesaurusService = snsService;
	}
	
	public final void testFindTermsFromQueryTerm() {
		Term[] terms;

		// german locations
		String queryTerm = "Wasser";
		MatchingType matchingType = MatchingType.BEGINS_WITH;
		boolean addDescriptors = false;
		Locale locale = Locale.GERMAN;
		
		terms = thesaurusService.findTermsFromQueryTerm(queryTerm, matchingType, addDescriptors, locale);
		assertNotNull(terms);
		assertEquals(40, terms.length);
		for (Term term : terms) {
			checkTerm(term, null, null, null);
		}

		matchingType = MatchingType.EXACT;
		terms = thesaurusService.findTermsFromQueryTerm(queryTerm, matchingType, addDescriptors, locale);
		assertNotNull(terms);
		assertEquals(1, terms.length);
		for (Term term : terms) {
			checkTerm(term, null, null, null);
		}

		/*addDescriptors = true;
		terms = thesaurusService.findTermsFromQueryTerm(queryTerm, matchingType,
				addDescriptors, locale);
		assertNotNull(terms);
		assertEquals(26, terms.length);
		for (Term term : terms) {
			checkTerm(term, null, null, null);
		}*/

		// english results
		queryTerm = "water";
		matchingType = MatchingType.BEGINS_WITH;
		locale = Locale.ENGLISH;

		terms = thesaurusService.findTermsFromQueryTerm(queryTerm, matchingType, addDescriptors, locale);
		assertNotNull(terms);
		assertEquals(40, terms.length);
		for (Term term : terms) {
			checkTerm(term, null, null, null);
		}
	}

	public final void testGetTerm() {
		Term term;

		// DESCRIPTOR term in german
		//String termId = "http://data.uba.de/umt/_00008424"; // Entwicklungshilfe
		String termId = "http://umthes.innoq.com/_00007946"; // Emission
		Locale locale = Locale.GERMAN;
		term = thesaurusService.getTerm(termId, locale);
		checkTerm(term, termId, TermType.DESCRIPTOR, "Emission");

		// in english 
		locale = Locale.ENGLISH;
		term = thesaurusService.getTerm(termId, locale);
		// TODO: checkTerm(term, termId, TermType.DESCRIPTOR, "emission");
		
		// GEMET
		termId = "http://umthes.innoq.com/_00028954";
		locale = Locale.GERMAN;
		term = thesaurusService.getTerm(termId, locale);
		assertEquals("GEMETID9242", term.getAlternateId());

		// in english ? NOTICE: has different ID ! locale ignored in SNS
/*		termId = "t17b6643_115843ddf08_4681"; // forest deterioration
		term = thesaurusService.getTerm(termId, locale);
		checkTerm(term, termId, TermType.NON_DESCRIPTOR, "forest deterioration");

		// DESCRIPTOR term. NOTICE: locale ignored !
		termId = "uba_thes_27061"; // Waldschaden
		term = thesaurusService.getTerm(termId, locale);
		checkTerm(term, termId, TermType.DESCRIPTOR, "Waldschaden");

		// NODE_LABEL term
		termId = "uba_thes_41004"; // Abfall
		term = thesaurusService.getTerm(termId, locale);
		checkTerm(term, termId, TermType.NODE_LABEL, "Abfall");

		// TOP NODE_LABEL term (topTermType)
		termId = "uba_thes_49268"; // Schadstoffe und Abfälle, Umweltverschmutzung
		term = thesaurusService.getTerm(termId, locale);
		checkTerm(term, termId, TermType.NODE_LABEL, "Schadstoffe und Abf\u00e4lle, Umweltverschmutzung");
*/		
		// INVALID term
		termId = "wrong id";
		term = thesaurusService.getTerm(termId, locale);
		assertNull(term);
	}

	public final void testGetSimilarTermsFromNames() {
		Term[] terms;

		// german term
		String name = "Wald";
		Locale locale = Locale.GERMAN;
		
		terms = thesaurusService.getSimilarTermsFromNames(new String[] { name }, true, locale);
		assertTrue(terms.length > 0);
		for (Term term : terms) {
			checkTerm(term);
		}

		// english term
		// TODO: not supported yet!
		name = "water";
		locale = Locale.ENGLISH;

		terms = thesaurusService.getSimilarTermsFromNames(new String[] { name }, true, locale);
		assertTrue(terms.length == 0);
		for (Term term : terms) {
			checkTerm(term);
		}
	}

	public final void testGetTermsFromText() {
		Term[] terms;

		// german locations
		String text = "Waldsterben";
		int analyzeMaxWords = 1000;
		boolean ignoreCase = true;
		Locale locale = Locale.GERMAN;
		
		terms = thesaurusService.getTermsFromText(text, analyzeMaxWords, ignoreCase, locale);
		assertTrue(terms.length > 0);
		for (Term term : terms) {
			checkTerm(term, null, TermType.DESCRIPTOR, null);
		}

		// english results
		text = "forest deterioration";
		locale = Locale.ENGLISH;

		terms = thesaurusService.getTermsFromText(text, analyzeMaxWords, ignoreCase, locale);
		// normally we get results ! but we also had no results !? ignore result ! 
/*
		assertTrue(terms.length > 0);
//		assertTrue(terms.length == 0);
		for (Term term : terms) {
			checkTerm(term, null, TermType.DESCRIPTOR, null);
		}
*/
	}

	public final void testGetRelatedTermsFromTerm() {
		RelatedTerm[] relatedTerms;

		// DESCRIPTOR term in german
		String termId = "http://umthes.innoq.com/_00021949"; // Schrott
		Locale locale = Locale.GERMAN;
		relatedTerms = thesaurusService.getRelatedTermsFromTerm(termId, locale);
		assertTrue(relatedTerms.length > 0);
		for (RelatedTerm relTerm : relatedTerms) {
			checkRelatedTerm(relTerm);
		}

		// in english ? SAME RESULTS because locale ignored by SNS, id determines language ! 
		locale = Locale.ENGLISH;
		relatedTerms = thesaurusService.getRelatedTermsFromTerm(termId, locale);
		// TODO: English 
		/*assertTrue(relatedTerms.length > 0);
		for (RelatedTerm relTerm : relatedTerms) {
			checkRelatedTerm(relTerm);
		}

		// in english ? NOTICE: has different ID ! locale ignored in SNS
		termId = "t16e1782_1225eb9489f_-6afd"; // water
		locale = Locale.ENGLISH;
		relatedTerms = thesaurusService.getRelatedTermsFromTerm(termId, locale);
		// NO ENGLISH PROCESSING ! 
		assertTrue(relatedTerms.length == 0);

		// DESCRIPTOR term. NOTICE: locale ignored !
		termId = "uba_thes_27061"; // Waldschaden
		relatedTerms = thesaurusService.getRelatedTermsFromTerm(termId, locale);
		assertTrue(relatedTerms.length > 0);
		for (RelatedTerm relTerm : relatedTerms) {
			checkRelatedTerm(relTerm);
		}
		*/

		// INVALID term
		termId = "http://umthes.innoq.com/wrong-id";
		relatedTerms = thesaurusService.getRelatedTermsFromTerm(termId, locale);
		assertNotNull(relatedTerms);
		assertTrue(relatedTerms.length == 0);
	}

	public final void testGetHierarchyNextLevel() {
		TreeTerm[] treeTerms;

		// TOP TERMS in german
		String termId = null;
		Locale locale = Locale.GERMAN;
/*		treeTerms = thesaurusService.getHierarchyNextLevel(termId, locale);
		assertTrue(treeTerms.length > 0);
		for (TreeTerm treeTerm : treeTerms) {
			// all top terms have null as parents and do have children !
			assertNull(treeTerm.getParents());
			checkTreeTerm(treeTerm, false, true);
		}

		// in english ? NO RESULTS only german supported by SNS ! 
		locale = Locale.ENGLISH;
		treeTerms = thesaurusService.getHierarchyNextLevel(termId, locale);
		assertTrue(treeTerms.length > 0);
*/
		// SUB TERMS of top term
		termId = "http://umthes.innoq.com/_00049251"; // Atmosphäre und Klima
		locale = Locale.GERMAN;
		treeTerms = thesaurusService.getHierarchyNextLevel(termId, locale);
		assertTrue(treeTerms.length > 0);
		for (TreeTerm treeTerm : treeTerms) {
			// NOT checking children of children, there are leafs !  but check parent (ALWAYS SET)
			checkTreeTerm(treeTerm, true, false);
			// parent is term with passed id !
			assertEquals(termId, treeTerm.getParents().get(0).getId());
		}

		// SUB TERMS of leaf
		termId = "http://umthes.innoq.com/_00023126"; // Stadtklima
		treeTerms = thesaurusService.getHierarchyNextLevel(termId, locale);
		assertNotNull(treeTerms);
		assertTrue(treeTerms.length == 0);

/*
		// INVALID term, SNS throws Exception !
		termId = "wrong id";
		treeTerms = thesaurusService.getHierarchyNextLevel(termId, locale);
		assertNotNull(treeTerms);
		assertTrue(treeTerms.length == 0);
*/
	}

	public final void testGetHierarchyPathToTop() {
		TreeTerm startTerm;

		// PATH OF SUB TERM in german
		String termId = "http://umthes.innoq.com/_00023126"; // Stadtklima 
		Locale locale = Locale.GERMAN;
		startTerm = thesaurusService.getHierarchyPathToTop(termId, locale);
		// start term is term with requested id
		assertEquals(termId, startTerm.getId());
		// has 1 parent
		assertTrue(startTerm.getParents().size() == 1);
		// all parents have further parent and also start term as child
		for (TreeTerm parentTerm : startTerm.getParents()) {
			checkTreeTerm(parentTerm, true, true);
		}

		// PATH OF TOP TERM
		termId = "http://umthes.innoq.com/_00049251"; // Atmosphäre und Klima
		startTerm = thesaurusService.getHierarchyPathToTop(termId, locale);
		// start term is term with requested id
		assertEquals(termId, startTerm.getId());
		// has NO parent
		assertNull(startTerm.getParents());
		
		// PATH OF DEEP SUB TERM
		termId = "http://umthes.innoq.com/_00047197"; // Organo-Ton
		startTerm = thesaurusService.getHierarchyPathToTop(termId, locale);
		// start term is term with requested id
		assertEquals(termId, startTerm.getId());
		String[] expectedPath = {"Organo-Ton", "Bentonit", "Ton [Mineral]", "Sedimentgestein", "Gestein"};
		TreeTerm currentTerm = startTerm;
		for (String expectedParent : expectedPath) {
			assertNotNull("No further parent element when was expected: " + expectedParent, currentTerm);
			assertEquals(expectedParent, currentTerm.getName());
			currentTerm = currentTerm.getParents() != null ? currentTerm.getParents().get(0) : null;
		}
		
		
/*
		// in english ? NO RESULTS only german supported by SNS !
		termId = "t16e1782_1225eb9489f_-6afd"; // water
		locale = Locale.ENGLISH;
		treeTerms = thesaurusService.getHierarchyPathToTop(termId, locale);
		assertTrue(treeTerms.length == 0);

		// INVALID term, SNS throws Exception !
		termId = "wrong id";
		locale = Locale.GERMAN;
		treeTerms = thesaurusService.getHierarchyPathToTop(termId, locale);
		assertNotNull(treeTerms);
		assertTrue(treeTerms.length == 0);
*/
	}

	private void checkTerm(Term term) {
		checkTerm(term, null, null, null);
	}
	private void checkTerm(Term term, String id, TermType type, String name) {
		assertNotNull(term);
		assertNotNull(term.getId());
		assertNotNull(term.getType());
		assertNotNull(term.getName());
		if (id != null) {
			assertEquals(id, term.getId());			
		}
		if (type != null) {
			assertEquals(type, term.getType());
		}
		if (name != null) {
			assertEquals(name, term.getName());
		}
	}

	private void checkRelatedTerm(RelatedTerm term) {
		checkRelatedTerm(term, null, null, null, null);
	}
	private void checkRelatedTerm(RelatedTerm relTerm, RelationType relType,
			String termId, TermType termType, String termName) {
		if (relType != null) {
			assertEquals(relType, relTerm.getRelationType());			
		}
		checkTerm(relTerm, termId, termType, termName);
	}

	private void checkTreeTerm(TreeTerm term,
			boolean checkParent, boolean checkChildren) {
		if (checkParent) {
			assertNotNull(term.getParents());
		}
		if (checkChildren) {
			assertNotNull(term.getChildren());
		}
		checkTerm(term);
	}
}
