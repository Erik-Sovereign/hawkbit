/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.eclipse.hawkbit.ui.view;

import com.eclipse.hawkbit.ui.HawkbitClient;
import com.eclipse.hawkbit.ui.view.util.Filter;
import com.eclipse.hawkbit.ui.view.util.SelectionGrid;
import com.eclipse.hawkbit.ui.MainLayout;
import com.eclipse.hawkbit.ui.view.util.TableView;
import com.eclipse.hawkbit.ui.view.util.Utils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.eclipse.hawkbit.mgmt.json.model.artifact.MgmtArtifact;
import org.eclipse.hawkbit.mgmt.json.model.softwaremodule.MgmtSoftwareModule;
import org.eclipse.hawkbit.mgmt.json.model.softwaremodule.MgmtSoftwareModuleRequestBodyPost;
import org.eclipse.hawkbit.mgmt.json.model.softwaremoduletype.MgmtSoftwareModuleType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@PageTitle("Software Modules")
@Route(value = "software_modules", layout = MainLayout.class)
@RolesAllowed({"SOFTWARE_MODULE_READ"})
@Uses(Icon.class)
public class SoftwareModuleView extends TableView<MgmtSoftwareModule, Long> {

    @Autowired
    public SoftwareModuleView(final HawkbitClient hawkbitClient) {
        this(true, hawkbitClient);
    }

    public SoftwareModuleView(final boolean isParent, final HawkbitClient hawkbitClient) {
        super(
                new SoftwareModuleFilter(hawkbitClient),
                new SelectionGrid.EntityRepresentation<>(MgmtSoftwareModule.class, MgmtSoftwareModule::getModuleId) {

                    private final SoftwareModuleDetails details = new SoftwareModuleDetails(hawkbitClient);
                    @Override
                    protected void addColumns(final Grid<MgmtSoftwareModule> grid) {
                        grid.addColumn(MgmtSoftwareModule::getModuleId).setHeader("Id").setAutoWidth(true);
                        grid.addColumn(MgmtSoftwareModule::getName).setHeader("Name").setAutoWidth(true);
                        grid.addColumn(MgmtSoftwareModule::getVersion).setHeader("Version").setAutoWidth(true);
                        grid.addColumn(MgmtSoftwareModule::getTypeName).setHeader("Type").setAutoWidth(true);
                        grid.addColumn(MgmtSoftwareModule::getVendor).setHeader("Vendor").setAutoWidth(true);

                        grid.setItemDetailsRenderer(new ComponentRenderer<>(
                                () -> details, SoftwareModuleDetails::setItem));

                    }
                },
                (query, rsqlFilter) -> hawkbitClient.getSoftwareModuleRestApi()
                        .getSoftwareModules(
                                query.getOffset(), query.getPageSize(), "name:asc", rsqlFilter)
                        .getBody()
                        .getContent()
                        .stream(),
                isParent ? v -> new CreateDialog(hawkbitClient).result() : null,
                isParent ? selectionGrid -> {
                    selectionGrid.getSelectedItems().forEach(
                            module -> hawkbitClient.getSoftwareModuleRestApi().deleteSoftwareModule(module.getModuleId()));
                    selectionGrid.refreshGrid(false);
                    return CompletableFuture.completedFuture(null);
                } : null);
    }

    public Set<MgmtSoftwareModule> getSelection() {
        return selectionGrid.getSelectedItems();
    }

    private static SelectionGrid<MgmtArtifact, Long> createArtifactGrid() {
        return new SelectionGrid<>(
                new SelectionGrid.EntityRepresentation<>(MgmtArtifact.class, MgmtArtifact::getArtifactId) {
                    @Override
                    protected void addColumns(final Grid<MgmtArtifact> grid) {
                        grid.addColumn(MgmtArtifact::getArtifactId).setHeader("Id").setAutoWidth(true);
                        grid.addColumn(MgmtArtifact::getProvidedFilename).setHeader("Name").setAutoWidth(true);
                        grid.addColumn(MgmtArtifact::getSize).setHeader("Size").setAutoWidth(true);
                        grid.addColumn(MgmtArtifact::getHashes).setHeader("Hashes").setAutoWidth(true);
                    }
                });
    }

    private static class SoftwareModuleFilter implements Filter.Rsql {

