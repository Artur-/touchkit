/*
 * Copyright 2011 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.terminal.gwt.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.vaadin.Application;
import com.vaadin.RootRequiresMoreInformationException;
import com.vaadin.Version;
import com.vaadin.external.json.JSONException;
import com.vaadin.external.json.JSONObject;
import com.vaadin.terminal.DeploymentConfiguration;
import com.vaadin.terminal.PaintException;
import com.vaadin.terminal.RequestHandler;
import com.vaadin.terminal.WrappedRequest;
import com.vaadin.terminal.WrappedResponse;
import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.ui.Root;

/**
 * FIXME This class needs to be removed and viewport settings handled somehow properly
 *
 */
public abstract class BootstrapHandler implements RequestHandler {

    protected class BootstrapContext implements Serializable {

        private final WrappedResponse response;
        private final WrappedRequest request;
        private final Application application;
        private final Integer rootId;

        private Writer writer;
        private Root root;
        private String widgetsetName;
        private String themeName;
        private String appId;

        private boolean rootFetched = false;

        public BootstrapContext(WrappedResponse response,
                WrappedRequest request, Application application, Integer rootId) {
            this.response = response;
            this.request = request;
            this.application = application;
            this.rootId = rootId;
        }

        public WrappedResponse getResponse() {
            return response;
        }

        public WrappedRequest getRequest() {
            return request;
        }

        public Application getApplication() {
            return application;
        }

        public Writer getWriter() throws IOException {
            if (writer == null) {
                response.setContentType("text/html");
                writer = new BufferedWriter(new OutputStreamWriter(
                        response.getOutputStream(), "UTF-8"));
            }
            return writer;
        }

        public Integer getRootId() {
            return rootId;
        }

        public Root getRoot() {
            if (!rootFetched) {
                root = Root.getCurrent();
                rootFetched = true;
            }
            return root;
        }

        public String getWidgetsetName() {
            if (widgetsetName == null) {
                Root root = getRoot();
                if (root != null) {
                    widgetsetName = getWidgetsetForRoot(this);
                }
            }
            return widgetsetName;
        }

        public String getThemeName() {
            if (themeName == null) {
                Root root = getRoot();
                if (root != null) {
                    themeName = findAndEscapeThemeName(this);
                }
            }
            return themeName;
        }

        public String getAppId() {
            if (appId == null) {
                appId = getApplicationId(this);
            }
            return appId;
        }

    }

    @Override
    public boolean handleRequest(Application application,
            WrappedRequest request, WrappedResponse response)
            throws IOException {

        // TODO Should all urls be handled here?
        Integer rootId = null;
        try {
            Root root = application.getRootForRequest(request);
            if (root == null) {
                writeError(response, new Throwable("No Root found"));
                return true;
            }

            rootId = Integer.valueOf(root.getRootId());
        } catch (RootRequiresMoreInformationException e) {
            // Just keep going without rootId
        }

        try {
            writeBootstrapPage(request, response, application, rootId);
        } catch (JSONException e) {
            writeError(response, e);
        }

        return true;
    }

    protected final void writeBootstrapPage(WrappedRequest request,
            WrappedResponse response, Application application, Integer rootId)
            throws IOException, JSONException {

        BootstrapContext context = createContext(request, response,
                application, rootId);

        DeploymentConfiguration deploymentConfiguration = request
                .getDeploymentConfiguration();

        boolean standalone = deploymentConfiguration.isStandalone(request);
        if (standalone) {
            setBootstrapPageHeaders(context);
            writeBootstrapPageHtmlHeadStart(context);
            writeBootstrapPageHtmlHeader(context);
            writeBootstrapPageHtmlBodyStart(context);
        }

        // TODO include initial UIDL in the scripts?
        writeBootstrapPageHtmlVaadinScripts(context);

        writeBootstrapPageHtmlMainDiv(context);

        Writer page = context.getWriter();
        if (standalone) {
            page.write("</body>\n</html>\n");
        }

        page.close();
    }

    public BootstrapContext createContext(WrappedRequest request,
            WrappedResponse response, Application application, Integer rootId) {
        BootstrapContext context = new BootstrapContext(response, request,
                application, rootId);
        return context;
    }

    protected String getMainDivStyle(BootstrapContext context) {
        return null;
    }

    /**
     * Creates and returns a unique ID for the DIV where the application is to
     * be rendered.
     * 
     * @param context
     * 
     * @return the id to use in the DOM
     */
    protected abstract String getApplicationId(BootstrapContext context);

    public String getWidgetsetForRoot(BootstrapContext context) {
        Root root = context.getRoot();
        WrappedRequest request = context.getRequest();

        String widgetset = root.getApplication().getWidgetsetForRoot(root);
        if (widgetset == null) {
            widgetset = request.getDeploymentConfiguration()
                    .getConfiguredWidgetset(request);
        }

        widgetset = AbstractApplicationServlet.stripSpecialChars(widgetset);
        return widgetset;
    }

