package kg.geoinfo.system.geodataservice.dto;

import kg.geoinfo.system.geodataservice.models.enums.Status;
import kg.geoinfo.system.geodataservice.references.annotation.CheckDate;

import java.util.Date;
import java.util.UUID;

public class ImageryLayerDto extends AbstractDto<UUID> {
    private UUID id;
    private String name;
    private String description;
    private String workspace;
    private String layerName;
    private String serviceUrl;
    private Status status;
    private String style;
    @CheckDate
    private Date dateCaptured;
    private String crs;

    public ImageryLayerDto() {
    }

    public void setId(java.util.UUID id) {
        this.id = id;
    }

    public java.util.UUID getId() {
        return this.id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public String getWorkspace() {
        return this.workspace;
    }

    public void setLayerName(String layerName) {
        this.layerName = layerName;
    }

    public String getLayerName() {
        return this.layerName;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getServiceUrl() {
        return this.serviceUrl;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return this.status;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getStyle() {
        return this.style;
    }

    public void setDateCaptured(java.util.Date dateCaptured) {
        this.dateCaptured = dateCaptured;
    }

    public java.util.Date getDateCaptured() {
        return this.dateCaptured;
    }

    public void setCrs(String crs) {
        this.crs = crs;
    }

    public String getCrs() {
        return this.crs;
    }
}