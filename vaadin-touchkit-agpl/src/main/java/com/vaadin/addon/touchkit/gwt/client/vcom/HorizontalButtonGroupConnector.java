package com.vaadin.addon.touchkit.gwt.client.vcom;

import java.util.List;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.addon.touchkit.gwt.client.ui.HorizontalButtonGroupWidget;
import com.vaadin.addon.touchkit.ui.HorizontalButtonGroup;
import com.vaadin.client.ComponentConnector;
import com.vaadin.client.ConnectorHierarchyChangeEvent;
import com.vaadin.client.ui.AbstractComponentContainerConnector;
import com.vaadin.shared.communication.URLReference;
import com.vaadin.shared.ui.Connect;

@Connect(HorizontalButtonGroup.class)
public class HorizontalButtonGroupConnector extends
        AbstractComponentContainerConnector {

    private HorizontalButtonGroupWidget theWidget;


    @Override
    public void onConnectorHierarchyChange(ConnectorHierarchyChangeEvent event) {
        if (getParent() == null) {
            // Component is removed, skip stuff to save user from JS exceptions
            // and some milliseconds of lost life
            return;
        }

        List<ComponentConnector> children = getChildComponents();
        HorizontalButtonGroupWidget widget = (HorizontalButtonGroupWidget) getWidget();
        widget.clear();
        for (ComponentConnector connector : children) {
            // FIXME
            // URLReference urlRef = connector.getState().getIcon();
            URLReference urlRef = null;
            String caption = connector.getState().caption;
            String url = "";
            if (urlRef != null) {
                url = urlRef.getURL();
            }

            ((HorizontalButtonGroupWidget) getWidget()).add(connector
                    .getWidget());
        }
    }

    @Override
    protected Widget createWidget() {
        theWidget = GWT.create(HorizontalButtonGroupWidget.class);
        return theWidget;
    }

    @Override
    public void updateCaption(ComponentConnector connector) {
    }

}