    /**
     * Method to write the div element into which that actual Vaadin application
     * is rendered.
     * <p>
     * Override this method if you want to add some custom html around around
     * the div element into which the actual Vaadin application will be
     * rendered.
     * 
     * @param context
     * 
     * @throws IOException
     */
    protected void writeBootstrapPageHtmlMainDiv(BootstrapContext context)
            throws IOException {
        Writer page = context.getWriter();
        String style = getMainDivStyle(context);

        /*- Add classnames;
         *      .v-app
         *      .v-app-loading
         *      .v-app-<simpleName for app class>
         *- Additionally added from javascript:
         *      .v-theme-<themeName, remove non-alphanum> 
         */

        String appClass = "v-app-"
                + getApplicationCSSClassName(context.getApplication());

        String classNames = "v-app " + appClass;

        if (style != null && style.length() != 0) {
            style = " style=\"" + style + "\"";
        }
        page.write("<div id=\"" + context.getAppId() + "\" class=\""
                + classNames + "\"" + style + ">");
        page.write("<div class=\"v-app-loading\"></div>");
        page.write("</div>\n");
        page.write("<noscript>" + getNoScriptMessage() + "</noscript>");
    }

    /**
     * Returns a message printed for browsers without scripting support or if
     * browsers scripting support is disabled.
     */
    protected String getNoScriptMessage() {
        return "You have to enable javascript in your browser to use an application built with Vaadin.";
    }

    /**
     * Returns the application class identifier for use in the application CSS
     * class name in the root DIV. The application CSS class name is of form
     * "v-app-"+getApplicationCSSClassName().
     * 
     * This method should normally not be overridden.
     * 
     * @return The CSS class name to use in combination with "v-app-".
     */
    protected String getApplicationCSSClassName(Application application) {
        return application.getClass().getSimpleName();
    }

    /**
     * 
     * Method to open the body tag of the html kickstart page.
     * <p>
     * This method is responsible for closing the head tag and opening the body
     * tag.
     * <p>
     * Override this method if you want to add some custom html to the page.
     * 
     * @throws IOException
     */
    protected void writeBootstrapPageHtmlBodyStart(BootstrapContext context)
            throws IOException {
        Writer page = context.getWriter();
        page.write("\n</head>\n<body scroll=\"auto\" class=\""
                + ApplicationConnection.GENERATED_BODY_CLASSNAME + "\">\n");
    }

    /**
     * Method to write the script part of the page which loads needed Vaadin
     * scripts and themes.
     * <p>
     * Override this method if you want to add some custom html around scripts.
     * 
     * @param context
     * 
     * @throws IOException
     * @throws JSONException
     */
    protected void writeBootstrapPageHtmlVaadinScripts(BootstrapContext context)
            throws IOException, JSONException {
        WrappedRequest request = context.getRequest();
        Writer page = context.getWriter();

        DeploymentConfiguration deploymentConfiguration = request
                .getDeploymentConfiguration();
        String staticFileLocation = deploymentConfiguration
                .getStaticFileLocation(request);

        page.write("<iframe tabIndex=\"-1\" id=\"__gwt_historyFrame\" "
                + "style=\"position:absolute;width:0;height:0;border:0;overflow:"
                + "hidden;\" src=\"javascript:false\"></iframe>");

        String bootstrapLocation = staticFileLocation
                + "/VAADIN/vaadinBootstrap.js";
        page.write("<script type=\"text/javascript\" src=\"");
        page.write(bootstrapLocation);
        page.write("\"></script>\n");

        page.write("<script type=\"text/javascript\">\n");
        page.write("//<![CDATA[\n");
        page.write("if (!window.vaadin) alert("
                + JSONObject.quote("Failed to load the bootstrap javascript: "
                        + bootstrapLocation) + ");\n");

        writeMainScriptTagContents(context);
        page.write("//]]>\n</script>\n");
    }

    protected void writeMainScriptTagContents(BootstrapContext context)
            throws JSONException, IOException {
        JSONObject defaults = getDefaultParameters(context);
        JSONObject appConfig = getApplicationParameters(context);

        boolean isDebug = !context.getApplication().isProductionMode();
        Writer page = context.getWriter();

        page.write("vaadin.setDefaults(");
        printJsonObject(page, defaults, isDebug);
        page.write(");\n");

        page.write("vaadin.initApplication(\"");
        page.write(context.getAppId());
        page.write("\",");
        printJsonObject(page, appConfig, isDebug);
        page.write(");\n");
    }

    private static void printJsonObject(Writer page, JSONObject jsonObject,
            boolean isDebug) throws IOException, JSONException {
        if (isDebug) {
            page.write(jsonObject.toString(4));
        } else {
            page.write(jsonObject.toString());
        }
    }

