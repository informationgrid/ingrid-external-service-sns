package de.ingrid.external.sns;

import java.util.Locale;

import junit.framework.TestCase;
import de.ingrid.external.ThesaurusService;
import de.ingrid.external.om.RelatedTerm;
import de.ingrid.external.om.Term;
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
	
	public final void testGetTerm() {
		Term term;

		// NON_DESCRIPTOR term in german
		String termId = "uba_thes_27074"; // Waldsterben
		Locale locale = Locale.GERMAN;
		term = thesaurusService.getTerm(termId, locale);
		checkTerm(term, termId, TermType.NON_DESCRIPTOR, "Waldsterben");

		// in english ? SAME NAME because locale ignored by SNS, id determines language ! 
		locale = Locale.ENGLISH;
		term = thesaurusService.getTerm(termId, locale);
		checkTerm(term, termId, TermType.NON_DESCRIPTOR, "Waldsterben");

		// in english ? NOTICE: has different ID ! locale ignored in SNS
		termId = "t17b6643_115843ddf08_4681"; // forest deterioration
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
		checkTerm(term, termId, TermType.NODE_LABEL, "Schadstoffe und Abfälle, Umweltverschmutzung");
		
		// INVALID term
		termId = "wrong id";
		term = thesaurusService.getTerm(termId, locale);
		assertNull(term);
	}

	public final void testGetTermsFromName() {
		Term[] terms;

		// german term
		String name = "Wasser";
		Locale locale = Locale.GERMAN;
		
		terms = thesaurusService.getTermsFromName(name, locale);
		assertTrue(terms.length > 0);
		for (Term term : terms) {
			checkTerm(term);
		}

		// english term
		name = "water";
		locale = Locale.ENGLISH;

		terms = thesaurusService.getTermsFromName(name, locale);
		assertTrue(terms.length > 0);
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
		assertTrue(terms.length > 0);
		for (Term term : terms) {
			checkTerm(term, null, TermType.DESCRIPTOR, null);
		}
	}

	public final void testGetRelatedTermsFromTerm() {
		RelatedTerm[] relatedTerms;

		// NON_DESCRIPTOR term in german
		String termId = "uba_thes_27074"; // Waldsterben
		Locale locale = Locale.GERMAN;
		relatedTerms = thesaurusService.getRelatedTermsFromTerm(termId, locale);
		assertTrue(relatedTerms.length > 0);
		for (RelatedTerm relTerm : relatedTerms) {
			checkRelatedTerm(relTerm);
		}

		// in english ? SAME RESULTS because locale ignored by SNS, id determines language ! 
		locale = Locale.ENGLISH;
		relatedTerms = thesaurusService.getRelatedTermsFromTerm(termId, locale);
		assertTrue(relatedTerms.length > 0);
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

		// INVALID term
		termId = "wrong id";
		relatedTerms = thesaurusService.getRelatedTermsFromTerm(termId, locale);
		assertNotNull(relatedTerms);
		assertTrue(relatedTerms.length == 0);
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
}