        private final TextField name = Utils.textField("Name");
        private final CheckboxGroup<MgmtSoftwareModuleType> type = new CheckboxGroup<>("Type");

        private SoftwareModuleFilter(final HawkbitClient hawkbitClient) {
            name.setPlaceholder("<name filter>");
            type.setItemLabelGenerator(MgmtSoftwareModuleType::getName);
            type.setItems(
                    hawkbitClient.getSoftwareModuleTypeRestApi()
                            .getTypes(0, 20, "name:asc", null)
                            .getBody()
                            .getContent());
        }

        @Override
        public List<Component> components() {
            return List.of(name, type);
        }

        @Override
        public String filter() {
            return Filter.filter(
                    Map.of(
                            "name", name.getOptionalValue(),
                            "type", type.getSelectedItems().stream().map(MgmtSoftwareModuleType::getKey)
                                    .toList()
                    ));
        }
    }

    private static class SoftwareModuleDetails extends FormLayout {

        private final HawkbitClient hawkbitClient;

        private final TextArea description = new TextArea("Description");
        private final TextField createdBy = Utils.textField("Created by");
        private final TextField createdAt = Utils.textField("Created at");
        private final TextField lastModifiedBy = Utils.textField("Last modified by");
        private final TextField lastModifiedAt = Utils.textField("Last modified at");
        private final SelectionGrid<MgmtArtifact, Long> artifactGrid;

        private SoftwareModuleDetails(final HawkbitClient hawkbitClient) {
            this.hawkbitClient = hawkbitClient;

            description.setMinLength(2);
            artifactGrid = createArtifactGrid();
            Stream.of(
                    description,
                    createdBy, createdAt,
                    lastModifiedBy, lastModifiedAt)
                    .forEach(field -> {
                        field.setReadOnly(true);
                        add(field);
                    });
            add(artifactGrid);

            setResponsiveSteps(new ResponsiveStep("0", 2));
            setColspan(description, 2);
            setColspan(artifactGrid, 2);
        }

        private void setItem(final MgmtSoftwareModule softwareModule) {
            description.setValue(softwareModule.getDescription());
            createdBy.setValue(softwareModule.getCreatedBy());
            createdAt.setValue(new Date(softwareModule.getCreatedAt()).toString());
            lastModifiedBy.setValue(softwareModule.getLastModifiedBy());
            lastModifiedAt.setValue(new Date(softwareModule.getLastModifiedAt()).toString());

            artifactGrid.setItems(query ->
                    hawkbitClient.getSoftwareModuleRestApi()
                            .getArtifacts(
                                    softwareModule.getModuleId(), null, null)
                            .getBody().stream()
                            .skip(query.getOffset())
                            .limit(query.getPageSize()));
            artifactGrid.setSelectionMode(Grid.SelectionMode.NONE);
        }
    }

    private static class CreateDialog extends Utils.BaseDialog<Void> {

        private final Select<MgmtSoftwareModuleType> type;
        private final TextField name;
        private final TextField version;
        private final TextField vendor;
        private final TextArea description;
        private final Checkbox enableArtifactEncryption;
        private final Button create;

        private CreateDialog(final HawkbitClient hawkbitClient) {
            super("Create Software Module");

            type = new Select<>(
                    "Type",
                    this::readyToCreate,
                    hawkbitClient.getSoftwareModuleTypeRestApi()
                            .getTypes(0, 30, "name:asc", null)
                            .getBody()
                            .getContent()
                            .toArray(new MgmtSoftwareModuleType[0]));
            type.setWidthFull();
            type.setRequiredIndicatorVisible(true);
            type.setItemLabelGenerator(MgmtSoftwareModuleType::getName);
            type.focus();
            name = Utils.textField("Name", this::readyToCreate);
            version = Utils.textField("Version", this::readyToCreate);
            vendor = Utils.textField("Vendor");
            description = new TextArea("Description");
            description.setWidthFull();
            description.setMinLength(2);
            enableArtifactEncryption = new Checkbox("Enable artifact encryption");

            create = Utils.tooltip(new Button("Create"), "Create (Enter)");
            create.setEnabled(false);
            addCreateClickListener(hawkbitClient);
            create.addClickShortcut(Key.ENTER);
            final Button cancel = Utils.tooltip(new Button("Cancel"), "Cancel (Esc)");
            cancel.addClickListener(e -> close());
            cancel.addClickShortcut(Key.ESCAPE);
            final HorizontalLayout actions = new HorizontalLayout(create, cancel);
            actions.setSizeFull();
            actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

            final VerticalLayout layout = new VerticalLayout();
            layout.setSizeFull();
            layout.setSpacing(false);
            layout.add(type, name, version, vendor, description, enableArtifactEncryption, actions);
            add(layout);
            open();
        }