    protected JSONObject getApplicationParameters(BootstrapContext context)
            throws JSONException, PaintException {
        Application application = context.getApplication();
        Integer rootId = context.getRootId();

        JSONObject appConfig = new JSONObject();

        if (rootId != null) {
            appConfig.put(ApplicationConnection.ROOT_ID_PARAMETER, rootId);
        }

        if (context.getThemeName() != null) {
            appConfig.put("themeUri",
                    getThemeUri(context, context.getThemeName()));
        }

        JSONObject versionInfo = new JSONObject();
        versionInfo.put("vaadinVersion", Version.getFullVersion());
        versionInfo.put("applicationVersion", application.getVersion());
        appConfig.put("versionInfo", versionInfo);

        appConfig.put("widgetset", context.getWidgetsetName());

        if (rootId == null || application.isRootInitPending(rootId.intValue())) {
            appConfig.put("initialPath", context.getRequest()
                    .getRequestPathInfo());

            Map<String, String[]> parameterMap = context.getRequest()
                    .getParameterMap();
            appConfig.put("initialParams", parameterMap);
        } else {
            // write the initial UIDL into the config
            appConfig.put("uidl",
                    getInitialUIDL(context.getRequest(), context.getRoot()));
        }

        return appConfig;
    }

    protected JSONObject getDefaultParameters(BootstrapContext context)
            throws JSONException {
        JSONObject defaults = new JSONObject();

        WrappedRequest request = context.getRequest();
        Application application = context.getApplication();

        // Get system messages
        Application.SystemMessages systemMessages = AbstractApplicationServlet
                .getSystemMessages(application.getClass());
        if (systemMessages != null) {
            // Write the CommunicationError -message to client
            JSONObject comErrMsg = new JSONObject();
            comErrMsg.put("caption",
                    systemMessages.getCommunicationErrorCaption());
            comErrMsg.put("message",
                    systemMessages.getCommunicationErrorMessage());
            comErrMsg.put("url", systemMessages.getCommunicationErrorURL());

            defaults.put("comErrMsg", comErrMsg);

            JSONObject authErrMsg = new JSONObject();
            authErrMsg.put("caption",
                    systemMessages.getAuthenticationErrorCaption());
            authErrMsg.put("message",
                    systemMessages.getAuthenticationErrorMessage());
            authErrMsg.put("url", systemMessages.getAuthenticationErrorURL());

            defaults.put("authErrMsg", authErrMsg);
        }

        DeploymentConfiguration deploymentConfiguration = request
                .getDeploymentConfiguration();
        String staticFileLocation = deploymentConfiguration
                .getStaticFileLocation(request);
        String widgetsetBase = staticFileLocation + "/"
                + AbstractApplicationServlet.WIDGETSET_DIRECTORY_PATH;
        defaults.put("widgetsetBase", widgetsetBase);

        if (!application.isProductionMode()) {
            defaults.put("debug", true);
        }

        if (deploymentConfiguration.isStandalone(request)) {
            defaults.put("standalone", true);
        }

        defaults.put("appUri", getAppUri(context));

        return defaults;
    }

    protected abstract String getAppUri(BootstrapContext context);

    /**
     * Method to write the contents of head element in html kickstart page.
     * <p>
     * Override this method if you want to add some custom html to the header of
     * the page.
     * 
     * @throws IOException
     */
    protected void writeBootstrapPageHtmlHeader(BootstrapContext context)
            throws IOException {
        Writer page = context.getWriter();
        String themeName = context.getThemeName();

        page.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\n");

        // Chrome frame in all versions of IE (only if Chrome frame is
        // installed)
        page.write("<meta http-equiv=\"X-UA-Compatible\" content=\"chrome=1\"/>\n");

        page.write("<style type=\"text/css\">"
                + "html, body {height:100%;margin:0;}</style>\n");

        // Add favicon links
        if (themeName != null) {
            String themeUri = getThemeUri(context, themeName);
            page.write("<link rel=\"shortcut icon\" type=\"image/vnd.microsoft.icon\" href=\""
                    + themeUri + "/favicon.ico\" />\n");
            page.write("<link rel=\"icon\" type=\"image/vnd.microsoft.icon\" href=\""
                    + themeUri + "/favicon.ico\" />\n");
        }

        Root root = context.getRoot();
        String title = ((root == null || root.getCaption() == null) ? "" : root
                .getCaption());

        page.write("<title>"
                + AbstractApplicationServlet.safeEscapeForHtml(title)
                + "</title>\n");
    }

    /**
     * Method to set http request headers for the Vaadin kickstart page.
     * <p>
     * Override this method if you need to customize http headers of the page.
     * 
     * @param context
     */
    protected void setBootstrapPageHeaders(BootstrapContext context) {
        WrappedResponse response = context.getResponse();

        // Window renders are not cacheable
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setContentType("text/html; charset=UTF-8");
    }

