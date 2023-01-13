/*
 * **************************************************-
 * ingrid-external-service-sns
 * ==================================================
 * Copyright (C) 2014 - 2023 wemove digital solutions GmbH
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

import org.apache.jena.rdf.model.*;

import java.util.ArrayList;
import java.util.List;

public class RDFUtils {

    public static String getName(Resource res, String lang) {
		RDFNode node = getObject(res, "skos", "prefLabel", lang);
		if (node == null) node = getObject(res, "skosxl", "prefLabel", lang);
		if (node == null) node = getObject(res, "skos", "officialName", lang);
		if (node == null) node = getObject(res, "http://www.geonames.org/ontology", "officialName", lang);
		if (node == null) node = getObject(res, "skos", "altLabel", lang);
		// for autoclassify results
		if (node == null) node = getObject(res, "dct", "title");

        if (node != null) {
            return node.asNode().getLiteralValue().toString();
        }
        return null;
	}

    public static String getId(Resource res) {
		RDFNode node = getObject(res, "sdc", "link");
        if (node != null) {
            return node.toString();
        }
        return res.getURI();

	}

    public static NodeIterator getChildren(Model model) {
        return getObjects(model, "skos", "narrower");
    }
    public static ResIterator getChildrenAsRes(Resource resource) {
    	return getResources(resource.getModel(), "skos", "narrower");
    }

    public static StmtIterator getChildren(Resource res) {
        return getObjects(res, "skos", "narrower");
    }

    public static NodeIterator getMembers(Model model) {
        return getObjects(model, "skos", "member");
    }

	public static NodeIterator getTopConcepts(Model model) {
		return getObjects(model, "skos", "hasTopConcept");
	}
	public static ResIterator getTopConceptsOf(Model model) {
		return getResources(model, "skos", "topConceptOf");
	}
	public static boolean isTopConcept(Resource res) {
		return getObject(res, "skos", "topConceptOf") != null;
	}

    public static RDFNode getParent(Model model) {
        return getObject(model, "skos", "broader");
    }

    public static RDFNode getParent(Resource resource) {
    	return getObject(resource, "skos", "broader");
    }

    public static StmtIterator getParents(Resource resource) {
    	return getObjects(resource, "skos", "broader");
    }

    private static RDFNode getObject(Model model, String namespace, String name) {
        NodeIterator it = getObjects(model, namespace, name);
        if (it.hasNext()) {
            return it.next();
        }
        return null;
    }

    private static RDFNode getObject(Resource res, String namespace, String name) {
    	String nsURI = res.getModel().getNsPrefixURI(namespace);
        Property prop = res.getModel().createProperty(nsURI + name);
        Statement stmt = res.getProperty(prop);
        return stmt != null ? stmt.getObject() : null;
    }

    private static RDFNode getObject(Resource res, String namespace, String name, String lang) {
    	String nsURI = res.getModel().getNsPrefixURI(namespace);
    	if ( nsURI == null ) nsURI = namespace;
        Property prop = res.getModel().createProperty(nsURI + name);
        StmtIterator stmts = res.listProperties(prop);
        while (stmts.hasNext()) {
        	Statement stmt = stmts.next();
        	try {
        	    if (stmt.getLanguage().equals(lang)) {
        	        return stmt.getObject();
        	    }
        	} catch (LiteralRequiredException e) {
        	    continue;
        	}
        }
        return null;
    }

    private static NodeIterator getObjects(Model model, String namespace, String name) {
        String nsURI = model.getNsPrefixURI(namespace);
        Property prop = model.createProperty(nsURI + name);
        return model.listObjectsOfProperty(prop);
    }

    private static ResIterator getResources(Model model, String namespace, String name) {
    	String nsURI = model.getNsPrefixURI(namespace);
    	Property prop = model.createProperty(nsURI + name);
    	return model.listResourcesWithProperty(prop);
    }

    private static StmtIterator getObjects(Resource res, String namespace, String name) {
        String nsURI = res.getModel().getNsPrefixURI(namespace);
        Property prop = res.getModel().createProperty(nsURI + name);
        return res.listProperties(prop);
    }

	public static NodeIterator getResults(Resource searchResults) {
		return getObjects(searchResults.getModel(), "sdc", "result");
	}

	public static List<String> getAltLabels(Resource searchResults, String lang) {
		List<String> labels = new ArrayList<String>();
		StmtIterator stmts = getObjects(searchResults, "skos", "altLabel");
		if (!stmts.hasNext()) {
		    stmts = getObjects(searchResults, "skosxl", "altLabel");
		}
		if (!stmts.hasNext()) {
		    stmts = getObjects(searchResults, "skos", "usedForCombination");
		}

		while (stmts.hasNext()) {
        	Statement stmt = stmts.next();
        	try {
            	if (stmt.getLanguage().equals(lang)) {
            		labels.add(stmt.getObject().asNode().getLiteralValue().toString());
            	}
        	} catch (LiteralRequiredException e) {
        	    continue;
        	}
        }
		return labels;
	}

	public static ResIterator getConcepts(Model model) {
		return getResources(model, "rdf", "type");
	}

	public static String getType(Resource res) {
	    RDFNode node = getObject(res, "rdf", "type");
	    if (node != null) {
            return node.asNode().getURI();
        }
	    return null;
	}

	public static String getDefinition(Resource res, String lang) {
		RDFNode node = getObject(res, "skos", "definition", lang);
        if (node != null) {
            return node.asNode().getLiteralValue().toString();
        }
        return null;
	}

	public static float[] getBoundingBox(Resource res) {
		//RDFNode bb = getObject(res, "gn", "boundingBox");
		float[] bbFloat = null;
		String nsURI = "http://www.geonames.org/ontology";
        Property prop = res.getModel().createProperty(nsURI + "boundingBox");
        StmtIterator stmts = res.listProperties(prop);
        while (stmts.hasNext()) {
        	Statement bb = stmts.next();
			String preparedCoordinates = "";
			String value = bb.getObject().toString();
			// bounding boxes have the format "x1,y1 x2,y2"
			String[] coordinates = value.split(" ");
			if (coordinates.length == 1) {
				preparedCoordinates = coordinates[0];
			} else if (coordinates.length == 2) {
				preparedCoordinates = coordinates[0] + "," + coordinates[1];
			}
			String[] coordinatesSplitted = preparedCoordinates.split(",");

			// transform to floats
			bbFloat = new float[coordinatesSplitted.length];
			for (int i = 0; i < coordinatesSplitted.length; i++) {
				bbFloat[i] = Float.valueOf(coordinatesSplitted[i]);
			}
			// prefer real bounding box instead of coordinate
			if (bbFloat.length == 4) break;
		}
        return bbFloat;
	}

	public static String getMemberOf(Resource topic) {
		RDFNode node = getObject(topic, "schema", "memberOf");
		if (node == null) return null;
		return node.asNode().getURI();
	}

	public static String getGemetRef(Resource res) {
		RDFNode node = getObject(res, "skos", "closeMatch");
		if (node != null) {
            return node.asNode().getURI();
        }
		return null;
	}

	public static StmtIterator getRelatedConcepts(Resource res) {
		return getObjects(res, "skos", "related");
	}

	public static String getEventId(Resource res) {
		RDFNode node = getObject(res, "sdc", "link");
		if (node != null) {
            return node.asNode().getURI();
        }
		return null;
	}

	public static String getDateStart(Resource res) {
		RDFNode object = getObject(res, "dct", "temporal");
		if (object != null) {
			Resource temporalRes = object.asResource();
			RDFNode node = getObject(temporalRes, "dct", "start");
			if (node != null) {
				return node.asNode().getLiteralValue().toString();
			}
		}
		return null;
	}

	public static String getDateEnd(Resource res) {
		RDFNode object = getObject(res, "dct", "temporal");
		if (object != null) {
			Resource temporalRes = object.asResource();
			RDFNode node = getObject(temporalRes, "dct", "end");
			if (node != null) {
				return node.asNode().getLiteralValue().toString();
			}
		}
		return null;
	}

	public static StmtIterator getFurtherInfo(Resource res) {

		return getObjects(res, "rdfs", "seeAlso");
	}

	public static String getDctTitle(Resource info) {
		RDFNode node = getObject(info, "dct", "title");
		if (node != null) {
			return node.asNode().getLiteralValue().toString();
		}
		return null;
	}

	public static String getDctPage(Resource info) {
		RDFNode node = getObject(info, "foaf", "page");
		if (node != null) {
			if (node.asNode().isURI())
				return node.asNode().getURI();
			else
				return node.asNode().toString();
		}
		return null;
	}

	public static String getExpireDate(Resource res) {
		RDFNode node = getObject(res, "schema", "expires");
		if (node != null) {
			return node.asNode().getLiteralValue().toString();
		}
		return null;
	}

	public static String getNativeKey(Resource topic, String keyPrefix) {
		StmtIterator notations = getObjects(topic, "skos", "notation");
		while (notations.hasNext()) {
			Statement stmt = notations.next();
			if (stmt.getLiteral().getDatatypeURI().contains(keyPrefix))
				return stmt.getLiteral().getLexicalForm();
		}
		return null;
	}

	public static int getTotalResults(Model model) {
		RDFNode node = getObject(model, "sdc", "totalResults");
		if (node != null) {
			return Integer.valueOf(node.asNode().getLiteralValue().toString());
		}
		return 0;
	}

    public static String[] getSuccessors( Resource topic ) {
        StmtIterator successors = getObjects( topic, "gn", "successor" );
        List<String> succList = new ArrayList<String>();
        while (successors.hasNext()) {
            Statement successor = successors.next();
            succList.add( successor.getObject().asNode().getURI() );
        }

        return succList.toArray(new String[0]);
    }

}
