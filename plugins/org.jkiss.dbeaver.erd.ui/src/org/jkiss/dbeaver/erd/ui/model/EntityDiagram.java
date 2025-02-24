/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
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
/*
 * Created on Jul 13, 2004
 */
package org.jkiss.dbeaver.erd.ui.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.erd.model.*;
import org.jkiss.dbeaver.erd.ui.editor.ERDViewStyle;
import org.jkiss.dbeaver.erd.ui.internal.ERDUIActivator;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.ArrayUtils;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Schema in the model. Note that this class also includes
 * diagram specific information (layoutManualDesired and layoutManualAllowed fields)
 * although ideally these should be in a separate model hierarchy
 */
public class EntityDiagram extends ERDDiagram implements ERDContainerDecorated {
    private static final Log log = Log.getLog(EntityDiagram.class);

    private ERDModelAdapter modelAdapter;
    private final ERDDecorator decorator;
    private boolean needsAutoLayout;
    private boolean dirty;

    private final Map<ERDNote, NodeVisualInfo> noteVisuals = new IdentityHashMap<>();
    private final Map<DBSEntity, NodeVisualInfo> entityVisuals = new IdentityHashMap<>();

    private ERDAttributeVisibility attributeVisibility;
    private ERDViewStyle[] attributeStyles;
    
    public EntityDiagram(DBSObject container, String name, ERDContentProvider contentProvider, ERDDecorator decorator) {
        super(container, name, contentProvider);
        // Get model adapter (force adapter plugin activation if needed)
        this.modelAdapter = RuntimeUtils.getObjectAdapter(this, ERDModelAdapter.class, true);
        if (this.modelAdapter == null) {
            this.modelAdapter = new ERDModelAdapterDefault();
        }
        this.decorator = decorator;

        DBPPreferenceStore store = ERDUIActivator.getDefault().getPreferences();
        this.attributeVisibility = ERDAttributeVisibility.getDefaultVisibility(store);
        this.attributeStyles = ERDViewStyle.getDefaultStyles(store);
    }

    @NotNull
    public ERDModelAdapter getModelAdapter() {
        return modelAdapter;
    }

    @NotNull
    @Override
    public ERDDecorator getDecorator() {
        return decorator;
    }

    @Override
    public boolean hasAttributeStyle(@NotNull ERDViewStyle style) {
        return ArrayUtils.contains(attributeStyles, style);
    }

    public void setAttributeStyle(ERDViewStyle style, boolean enable) {
        if (enable) {
            attributeStyles = ArrayUtils.add(ERDViewStyle.class, attributeStyles, style);
        } else {
            attributeStyles = ArrayUtils.remove(ERDViewStyle.class, attributeStyles, style);
        }
        ERDViewStyle.setDefaultStyles(ERDUIActivator.getDefault().getPreferences(), attributeStyles);
    }

    public void setAttributeStyles(ERDViewStyle[] attributeStyles) {
        this.attributeStyles = attributeStyles;
    }

    @Override
    public ERDAttributeVisibility getAttributeVisibility() {
        return attributeVisibility;
    }

    public void setAttributeVisibility(ERDAttributeVisibility attributeVisibility) {
        this.attributeVisibility = attributeVisibility;
        ERDAttributeVisibility.setDefaultVisibility(ERDUIActivator.getDefault().getPreferences(), attributeVisibility);
    }

    @Override
    public boolean isEditEnabled() {
        return decorator.supportsStructureEdit() && modelAdapter.supportsModelEdit();
    }

    public EntityDiagram copy() {
        EntityDiagram copy = new EntityDiagram(getObject(), getName(), getContentProvider(), decorator);
        copy.getEntities().addAll(this.getEntities());
        copy.getEntityMap().putAll(this.getEntityMap());
        copy.noteVisuals.putAll(this.noteVisuals);
        copy.entityVisuals.putAll(this.entityVisuals);
        return copy;
    }

    public void clear() {
        super.clear();
        this.getNotes().clear();
        this.noteVisuals.clear();
        this.entityVisuals.clear();
    }

    @Nullable
    public NodeVisualInfo getVisualInfo(ERDNote erdObject) {
        return getVisualInfo(erdObject, false);
    }

    @Nullable
    public NodeVisualInfo getVisualInfo(ERDNote erdObject, boolean create) {
        NodeVisualInfo visualInfo = noteVisuals.get(erdObject);
        if (visualInfo == null && create) {
            visualInfo = new NodeVisualInfo();
            noteVisuals.put(erdObject, visualInfo);
        }
        return visualInfo;
    }

    @Nullable
    public NodeVisualInfo getVisualInfo(DBSEntity entity) {
        return getVisualInfo(entity, false);
    }

    @Nullable
    @Override
    public NodeVisualInfo getVisualInfo(DBSEntity entity, boolean create) {
        NodeVisualInfo visualInfo = entityVisuals.get(entity);
        if (visualInfo == null && create) {
            visualInfo = new NodeVisualInfo();
            entityVisuals.put(entity, visualInfo);
        }
        return visualInfo;
    }

    public void addVisualInfo(ERDNote note, NodeVisualInfo visualInfo) {
        noteVisuals.put(note, visualInfo);
    }

    public void addVisualInfo(DBSEntity entity, NodeVisualInfo visualInfo) {
        entityVisuals.put(entity, visualInfo);
    }

    public boolean isNeedsAutoLayout() {
        return needsAutoLayout;
    }

    public void setNeedsAutoLayout(boolean needsAutoLayout) {
        this.needsAutoLayout = needsAutoLayout;
    }

    public void addInitRelationBends(ERDElement<?> sourceEntity, ERDElement<?> targetEntity, String relName, List<int[]> bends) {
        for (ERDAssociation rel : sourceEntity.getReferences()) {
            if (rel.getSourceEntity() == targetEntity && relName.equals(rel.getObject().getName())) {
                rel.setInitBends(bends);
            }
        }
    }

    public List<ERDObject<?>> getContents() {
        List<ERDObject<?>> children = super.getContents();
        children.sort((o1, o2) -> {
            NodeVisualInfo vi1 = o1 instanceof ERDNote ? noteVisuals.get(o1) : entityVisuals.get(o1.getObject());
            NodeVisualInfo vi2 = o2 instanceof ERDNote ? noteVisuals.get(o2) : entityVisuals.get(o2.getObject());
            return vi1 != null && vi2 != null ? vi1.zOrder - vi2.zOrder : 0;
        });
        return children;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