    /**
     * Method to write the beginning of the html page.
     * <p>
     * This method is responsible for writing appropriate doc type declarations
     * and to open html and head tags.
     * <p>
     * Override this method if you want to add some custom html to the very
     * beginning of the page.
     * 
     * @param context
     * @throws IOException
     */
    protected void writeBootstrapPageHtmlHeadStart(BootstrapContext context)
            throws IOException {
        Writer page = context.getWriter();

        // write html header
        page.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD "
                + "XHTML 1.0 Transitional//EN\" "
                + "\"http://www.w3.org/TR/xhtml1/"
                + "DTD/xhtml1-transitional.dtd\">\n");

        page.write("<html xmlns=\"http://www.w3.org/1999/xhtml\""
                + ">\n<head>\n");
        
      boolean viewportOpen = false;
          viewportOpen = prepareViewPort(viewportOpen, page);
          page.write("width=device-widdth");
          viewportOpen = prepareViewPort(viewportOpen, page);
          page.write("user-scalable=no");
          page.write("initial-scale=1");
          viewportOpen = prepareViewPort(viewportOpen, page);
          page.write("maximum-scale=1");
          viewportOpen = prepareViewPort(viewportOpen, page);
          page.write("minimum-scale=1");
          closeSingleElementTag(page);

          page.write("<meta name=\"apple-mobile-web-app-capable\" "
                  + "content=\"yes\" />\n");
          page.append("<meta name=\"apple-mobile-web-app-status-bar-style\" "
                  + "content=\"black\" />\n");
//      ApplicationIcon[] icons = w.getApplicationIcons();
//      for (int i = 0; i < icons.length; i++) {
//          ApplicationIcon icon = icons[i];
//          page.write("<link rel=\"apple-touch-icon\" ");
//          if (icon.getSizes() != null) {
//              page.write("sizes=\"");
//              page.write(icon.getSizes());
//              page.write("\"");
//          }
//          page.write(" href=\"");
//          page.write(icon.getHref());
//          closeSingleElementTag(page);
//      }
//      if (w.getStartupImage() != null) {
//          page.append("<link rel=\"apple-touch-startup-image\" "
//                  + "href=\"" + w.getStartupImage() + "\" />");
//      }

//      int offlineTimeout = 5000;
//      page.append("<script type=\"text/javascript\"> vaadin = {touchkit : { offlineTimeout: ");
//      page.append(Integer.toString(offlineTimeout));
//      page.append("}};</script>");

    }
    
    private void closeSingleElementTag(Writer page) throws IOException {
        page.write("\" />\n");
    }

    private boolean prepareViewPort(boolean viewportOpen, Writer page)
            throws IOException {
        if (viewportOpen) {
            page.write(", ");
        } else {
            page.write("\n<meta name=\"viewport\" content=\"");
        }
        return true;
    }


    /**
     * Get the URI for the application theme.
     * 
     * A portal-wide default theme is fetched from the portal shared resource
     * directory (if any), other themes from the portlet.
     * 
     * @param context
     * @param themeName
     * 
     * @return
     */
    public String getThemeUri(BootstrapContext context, String themeName) {
        WrappedRequest request = context.getRequest();
        final String staticFilePath = request.getDeploymentConfiguration()
                .getStaticFileLocation(request);
        return staticFilePath + "/"
                + AbstractApplicationServlet.THEME_DIRECTORY_PATH + themeName;
    }

    /**
     * Override if required
     * 
     * @param context
     * @return
     */
    public String getThemeName(BootstrapContext context) {
        return context.getApplication().getThemeForRoot(context.getRoot());
    }

    /**
     * Don not override.
     * 
     * @param context
     * @return
     */
    public String findAndEscapeThemeName(BootstrapContext context) {
        String themeName = getThemeName(context);
        if (themeName == null) {
            WrappedRequest request = context.getRequest();
            themeName = request.getDeploymentConfiguration()
                    .getConfiguredTheme(request);
        }

        // XSS preventation, theme names shouldn't contain special chars anyway.
        // The servlet denies them via url parameter.
        themeName = AbstractApplicationServlet.stripSpecialChars(themeName);

        return themeName;
    }

    protected void writeError(WrappedResponse response, Throwable e)
            throws IOException {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                e.getLocalizedMessage());
    }

    /**
     * Gets the initial UIDL message to send to the client.
     * 
     * @param request
     *            the originating request
     * @param root
     *            the root for which the UIDL should be generated
     * @return a string with the initial UIDL message
     * @throws PaintException
     *             if an exception occurs while painting the components
     * @throws JSONException
     *             if an exception occurs while formatting the output
     */
    protected abstract String getInitialUIDL(WrappedRequest request, Root root)
            throws PaintException, JSONException;

}
