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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlUtils {

	public static String getHtmlDocLanguage(String doc) {
    	Pattern p = Pattern.compile("<html.*lang=\"(.*?)\"");
        Matcher m = p.matcher(doc);
        if (m.find() == true) return m.group(1);
    	return "de";
	}

	public static String getHtmlTagContent(String doc, String tag) {
    	Pattern p = Pattern.compile("<"+tag+">(.*?)</"+tag+">");
        Matcher m = p.matcher(doc);
        if (m.find() == true) return m.group(1);
    	return "";
    }
	public static String getHtmlMetaTagContent(String doc, String tag) {
    	Pattern p = Pattern.compile(".*<meta name=\""+tag+"\" content=\"(.*?)\"");
        Matcher m = p.matcher(doc);
        if (m.find() == true) return m.group(1);
    	return "";
    }

	public static String prepareUrl(String url) {
	    if (url.endsWith( "/" )) 
	        return url;
	    else 
			return url + "/";
	}
}

