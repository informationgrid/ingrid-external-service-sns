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
		int pos = url.lastIndexOf('/');
		if (pos != url.length()) {
			return url + "/";
		}
		return url;
	}
}