        private void readyToCreate(final Object v) {
            final boolean createEnabled = !type.isEmpty() && !name.isEmpty() && !version.isEmpty();
            if (create.isEnabled() != createEnabled) {
                create.setEnabled(createEnabled);
            }
        }

        private void addCreateClickListener(final HawkbitClient hawkbitClient) {
            create.addClickListener(e -> {
                close();
                final long softwareModuleId = hawkbitClient.getSoftwareModuleRestApi().createSoftwareModules(
                        List.of(new MgmtSoftwareModuleRequestBodyPost()
                                        .setType(type.getValue().getKey())
                                        .setName(name.getValue())
                                        .setVersion(version.getValue())
                                        .setVendor(vendor.getValue())
                                        .setDescription(description.getValue())
                                        .setEncrypted(enableArtifactEncryption.getValue())))
                        .getBody()
                        .stream()
                        .findFirst()
                        .orElseThrow()
                        .getModuleId();
                new AddArtifactsDialog(softwareModuleId, hawkbitClient).open();
            });
        }
    }

    private static class AddArtifactsDialog extends Utils.BaseDialog<Void> {

        private final Set<MgmtArtifact> artifacts = Collections.synchronizedSet(new HashSet<>());

        private AddArtifactsDialog(
                final long softwareModuleId,
                final HawkbitClient hawkbitClient) {
            super("Add Artifacts");

            final SelectionGrid<MgmtArtifact, Long> artifactGrid = createArtifactGrid();
            artifactGrid.setItems(query -> {
                query.getOffset(); // to keep vaadin contract
                return artifacts.stream().limit(query.getLimit());
            });
            artifactGrid.setSelectionMode(Grid.SelectionMode.NONE);

            final FileBuffer fileBuffer = new FileBuffer();
            final Upload uploadBtn = new Upload(fileBuffer);
            uploadBtn.setMaxFiles(10);
            uploadBtn.setWidthFull();
            uploadBtn.setDropAllowed(true);
            uploadBtn.addSucceededListener(e -> {
                final MgmtArtifact artifact = hawkbitClient.getSoftwareModuleRestApi()
                        .uploadArtifact(softwareModuleId,
                                new MultipartFileImpl(fileBuffer, e.getContentLength(), e.getMIMEType()), fileBuffer.getFileName(), null, null,
                                null).getBody();
                artifacts.add(artifact);
                artifactGrid.refreshGrid(false);
            });

            final Button finishBtn = Utils.tooltip(new Button("Finish"), "Finish (Enter)");
            finishBtn.addClickListener(e ->  close());
            finishBtn.addClickShortcut(Key.ENTER);
            finishBtn.setHeightFull();
            final HorizontalLayout finish = new HorizontalLayout(finishBtn);
            finish.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            finish.setWidthFull();

            final VerticalLayout layout = new VerticalLayout(artifactGrid, uploadBtn, finish);
            layout.setSizeFull();
            layout.setSpacing(false);
            add(layout);
        }

        private static class MultipartFileImpl implements MultipartFile {

            private final FileBuffer fileBuffer;
            private final String mimeType;
            private final long contentLength;

            public MultipartFileImpl(final FileBuffer fileBuffer, final long contentLength, final String mimeType) {
                this.fileBuffer = fileBuffer;
                this. contentLength = contentLength;
                this.mimeType = mimeType;
            }

            @Override
            public String getName() {
                return fileBuffer.getFileName();
            }

            @Override
            public String getOriginalFilename() {
                return getName();
            }

            @Override
            public String getContentType() {
                return mimeType;
            }

            @Override
            public boolean isEmpty() {
                return contentLength == 0;
            }

            @Override
            public long getSize() {
                return contentLength;
            }

            @Override
            public byte[] getBytes() throws IOException {
               return getInputStream().readAllBytes();
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return fileBuffer.getInputStream();
            }

            @Override
            public void transferTo(final File dest) throws IllegalStateException {
                throw new UnsupportedOperationException();
            }
        }
    }
}
