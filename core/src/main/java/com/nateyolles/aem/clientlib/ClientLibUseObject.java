package com.nateyolles.aem.clientlib;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import javax.script.Bindings;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.api.resource.Resource;

import com.day.cq.widget.ClientLibrary;
import com.day.cq.widget.HtmlLibraryManager;
import com.day.cq.widget.LibraryType;

import com.adobe.granite.xss.XSSAPI;

import org.slf4j.Logger;

import io.sightly.java.api.Use;

/**
 * Sightly Clientlibs that can accept attributes for 'defer', 'async' and 'onload'.
 *
 * This class is mostly code from /libs/granite/sightly/templates/ClientLibUseObjec.java file found
 * in your default AEM instance. The differences are that this class gets the 'loading' and 'onload'
 * attributes, gets the categories retrieved from
 * {@link com.day.cq.widget.HtmlLibraryManager#getLibraries(String[], LibraryType, boolean, boolean)}
 * and writes it's own HTML script elements rather than have the HtmlLibrary manger do it for us using
 * {@link com.day.cq.widget.HtmlLibraryManager#writeIncludes(SlingHttpServletRequest, Writer, String...)}.
 *
 * @author    Nate Yolles <yolles@adobe.com>
 * @version   0.1
 * @since     2015-03-19
 * @see       libs.granite.sightly.templates.ClientLibUseObject
 * @see       com.day.cq.widget.HtmlLibraryManager
 */
public class ClientLibUseObject implements Use {

    private static final String BINDINGS_CATEGORIES = "categories";
    private static final String BINDINGS_MODE = "mode";

    /**
     * Sightly parameter that becomes the script element void attribute such as 'defer' and 'async'.
     * Valid values are listed in {@link #VALID_ATTRIBUTES}.
     */
    private static final String BINDINGS_ATTRIBUTE = "loading";

    /**
     * Sightly parameter that becomes the javascript function value in the script element's 'onload'
     * attribute.
     */
    private static final String BINDINGS_ONLOAD = "onload";

    /**
     * HTML markup for javascript. Add 'type="text/javascript"' if you are not using HTML 5.
     */
    private static final String TAG_JAVASCRIPT = "<script src=\"%s\"%s></script>";

    /**
     * HTML markup for stylesheets.
     */
    private static final String TAG_STYLESHEET = "<link rel=\"stylesheet\" href=\"%s\">";

    /**
     * HTML markup for onload attribute of script element.
     */
    private static final String ONLOAD_ATTRIBUTE = " onload=\"%s\"";

    /**
     * Valid void attributes for HTML markup of script element.
     */
    private static final List<String> VALID_ATTRIBUTES = new ArrayList<String>(){{
        add("async");
        add("defer");
    }};

    private HtmlLibraryManager htmlLibraryManager = null;
    private String[] categories;
    private String mode;
    private String additionalAttribute;
    private String onloadAttribute;
    private SlingHttpServletRequest request;
    private PrintWriter out;
    private Logger log;
    private Resource resource;
    private XSSAPI xssAPI;

    /**
     * Same as AEM provided method with the addition of getting the XSSAPI service.
     * 
     * @see libs.granite.sightly.templates.ClientLibUseObject#init(Bindings)
     */
    public void init(Bindings bindings) {
        additionalAttribute = (String) bindings.get(BINDINGS_ATTRIBUTE);
        onloadAttribute = (String) bindings.get(BINDINGS_ONLOAD);
        resource = (Resource) bindings.get("resource");

        Object categoriesObject = bindings.get(BINDINGS_CATEGORIES);
        if (categoriesObject != null) {
            if (categoriesObject instanceof Object[]) {
                Object[] categoriesArray = (Object[]) categoriesObject;
                categories = new String[categoriesArray.length];
                int i = 0;
                for (Object o : categoriesArray) {
                    if (o instanceof String) {
                        categories[i++] = ((String) o).trim();
                    }
                }
            } else if (categoriesObject instanceof String) {
                categories = ((String) categoriesObject).split(",");
                int i = 0;
                for (String c : categories) {
                    categories[i++] = c.trim();
                }
            }
            if (categories != null && categories.length > 0) {
                mode = (String) bindings.get(BINDINGS_MODE);
                request = (SlingHttpServletRequest) bindings.get(SlingBindings.REQUEST);
                log = (Logger) bindings.get(SlingBindings.LOG);
                SlingScriptHelper sling = (SlingScriptHelper) bindings.get(SlingBindings.SLING);
                htmlLibraryManager = sling.getService(HtmlLibraryManager.class);
                xssAPI = sling.getService(XSSAPI.class);
            }
        }
    }

    /**
     * Essentially the same as the AEM provided method with the exception that the
     * HtmlLibraryManger's writeIncludes methods have been replaced.
     * 
     * @see libs.granite.sightly.templates.ClientLibUseObject#include()
     */
    public String include() {
        StringWriter sw = new StringWriter();

        if (categories == null || categories.length == 0)  {
            log.error("'categories' option might be missing from the invocation of the /apps/beagle/sightly/templates/clientlib.html" +
                    "client libraries template library. Please provide a CSV list or an array of categories to include.");
        } else {
            PrintWriter out = new PrintWriter(sw);
            if ("js".equalsIgnoreCase(mode)) {
                includeLibraries(out, LibraryType.JS);
            } else if ("css".equalsIgnoreCase(mode)) {
                includeLibraries(out, LibraryType.CSS);
            } else {
                includeLibraries(out, LibraryType.CSS);
                includeLibraries(out, LibraryType.JS);
            }
        }

        return sw.toString();
    }

    /**
     * Construct the HTML markup for the script and link elements.
     *
     * @param out The PrintWriter object responsible for writing the HTML.
     * @param LibraryType The library type either CSS or JS.
     */
    private void includeLibraries(PrintWriter out, LibraryType libraryType) {
        if (htmlLibraryManager != null && libraryType != null && xssAPI != null) { 
            Collection<ClientLibrary> libs = htmlLibraryManager.getLibraries(categories, libraryType, false, false);

            if (libraryType.equals(LibraryType.JS)) {
                String attribute = StringUtils.EMPTY;

                if (StringUtils.isNotBlank(additionalAttribute) && VALID_ATTRIBUTES.contains(additionalAttribute.toLowerCase())) {
                    attribute = " ".concat(additionalAttribute.toLowerCase());
                }

                if (StringUtils.isNotBlank(onloadAttribute)) {
                    String safeOnload = xssAPI.encodeForHTMLAttr(onloadAttribute);

                    if (StringUtils.isNotBlank(safeOnload)) {
                        attribute = attribute.concat(String.format(ONLOAD_ATTRIBUTE, safeOnload));
                    }
                }

                for (ClientLibrary lib : libs) {
                    out.write(String.format(TAG_JAVASCRIPT, lib.getIncludePath(libraryType, htmlLibraryManager.isMinifyEnabled()), attribute));
                }
            } else if (libraryType.equals(LibraryType.CSS)) {
                for (ClientLibrary lib : libs) {
                    out.write(String.format(TAG_STYLESHEET, lib.getIncludePath(libraryType, htmlLibraryManager.isMinifyEnabled())));
                }
            }
        }
    }
}