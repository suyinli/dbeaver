/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 * Copyright (C) 2011-2012 Eugene Fradkin (eugene.fradkin@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.tasks.ui.nativetool;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbenchWindow;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.connection.DBPNativeClientLocation;
import org.jkiss.dbeaver.model.connection.LocalNativeClientLocation;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.task.DBTTask;
import org.jkiss.dbeaver.registry.task.TaskPreferenceStore;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.tasks.nativetool.AbstractNativeToolSettings;
import org.jkiss.dbeaver.tasks.ui.nativetool.internal.TaskNativeUIMessages;
import org.jkiss.dbeaver.tasks.ui.wizard.TaskConfigurationWizard;
import org.jkiss.dbeaver.tasks.ui.wizard.TaskConfigurationWizardDialog;
import org.jkiss.dbeaver.tasks.ui.wizard.TaskWizardExecutor;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.utils.HelpUtils;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

/**
 * Abstract wizard
 */
public abstract class AbstractNativeToolWizard<SETTINGS extends AbstractNativeToolSettings<BASE_OBJECT>, BASE_OBJECT extends DBSObject, PROCESS_ARG>
    extends TaskConfigurationWizard<SETTINGS> {

    private static final Log log = Log.getLog(AbstractNativeToolWizard.class);

    private final DBPPreferenceStore preferenceStore;
    private final SETTINGS settings;

    protected String taskTitle;
    protected final NativeToolWizardPageLog logPage;
    private boolean finished;
    private String errorMessage;

    protected AbstractNativeToolWizard(@NotNull Collection<BASE_OBJECT> databaseObjects, @NotNull String taskTitle) {
        this.preferenceStore = DBWorkbench.getPlatform().getPreferenceStore();
        this.settings = createSettings();
        this.settings.getDatabaseObjects().addAll(databaseObjects);
        this.taskTitle = taskTitle;
        this.logPage = new NativeToolWizardPageLog(taskTitle);
    }

    public AbstractNativeToolWizard(@NotNull DBTTask task) {
        super(task);
        if (isToolTask()) {
            // Use global store for tool tasks
            this.preferenceStore = DBWorkbench.getPlatform().getPreferenceStore();
        } else {
            this.preferenceStore = new TaskPreferenceStore(task);
        }
        this.settings = createSettings();
        this.taskTitle = task.getType().getName();
        this.logPage = new NativeToolWizardPageLog(taskTitle);
    }

    protected abstract SETTINGS createSettings();

    @Override
    public SETTINGS getSettings() {
        return settings;
    }

    @NotNull
    protected DBPPreferenceStore getPreferenceStore() {
        return preferenceStore;
    }

    public DBPProject getProject() {
        if (settings.getDataSourceContainer() != null) {
            return settings.getDataSourceContainer().getProject();
        }
        return super.getProject();
    }

    @Override
    protected String getDefaultWindowTitle() {
        return taskTitle;
    }

    @Override
    public boolean canFinish() {
        if (!super.canFinish()) {
            return false;
        }
        if (isSingleTimeWizard()) {
            return !finished;
        }
        // [#2917] Finish button is always enabled (!finished && super.canFinish())
        return true;
    }

    /**
     * @return true if this wizard can be executed only once
     */
    protected boolean isSingleTimeWizard() {
        return false;
    }

    @Override
    public void initializeWizard(Composite pageContainer) {
        try {
            settings.loadSettings(UIUtils.getDefaultRunnableContext(), getPreferenceStore());
        } catch (DBException e) {
            DBWorkbench.getPlatformUI().showError("Settings load", "Error loading wizard settings", e);
        }

        super.initializeWizard(pageContainer);

        readLocalClientInfo();
    }

    void readLocalClientInfo() {
        WizardPage currentPage = (WizardPage) getStartingPage();

        if (isNativeClientHomeRequired()) {
            String clientHomeId = getSettings().getDataSourceContainer().getConnectionConfiguration().getClientHomeId();
            List<DBPNativeClientLocation> nativeClientLocations = getSettings().getDataSourceContainer().getDriver().getNativeClientLocations();
            if (CommonUtils.isEmpty(clientHomeId)) {
                if (nativeClientLocations != null && !nativeClientLocations.isEmpty()) {
                    settings.setClientHome(nativeClientLocations.get(0));
                } else {
                    settings.setClientHome(null);
                }
                if (settings.getClientHome() == null) {
                    currentPage.setErrorMessage(TaskNativeUIMessages.tools_wizard_message_no_client_home + "\n " +
                                    NLS.bind(
                                            TaskNativeUIMessages.tools_wizard_message_no_client_home_link,
                                            HelpUtils.getHelpExternalReference("Local-Client-Configuration")));
                    getContainer().updateMessage();
                    return;
                }
            } else {
                DBPNativeClientLocation clientHome = DBUtils.findObject(nativeClientLocations, clientHomeId);
                if (clientHome == null) {
                    clientHome = getSettings().findNativeClientHome(clientHomeId);
                }
                if (clientHome == null) {
                    // Make local client home from location
                    clientHome = new LocalNativeClientLocation(clientHomeId, clientHomeId);
                }
                settings.setClientHome(clientHome);
            }
            if (settings.getClientHome() == null) {
                currentPage.setErrorMessage(NLS.bind(TaskNativeUIMessages.tools_wizard_message_client_home_not_found, clientHomeId));
            } else {
                currentPage.setErrorMessage(null);
            }
            getContainer().updateMessage();
            getContainer().updateButtons();
        }
    }

    private boolean validateClientFiles() {
        if (!isNativeClientHomeRequired() || settings.getClientHome() == null) {
            return true;
        }
        try {
            UIUtils.run(getContainer(), true, true, monitor -> {
                try {
                    settings.getClientHome().validateFilesPresence(monitor);
                } catch (DBException e) {
                    throw new InvocationTargetException(e);
                }
            });
        } catch (InvocationTargetException e) {
            DBWorkbench.getPlatformUI().showError("Download local client file(s)", "Error downloading client file(s)", e.getTargetException());
            ((WizardPage) getContainer().getCurrentPage()).setErrorMessage("Error downloading local client file(s)");
            getContainer().updateMessage();
            return false;
        } catch (InterruptedException e) {
            // ignore
            return false;
        }
        return true;
    }

    @Override
    public boolean performFinish() {
        // Save settings
        settings.saveSettings(getRunnableContext(), getPreferenceStore());

        if (!validateClientFiles()) {
            return false;
        }

        if (getCurrentTask() != null && !isToolTask()) {
            return super.performFinish();
        }

        TaskConfigurationWizardDialog container = getContainer();
        container.disableButtonsOnProgress();

        showLogPage();

        try {
            // Execute directly - without task serialize/deserialize
            // We need it because some data producers cannot be serialized properly (e.g. ResultSetDatacontainer - see #7342)
            DBTTask temporaryTask = getProject().getTaskManager().createTemporaryTask(getTaskType(), getTaskType().getName());
            saveConfigurationToTask(temporaryTask);
            TaskWizardExecutor executor = new TaskWizardExecutor(getRunnableContext(), temporaryTask, log, logPage.getLogWriter());
            executor.executeTask();
            container.enableButtonsAfterProgress();
            container.setCompleteMarkAfterProgress();
            return false;
        } catch (Exception e) {
            DBWorkbench.getPlatformUI().showError(e.getMessage(), "Error running task", e);
            return false;
        }
    }

    protected void showLogPage() {
        if (getContainer().getCurrentPage() != logPage) {
            getContainer().showPage(logPage);
        }
    }

    public String getObjectsName() {
        StringBuilder str = new StringBuilder();
//        for (BASE_OBJECT object : settings.getDatabaseObjects()) {
//            if (str.length() > 0) str.append(",");
//            str.append(object.getName());
//        }
        return str.toString();
    }

    protected boolean isNativeClientHomeRequired() {
        return true;
    }

    public boolean isVerbose() {
        return false;
    }

    protected void onSuccess(long workTime) {

    }

    protected void onError() {
        UIUtils.showMessageBox(
            getShell(),
            taskTitle,
            errorMessage == null ? "Internal error" : errorMessage,
            SWT.ICON_ERROR);
    }

    @NotNull
    @Override
    public TaskConfigurationWizardDialog createWizardDialog(@NotNull IWorkbenchWindow window, @Nullable IStructuredSelection selection) {
        return new NativeToolWizardDialog(window, this, selection);
    }
}
