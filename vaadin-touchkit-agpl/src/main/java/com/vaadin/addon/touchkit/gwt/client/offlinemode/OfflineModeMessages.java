package com.vaadin.addon.touchkit.gwt.client.offlinemode;

import com.google.gwt.i18n.client.Constants;

public interface OfflineModeMessages extends Constants {

    /**
     * @return A string message saying that the server cannot be reached.
     */
    String serverCannotBeReachedMsg();

    /**
     * @return The caption of the button that tries to connect to the server.
     */
    String tryAgainMsg();

    /**
     * @return A message saying that the network seems to be down.
     */
    String offlineDueToNetworkMsg();

}
