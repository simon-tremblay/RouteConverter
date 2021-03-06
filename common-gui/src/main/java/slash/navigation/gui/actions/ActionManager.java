/*
    This file is part of RouteConverter.

    RouteConverter is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    RouteConverter is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with RouteConverter; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Copyright (C) 2007 Christian Pesch. All Rights Reserved.
*/

package slash.navigation.gui.actions;

import slash.navigation.gui.Application;

import javax.swing.*;
import javax.swing.event.SwingPropertyChangeSupport;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import static javax.swing.Action.NAME;
import static slash.navigation.gui.helpers.PreferencesHelper.count;

/**
 * Manages the {@link Action}s of an {@link Application}.
 *
 * @author Christian Pesch
 */

public class ActionManager {
    private static final Logger log = Logger.getLogger(ActionManager.class.getName());
    private static final Preferences preferences = Preferences.userNodeForPackage(ActionManager.class);
    private static final String RUN_COUNT_PREFERENCE = "runCount";

    private Map<String, Action> actionMap = new HashMap<>();
    private Map<String, ProxyAction> proxyActionMap = new HashMap<>();

    public Action get(String actionName) {
        Action action = actionMap.get(actionName);
        if (action != null)
            return action;
        ProxyAction proxyAction = proxyActionMap.get(actionName);
        if (proxyAction == null) {
            proxyAction = new ProxyAction();
            proxyActionMap.put(actionName, proxyAction);
        }
        return proxyAction;
    }

    public void register(String actionName, Action action) {
        Action found = actionMap.get(actionName);
        if (found != null)
            throw new IllegalArgumentException("action '" + found + "' for '" + actionName + "' already registered");
        actionMap.put(actionName, action);
        action.putValue(NAME, actionName);
        ProxyAction proxyAction = proxyActionMap.get(actionName);
        if (proxyAction != null) {
            proxyAction.setDelegate(action);
        } else {
            proxyActionMap.put(actionName, new ProxyAction(action));
        }
    }

    public void run(String actionName) {
        run(actionName, new ActionEvent(this, -1, actionName));
    }

    public void run(String actionName, ActionEvent actionEvent) {
        Action action = actionMap.get(actionName);
        if (action == null)
            throw new IllegalArgumentException("no action registered for '" + actionName + "'");
        perform(action, actionEvent);
    }

    public void enable(String actionName, boolean enable) {
        Action action = actionMap.get(actionName);
        if (action == null)
            throw new IllegalArgumentException("no action registered for '" + actionName + "'");
        action.setEnabled(enable);
    }

    private static void perform(Action action, ActionEvent event) {
        count(preferences, RUN_COUNT_PREFERENCE + action.getValue(NAME));
        action.actionPerformed(event);
    }

    public void logUsage() {
        StringBuilder builder = new StringBuilder();
        for (String actionName : actionMap.keySet()) {
            int runs = preferences.getInt(RUN_COUNT_PREFERENCE + actionName, 0);
            if (runs > 0)
                builder.append(String.format("\n%s, runs: %d", actionName, runs));
        }
        log.info("Action usage:" + builder.toString());
    }

    private static class ProxyAction implements Action, PropertyChangeListener {
        private Action delegate = null;
        private SwingPropertyChangeSupport changeSupport = new SwingPropertyChangeSupport(this);

        private ProxyAction() {
        }

        private ProxyAction(Action delegate) {
            this.delegate = delegate;
        }

        public void setDelegate(Action delegate) {
            if (this.delegate != null)
                delegate.removePropertyChangeListener(this);

            this.delegate = delegate;

            delegate.addPropertyChangeListener(this);
        }

        public void propertyChange(PropertyChangeEvent evt) {
            changeSupport.firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }

        public Object getValue(String key) {
            return delegate != null ? delegate.getValue(key) : null;
        }

        public void putValue(String key, Object value) {
            if (delegate != null)
                delegate.putValue(key, value);
        }

        public boolean isEnabled() {
            return delegate == null || delegate.isEnabled();
        }

        public void setEnabled(boolean enabled) {
            if (delegate != null)
                delegate.setEnabled(enabled);
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            changeSupport.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            changeSupport.removePropertyChangeListener(listener);
        }

        public void actionPerformed(ActionEvent e) {
            if(delegate != null)
                perform(delegate, e);
        }
    }
}
